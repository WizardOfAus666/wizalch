package com.alchmatepro;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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
 * Official OSRS Wiki Real-Time Prices API provider (https://prices.runescape.wiki/api/v1/osrs).
 * Retrieves bulk pricing and volume telemetry from /latest, /5m, and /1h endpoints
 * with timeouts, retry backoff, rate limiting, and strict JSON validation.
 */
@Slf4j
@Singleton
public class WikiPriceDataProvider implements MarketDataProvider
{
	public static final String DEFAULT_BASE_URL = "https://prices.runescape.wiki/api/v1/osrs";
	public static final String USER_AGENT = "WIZALCH-RuneLite-Plugin/1.0 (OSRS High Alch Scanner; +https://github.com/runelite/plugin-hub)";

	private static final int MAX_RETRIES = 2;
	private static final long INITIAL_BACKOFF_MS = 400L;
	private static final long MIN_REQUEST_INTERVAL_MS = 5_000L;

	private final OkHttpClient httpClient;
	private final Gson gson;

	@Getter
	@Setter
	private volatile String baseUrl = DEFAULT_BASE_URL;

	private volatile boolean available = true;
	private volatile String statusSummary = "Ready (OSRS Wiki Real-Time Prices API v1)";
	private volatile long lastRequestTimeMs = 0L;

	@Inject
	public WikiPriceDataProvider(OkHttpClient okHttpClient, Gson gson)
	{
		this.httpClient = okHttpClient.newBuilder()
			.connectTimeout(10, TimeUnit.SECONDS)
			.readTimeout(15, TimeUnit.SECONDS)
			.build();
		this.gson = gson;
	}

	@Override
	public String getProviderName()
	{
		return "OSRS Wiki Real-Time Prices API";
	}

	@Override
	public boolean isAvailable()
	{
		return available;
	}

	@Override
	public String getStatusSummary()
	{
		return statusSummary;
	}

	@Override
	public synchronized Map<Integer, MarketPrice> fetchMarketPrices() throws IOException
	{
		long nowMs = System.currentTimeMillis();
		long elapsedMs = nowMs - lastRequestTimeMs;
		if (lastRequestTimeMs > 0 && elapsedMs < MIN_REQUEST_INTERVAL_MS)
		{
			try
			{
				Thread.sleep(MIN_REQUEST_INTERVAL_MS - elapsedMs);
			}
			catch (InterruptedException ie)
			{
				Thread.currentThread().interrupt();
				throw new IOException("Market price fetch interrupted during rate-limit wait.", ie);
			}
		}
		lastRequestTimeMs = System.currentTimeMillis();

		long fetchedAtEpochSec = Instant.now().getEpochSecond();

		try
		{
			String latestJson = executeWithRetry(baseUrl + "/latest");
			Map<Integer, MarketPrice> priceMap = parseLatestJson(latestJson, fetchedAtEpochSec);

			if (priceMap.isEmpty())
			{
				available = false;
				statusSummary = "OSRS Wiki /latest returned 0 valid prices.";
				throw new IOException(statusSummary);
			}

			// Enrich with /5m rolling averages and volume (non-fatal if /5m fails)
			try
			{
				String fiveMinJson = executeWithRetry(baseUrl + "/5m");
				merge5mJson(priceMap, fiveMinJson, fetchedAtEpochSec);
			}
			catch (Exception ex)
			{
				log.debug("Non-fatal: failed to merge /5m prices: {}", ex.getMessage());
			}

			// Enrich with /1h stable averages and volume (non-fatal if /1h fails)
			try
			{
				String oneHourJson = executeWithRetry(baseUrl + "/1h");
				merge1hJson(priceMap, oneHourJson, fetchedAtEpochSec);
			}
			catch (Exception ex)
			{
				log.debug("Non-fatal: failed to merge /1h prices: {}", ex.getMessage());
			}

			available = true;
			statusSummary = "Connected (" + priceMap.size() + " live items via /latest, /5m, /1h)";
			return priceMap;
		}
		catch (IOException ex)
		{
			available = false;
			statusSummary = "Wiki API error: " + ex.getMessage();
			throw ex;
		}
	}

	/**
	 * Parses the /latest JSON response into a map of itemId -> {@link MarketPrice}.
	 */
	public Map<Integer, MarketPrice> parseLatestJson(String json, long fetchedAtEpochSec) throws IOException
	{
		if (json == null || json.trim().isEmpty())
		{
			throw new IOException("Empty JSON payload from /latest endpoint.");
		}

		try
		{
			JsonElement rootElement = gson.fromJson(json, JsonElement.class);
			if (rootElement == null || !rootElement.isJsonObject())
			{
				throw new IOException("Malformed /latest JSON root object.");
			}

			JsonObject root = rootElement.getAsJsonObject();
			if (!root.has("data") || !root.get("data").isJsonObject())
			{
				throw new IOException("Missing 'data' object in /latest JSON response.");
			}

			JsonObject data = root.getAsJsonObject("data");
			Map<Integer, MarketPrice> result = new HashMap<>(data.size());

			for (Map.Entry<String, JsonElement> entry : data.entrySet())
			{
				int itemId;
				try
				{
					itemId = Integer.parseInt(entry.getKey());
				}
				catch (NumberFormatException ignored)
				{
					continue;
				}
				if (itemId <= 0 || !entry.getValue().isJsonObject())
				{
					continue;
				}

				JsonObject itemObj = entry.getValue().getAsJsonObject();
				Integer high = getPositiveInt(itemObj, "high");
				Long highTime = getPositiveLong(itemObj, "highTime");
				Integer low = getPositiveInt(itemObj, "low");
				Long lowTime = getPositiveLong(itemObj, "lowTime");

				if (high == null && low == null)
				{
					continue;
				}

				MarketPrice mp = MarketPrice.builder()
					.itemId(itemId)
					.instantBuyPrice(high)
					.instantBuyTimeEpochSec(highTime)
					.instantSellPrice(low)
					.instantSellTimeEpochSec(lowTime)
					.fetchedAtEpochSec(fetchedAtEpochSec)
					.build();

				result.put(itemId, mp);
			}

			return result;
		}
		catch (JsonSyntaxException ex)
		{
			throw new IOException("Invalid JSON syntax from /latest endpoint: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Merges /5m rolling average pricing and volumes into the existing price map.
	 */
	public void merge5mJson(Map<Integer, MarketPrice> priceMap, String json, long fetchedAtEpochSec) throws IOException
	{
		JsonObject data = extractDataObject(json, "/5m");
		for (Map.Entry<String, JsonElement> entry : data.entrySet())
		{
			int itemId;
			try
			{
				itemId = Integer.parseInt(entry.getKey());
			}
			catch (NumberFormatException ignored)
			{
				continue;
			}
			if (itemId <= 0 || !entry.getValue().isJsonObject())
			{
				continue;
			}

			JsonObject obj = entry.getValue().getAsJsonObject();
			Integer avgHigh = getPositiveInt(obj, "avgHighPrice");
			int highVol = getNonNegativeInt(obj, "highPriceVolume");
			Integer avgLow = getPositiveInt(obj, "avgLowPrice");
			int lowVol = getNonNegativeInt(obj, "lowPriceVolume");

			MarketPrice existing = priceMap.computeIfAbsent(itemId, id -> MarketPrice.builder()
				.itemId(id)
				.fetchedAtEpochSec(fetchedAtEpochSec)
				.build());

			existing.setAvgHighPrice5m(avgHigh);
			existing.setHighVolume5m(highVol);
			existing.setAvgLowPrice5m(avgLow);
			existing.setLowVolume5m(lowVol);
		}
	}

	/**
	 * Merges /1h stable rolling average pricing and volumes into the existing price map.
	 */
	public void merge1hJson(Map<Integer, MarketPrice> priceMap, String json, long fetchedAtEpochSec) throws IOException
	{
		JsonObject data = extractDataObject(json, "/1h");
		for (Map.Entry<String, JsonElement> entry : data.entrySet())
		{
			int itemId;
			try
			{
				itemId = Integer.parseInt(entry.getKey());
			}
			catch (NumberFormatException ignored)
			{
				continue;
			}
			if (itemId <= 0 || !entry.getValue().isJsonObject())
			{
				continue;
			}

			JsonObject obj = entry.getValue().getAsJsonObject();
			Integer avgHigh = getPositiveInt(obj, "avgHighPrice");
			int highVol = getNonNegativeInt(obj, "highPriceVolume");
			Integer avgLow = getPositiveInt(obj, "avgLowPrice");
			int lowVol = getNonNegativeInt(obj, "lowPriceVolume");

			MarketPrice existing = priceMap.computeIfAbsent(itemId, id -> MarketPrice.builder()
				.itemId(id)
				.fetchedAtEpochSec(fetchedAtEpochSec)
				.build());

			existing.setAvgHighPrice1h(avgHigh);
			existing.setHighVolume1h(highVol);
			existing.setAvgLowPrice1h(avgLow);
			existing.setLowVolume1h(lowVol);
		}
	}

	private JsonObject extractDataObject(String json, String endpointName) throws IOException
	{
		if (json == null || json.trim().isEmpty())
		{
			throw new IOException("Empty JSON payload from " + endpointName);
		}
		try
		{
			JsonElement rootElement = gson.fromJson(json, JsonElement.class);
			if (rootElement == null || !rootElement.isJsonObject())
			{
				throw new IOException("Malformed JSON root from " + endpointName);
			}
			JsonObject root = rootElement.getAsJsonObject();
			if (!root.has("data") || !root.get("data").isJsonObject())
			{
				throw new IOException("Missing 'data' object in " + endpointName);
			}
			return root.getAsJsonObject("data");
		}
		catch (JsonSyntaxException ex)
		{
			throw new IOException("Invalid JSON syntax from " + endpointName + ": " + ex.getMessage(), ex);
		}
	}

	String executeWithRetry(String url) throws IOException
	{
		IOException lastException = null;
		long backoffMs = INITIAL_BACKOFF_MS;

		for (int attempt = 0; attempt <= MAX_RETRIES; attempt++)
		{
			if (Thread.currentThread().isInterrupted())
			{
				throw new IOException("HTTP request interrupted before execution.");
			}

			Request request = new Request.Builder()
				.url(url)
				.header("User-Agent", USER_AGENT)
				.header("Accept", "application/json")
				.get()
				.build();

			try (Response response = httpClient.newCall(request).execute())
			{
				int code = response.code();
				if (response.isSuccessful() && response.body() != null)
				{
					ResponseBody body = response.body();
					return body.string();
				}

				if (code == 429 || code >= 500)
				{
					lastException = new IOException("HTTP " + code + " from " + url);
				}
				else
				{
					throw new IOException("HTTP " + code + " from " + url);
				}
			}
			catch (IOException ex)
			{
				lastException = ex;
			}

			if (attempt < MAX_RETRIES)
			{
				try
				{
					Thread.sleep(backoffMs);
				}
				catch (InterruptedException ie)
				{
					Thread.currentThread().interrupt();
					throw new IOException("Retry backoff interrupted.", ie);
				}
				backoffMs *= 2;
			}
		}

		throw lastException != null ? lastException : new IOException("Failed to fetch " + url);
	}

	private static Integer getPositiveInt(JsonObject obj, String key)
	{
		if (obj.has(key) && !obj.get(key).isJsonNull())
		{
			try
			{
				int val = obj.get(key).getAsInt();
				return val > 0 ? val : null;
			}
			catch (Exception ignored)
			{
				return null;
			}
		}
		return null;
	}

	private static int getNonNegativeInt(JsonObject obj, String key)
	{
		if (obj.has(key) && !obj.get(key).isJsonNull())
		{
			try
			{
				int val = obj.get(key).getAsInt();
				return Math.max(0, val);
			}
			catch (Exception ignored)
			{
				return 0;
			}
		}
		return 0;
	}

	private static Long getPositiveLong(JsonObject obj, String key)
	{
		if (obj.has(key) && !obj.get(key).isJsonNull())
		{
			try
			{
				long val = obj.get(key).getAsLong();
				return val > 0 ? val : null;
			}
			catch (Exception ignored)
			{
				return null;
			}
		}
		return null;
	}
}
