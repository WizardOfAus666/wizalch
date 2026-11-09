package com.alchmatepro;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
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
 * Primary provider targeting AlchMate (https://alchmate.com/osrs_high_alch).
 *
 * Architectural Note:
 * AlchMate is a web application that renders HTML and protects its internal routes without
 * publishing an open developer JSON endpoint for third-party RuneLite plugins.
 * Per RuneLite Plugin Hub rules and ethical API rules, this provider never scrapes HTML
 * or bypasses access controls. It checks whether a valid JSON payload is served at the
 * configured endpoint; if HTML or an access restriction is returned, it cleanly reports
 * the limitation so {@link MarketDataService} seamlessly activates {@link WikiPriceDataProvider}.
 */
@Slf4j
@Singleton
public class AlchMateDataProvider implements MarketDataProvider
{
	public static final String DEFAULT_ENDPOINT = "https://alchmate.com/api/osrs/high-alch";
	public static final String USER_AGENT = "WIZALCH-RuneLite-Plugin/1.0 (+https://github.com/runelite/plugin-hub)";

	private final OkHttpClient httpClient;
	private final Gson gson;

	@Getter
	@Setter
	private volatile String endpointUrl = DEFAULT_ENDPOINT;

	@Getter
	@Setter
	private volatile boolean networkProbeEnabled = false;

	private volatile boolean available = false;
	private volatile String statusSummary = "AlchMate web app does not expose a public JSON API; OSRS Wiki Real-Time API active.";
	private volatile long lastProbeEpochSec = 0L;
	private static final long PROBE_COOLDOWN_SECONDS = 600L;

	@Inject
	public AlchMateDataProvider(OkHttpClient okHttpClient, Gson gson)
	{
		this.httpClient = okHttpClient.newBuilder()
			.connectTimeout(8, TimeUnit.SECONDS)
			.readTimeout(10, TimeUnit.SECONDS)
			.build();
		this.gson = gson;
	}

	@Override
	public String getProviderName()
	{
		return "AlchMate Data Provider";
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
	public Map<Integer, MarketPrice> fetchMarketPrices() throws IOException
	{
		if (!networkProbeEnabled)
		{
			throw new IOException(statusSummary);
		}
		long now = Instant.now().getEpochSecond();
		if (!available && (now - lastProbeEpochSec) < PROBE_COOLDOWN_SECONDS)
		{
			throw new IOException(statusSummary);
		}
		lastProbeEpochSec = now;

		Request request = new Request.Builder()
			.url(endpointUrl)
			.header("User-Agent", USER_AGENT)
			.header("Accept", "application/json")
			.get()
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				available = false;
				statusSummary = "AlchMate public JSON endpoint unavailable (HTTP " + response.code()
					+ "); using OSRS Wiki Real-Time Prices API.";
				throw new IOException(statusSummary);
			}

			String contentType = response.header("Content-Type", "");
			ResponseBody body = response.body();
			if (body == null)
			{
				available = false;
				statusSummary = "AlchMate returned an empty response body; using OSRS Wiki fallback.";
				throw new IOException(statusSummary);
			}

			String raw = body.string().trim();
			if (raw.isEmpty() || raw.startsWith("<") || contentType.contains("text/html"))
			{
				available = false;
				statusSummary = "AlchMate serves HTML rather than a public JSON API; OSRS Wiki Real-Time API active.";
				throw new IOException(statusSummary);
			}

			Map<Integer, MarketPrice> parsed = parseJsonPayload(raw, now);
			if (parsed.isEmpty())
			{
				available = false;
				statusSummary = "AlchMate JSON contained no valid item price records; OSRS Wiki fallback active.";
				throw new IOException(statusSummary);
			}

			available = true;
			statusSummary = "Connected to AlchMate JSON feed (" + parsed.size() + " items)";
			return parsed;
		}
		catch (JsonSyntaxException ex)
		{
			available = false;
			statusSummary = "AlchMate response is not valid JSON; OSRS Wiki Real-Time API active.";
			throw new IOException(statusSummary, ex);
		}
		catch (IOException ex)
		{
			available = false;
			if (statusSummary == null || statusSummary.isEmpty())
			{
				statusSummary = "AlchMate unreachable (" + ex.getMessage() + "); OSRS Wiki fallback active.";
			}
			throw ex;
		}
	}

	/**
	 * Parses a structured JSON payload if a compatible JSON feed is provided.
	 */
	Map<Integer, MarketPrice> parseJsonPayload(String json, long fetchedAtEpochSec) throws JsonSyntaxException
	{
		if (json == null || json.trim().isEmpty())
		{
			return Collections.emptyMap();
		}

		JsonElement root = gson.fromJson(json, JsonElement.class);
		if (root == null)
		{
			return Collections.emptyMap();
		}

		Map<Integer, MarketPrice> result = new HashMap<>();
		if (root.isJsonArray())
		{
			JsonArray arr = root.getAsJsonArray();
			for (JsonElement el : arr)
			{
				if (el.isJsonObject())
				{
					MarketPrice mp = parseSingleObject(el.getAsJsonObject(), 0, fetchedAtEpochSec);
					if (mp != null && mp.getItemId() > 0)
					{
						result.put(mp.getItemId(), mp);
					}
				}
			}
		}
		else if (root.isJsonObject())
		{
			JsonObject obj = root.getAsJsonObject();
			JsonObject dataObj = obj.has("data") && obj.get("data").isJsonObject()
				? obj.getAsJsonObject("data")
				: obj;
			for (Map.Entry<String, JsonElement> entry : dataObj.entrySet())
			{
				try
				{
					int itemId = Integer.parseInt(entry.getKey());
					if (entry.getValue().isJsonObject())
					{
						MarketPrice mp = parseSingleObject(entry.getValue().getAsJsonObject(), itemId, fetchedAtEpochSec);
						if (mp != null)
						{
							result.put(itemId, mp);
						}
					}
				}
				catch (NumberFormatException ignored)
				{
					// Ignore non-numeric metadata keys
				}
			}
		}

		return result;
	}

	private MarketPrice parseSingleObject(JsonObject obj, int fallbackId, long fetchedAtEpochSec)
	{
		int id = getInt(obj, "id", fallbackId);
		if (id <= 0)
		{
			id = getInt(obj, "itemId", fallbackId);
		}
		if (id <= 0)
		{
			return null;
		}

		Integer high = getNullableInt(obj, "high", "buyPrice", "instantBuyPrice");
		Integer low = getNullableInt(obj, "low", "sellPrice", "instantSellPrice");
		Long highTime = getNullableLong(obj, "highTime", "buyTime", "timestamp");
		Long lowTime = getNullableLong(obj, "lowTime", "sellTime");
		Integer avgHigh1h = getNullableInt(obj, "avgHighPrice1h", "avgHighPrice", "stablePrice");
		int vol1h = getInt(obj, "volume1h", getInt(obj, "volume", 0));

		if (high == null && low == null && avgHigh1h == null)
		{
			return null;
		}

		return MarketPrice.builder()
			.itemId(id)
			.instantBuyPrice(high)
			.instantBuyTimeEpochSec(highTime != null ? highTime : fetchedAtEpochSec)
			.instantSellPrice(low)
			.instantSellTimeEpochSec(lowTime != null ? lowTime : fetchedAtEpochSec)
			.avgHighPrice1h(avgHigh1h)
			.highVolume1h(vol1h)
			.fetchedAtEpochSec(fetchedAtEpochSec)
			.build();
	}

	private static int getInt(JsonObject obj, String key, int defaultValue)
	{
		if (obj.has(key) && !obj.get(key).isJsonNull())
		{
			try
			{
				return obj.get(key).getAsInt();
			}
			catch (Exception ignored)
			{
				return defaultValue;
			}
		}
		return defaultValue;
	}

	private static Integer getNullableInt(JsonObject obj, String... keys)
	{
		for (String key : keys)
		{
			if (obj.has(key) && !obj.get(key).isJsonNull())
			{
				try
				{
					int v = obj.get(key).getAsInt();
					if (v > 0)
					{
						return v;
					}
				}
				catch (Exception ignored)
				{
				}
			}
		}
		return null;
	}

	private static Long getNullableLong(JsonObject obj, String... keys)
	{
		for (String key : keys)
		{
			if (obj.has(key) && !obj.get(key).isJsonNull())
			{
				try
				{
					long v = obj.get(key).getAsLong();
					if (v > 0)
					{
						return v;
					}
				}
				catch (Exception ignored)
				{
				}
			}
		}
		return null;
	}
}
