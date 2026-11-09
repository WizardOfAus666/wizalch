package com.alchmatepro;

import com.alchmatepro.PriceTrendSummary.MarginDirection;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Retrieves historical time-series pricing for individual OSRS items from
 * https://prices.runescape.wiki/api/v1/osrs/timeseries and computes trend direction,
 * volatility, and whether the High Level Alchemy margin is improving or declining.
 */
@Slf4j
@Singleton
public class PriceHistoryService
{
	public static final String DEFAULT_TIMESERIES_URL = "https://prices.runescape.wiki/api/v1/osrs/timeseries";

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final Map<Integer, PriceTrendSummary> historyCache = new ConcurrentHashMap<>();

	@Getter
	@Setter
	private volatile String timeseriesBaseUrl = DEFAULT_TIMESERIES_URL;

	@Inject
	public PriceHistoryService(OkHttpClient okHttpClient, Gson gson)
	{
		this.httpClient = okHttpClient;
		this.gson = gson;
	}

	/**
	 * Fetches and analyses the historical price series for {@code itemId} using a 1h timestep.
	 */
	public PriceTrendSummary fetchTrendSummary(int itemId) throws IOException
	{
		return fetchTrendSummary(itemId, "1h");
	}

	public PriceTrendSummary fetchTrendSummary(int itemId, String timestep) throws IOException
	{
		if (itemId <= 0)
		{
			return PriceTrendSummary.builder()
				.itemId(itemId)
				.marginDirection(MarginDirection.INSUFFICIENT_DATA)
				.build();
		}

		String safeTimestep = (timestep == null || timestep.isEmpty()) ? "1h" : timestep;
		String url = timeseriesBaseUrl + "?timestep=" + safeTimestep + "&id=" + itemId;

		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", WikiPriceDataProvider.USER_AGENT)
			.header("Accept", "application/json")
			.get()
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful() || response.body() == null)
			{
				throw new IOException("HTTP " + response.code() + " fetching timeseries for item " + itemId);
			}
			ResponseBody body = response.body();
			List<PricePoint> points = parseTimeseriesJson(body.string());
			PriceTrendSummary summary = buildTrendSummary(itemId, points);
			historyCache.put(itemId, summary);
			return summary;
		}
		catch (IOException ex)
		{
			PriceTrendSummary cached = historyCache.get(itemId);
			if (cached != null)
			{
				return cached;
			}
			throw ex;
		}
	}

	/**
	 * Parses the JSON response from /timeseries into a chronologically sorted list of {@link PricePoint}.
	 */
	public List<PricePoint> parseTimeseriesJson(String json) throws IOException
	{
		if (json == null || json.trim().isEmpty())
		{
			return Collections.emptyList();
		}

		try
		{
			JsonElement root = gson.fromJson(json, JsonElement.class);
			if (root == null || !root.isJsonObject())
			{
				throw new IOException("Malformed /timeseries JSON root.");
			}
			JsonObject obj = root.getAsJsonObject();
			if (!obj.has("data") || !obj.get("data").isJsonArray())
			{
				return Collections.emptyList();
			}

			JsonArray arr = obj.getAsJsonArray("data");
			List<PricePoint> points = new ArrayList<>(arr.size());
			for (JsonElement el : arr)
			{
				if (!el.isJsonObject())
				{
					continue;
				}
				JsonObject pt = el.getAsJsonObject();
				long ts = pt.has("timestamp") && !pt.get("timestamp").isJsonNull()
					? pt.get("timestamp").getAsLong()
					: 0L;
				Integer avgHigh = pt.has("avgHighPrice") && !pt.get("avgHighPrice").isJsonNull()
					? pt.get("avgHighPrice").getAsInt()
					: null;
				int highVol = pt.has("highPriceVolume") && !pt.get("highPriceVolume").isJsonNull()
					? Math.max(0, pt.get("highPriceVolume").getAsInt())
					: 0;
				Integer avgLow = pt.has("avgLowPrice") && !pt.get("avgLowPrice").isJsonNull()
					? pt.get("avgLowPrice").getAsInt()
					: null;
				int lowVol = pt.has("lowPriceVolume") && !pt.get("lowPriceVolume").isJsonNull()
					? Math.max(0, pt.get("lowPriceVolume").getAsInt())
					: 0;

				if (ts > 0 && ((avgHigh != null && avgHigh > 0) || (avgLow != null && avgLow > 0)))
				{
					points.add(PricePoint.builder()
						.timestampEpochSec(ts)
						.avgHighPrice(avgHigh)
						.highPriceVolume(highVol)
						.avgLowPrice(avgLow)
						.lowPriceVolume(lowVol)
						.build());
				}
			}

			points.sort(Comparator.comparingLong(PricePoint::getTimestampEpochSec));
			// Retain most recent 48 points for crisp chart rendering and recent trend analysis
			if (points.size() > 48)
			{
				points = new ArrayList<>(points.subList(points.size() - 48, points.size()));
			}
			return points;
		}
		catch (JsonSyntaxException ex)
		{
			throw new IOException("Invalid JSON from /timeseries: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Computes trend metrics and whether the high-alchemy margin is improving or declining.
	 * Note: Because High Level Alchemy returns a fixed coin value, a falling buy price means
	 * the alchemy profit margin is IMPROVING, whereas a rising buy price means the margin is DECLINING.
	 */
	public PriceTrendSummary buildTrendSummary(int itemId, List<PricePoint> points)
	{
		if (points == null || points.isEmpty())
		{
			return PriceTrendSummary.builder()
				.itemId(itemId)
				.points(Collections.emptyList())
				.marginDirection(MarginDirection.INSUFFICIENT_DATA)
				.build();
		}

		int minPrice = Integer.MAX_VALUE;
		int maxPrice = 0;
		long sumPrice = 0L;
		int validCount = 0;
		int firstPrice = 0;
		int latestPrice = 0;

		for (PricePoint pt : points)
		{
			Integer buyPrice = pt.getEffectiveBuyPrice();
			if (buyPrice != null && buyPrice > 0)
			{
				if (firstPrice == 0)
				{
					firstPrice = buyPrice;
				}
				latestPrice = buyPrice;
				minPrice = Math.min(minPrice, buyPrice);
				maxPrice = Math.max(maxPrice, buyPrice);
				sumPrice += buyPrice;
				validCount++;
			}
		}

		if (validCount == 0)
		{
			return PriceTrendSummary.builder()
				.itemId(itemId)
				.points(Collections.emptyList())
				.marginDirection(MarginDirection.INSUFFICIENT_DATA)
				.build();
		}

		int avgPrice = (int) Math.round((double) sumPrice / validCount);
		long changeGp = (long) latestPrice - (long) firstPrice;
		double changePct = firstPrice > 0 ? ((double) changeGp / (double) firstPrice) * 100.0 : 0.0;
		double volatilityPct = avgPrice > 0 ? (((double) (maxPrice - minPrice) / (double) avgPrice) * 100.0) : 0.0;

		MarginDirection direction;
		if (validCount < 2)
		{
			direction = MarginDirection.INSUFFICIENT_DATA;
		}
		else if (changePct <= -1.0)
		{
			// Buy price decreased -> High Alch margin increased (Improving)
			direction = MarginDirection.IMPROVING;
		}
		else if (changePct >= 1.0)
		{
			// Buy price increased -> High Alch margin decreased (Declining)
			direction = MarginDirection.DECLINING;
		}
		else
		{
			direction = MarginDirection.STABLE;
		}

		return PriceTrendSummary.builder()
			.itemId(itemId)
			.points(Collections.unmodifiableList(points))
			.minBuyPrice(minPrice)
			.maxBuyPrice(maxPrice)
			.averageBuyPrice(avgPrice)
			.firstBuyPrice(firstPrice)
			.latestBuyPrice(latestPrice)
			.priceChangeGp(changeGp)
			.priceChangePercent(changePct)
			.volatilityPercent(volatilityPct)
			.marginDirection(direction)
			.build();
	}

	public PriceTrendSummary getCachedSummary(int itemId)
	{
		return historyCache.get(itemId);
	}
}
