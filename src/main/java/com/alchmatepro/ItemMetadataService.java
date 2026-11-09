package com.alchmatepro;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 * Retrieves and caches OSRS item metadata from the OSRS Wiki /mapping endpoint
 * (https://prices.runescape.wiki/api/v1/osrs/mapping).
 * Extracts item ID, name, examine text, members status, low alch, high alch, and 4h GE buy limit.
 */
@Slf4j
@Singleton
public class ItemMetadataService implements ItemMetadataProvider
{
	public static final String DEFAULT_MAPPING_URL = "https://prices.runescape.wiki/api/v1/osrs/mapping";

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final CacheService cacheService;
	private final Map<Integer, ItemMetadata> metadataCache = new ConcurrentHashMap<>();

	@Getter
	@Setter
	private volatile String mappingUrl = DEFAULT_MAPPING_URL;

	@Inject
	public ItemMetadataService(OkHttpClient okHttpClient, Gson gson, CacheService cacheService)
	{
		this.httpClient = okHttpClient.newBuilder()
			.connectTimeout(10, TimeUnit.SECONDS)
			.readTimeout(15, TimeUnit.SECONDS)
			.build();
		this.gson = gson;
		this.cacheService = cacheService;
	}

	@Override
	public synchronized Map<Integer, ItemMetadata> fetchItemMetadata() throws IOException
	{
		if (!metadataCache.isEmpty())
		{
			return Collections.unmodifiableMap(metadataCache);
		}

		// Check persistent local cache first if offline or before network fetch
		Request request = new Request.Builder()
			.url(mappingUrl)
			.header("User-Agent", WikiPriceDataProvider.USER_AGENT)
			.header("Accept", "application/json")
			.get()
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful() || response.body() == null)
			{
				throw new IOException("Failed to fetch /mapping (HTTP " + response.code() + ")");
			}
			ResponseBody body = response.body();
			String json = body.string();
			Map<Integer, ItemMetadata> parsed = parseMappingJson(json);
			if (parsed.isEmpty())
			{
				throw new IOException("/mapping endpoint returned 0 alchable items.");
			}

			metadataCache.clear();
			metadataCache.putAll(parsed);
			if (cacheService != null)
			{
				cacheService.saveMetadataCache(parsed);
			}
			return Collections.unmodifiableMap(metadataCache);
		}
		catch (IOException ex)
		{
			log.warn("Network /mapping fetch failed ({}), attempting fallback to cached metadata", ex.getMessage());
			if (cacheService != null)
			{
				Map<Integer, ItemMetadata> cached = cacheService.loadMetadataCache();
				if (cached != null && !cached.isEmpty())
				{
					metadataCache.putAll(cached);
					return Collections.unmodifiableMap(metadataCache);
				}
			}
			throw ex;
		}
	}

	/**
	 * Parses the JSON array returned by /mapping into itemId -> {@link ItemMetadata}.
	 */
	public Map<Integer, ItemMetadata> parseMappingJson(String json) throws IOException
	{
		if (json == null || json.trim().isEmpty())
		{
			throw new IOException("Empty /mapping JSON response.");
		}

		try
		{
			JsonElement root = gson.fromJson(json, JsonElement.class);
			if (root == null || !root.isJsonArray())
			{
				throw new IOException("Expected JSON array from /mapping endpoint.");
			}

			JsonArray arr = root.getAsJsonArray();
			Map<Integer, ItemMetadata> map = new HashMap<>(arr.size());

			for (JsonElement el : arr)
			{
				if (!el.isJsonObject())
				{
					continue;
				}
				JsonObject obj = el.getAsJsonObject();
				int id = getInt(obj, "id", -1);
				String name = getString(obj, "name", "");
				if (id <= 0 || name.isEmpty())
				{
					continue;
				}

				int highAlch = getInt(obj, "highalch", 0);
				int lowAlch = getInt(obj, "lowalch", 0);
				int limit = getInt(obj, "limit", 0);
				int value = getInt(obj, "value", 0);
				boolean members = getBoolean(obj, "members", false);
				String examine = getString(obj, "examine", "");
				String icon = getString(obj, "icon", "");

				// Also keep Nature rune (561) and Fire rune (554) in metadata map for rune cost lookups,
				// alongside all items with a positive High Level Alchemy value.
				if (highAlch <= 0 && id != 561 && id != 554)
				{
					continue;
				}

				ItemMetadata metadata = ItemMetadata.builder()
					.id(id)
					.name(name)
					.examine(examine)
					.members(members)
					.lowAlchValue(Math.max(0, lowAlch))
					.highAlchValue(Math.max(0, highAlch))
					.buyLimit(Math.max(0, limit))
					.baseValue(Math.max(0, value))
					.iconName(icon)
					.build();

				map.put(id, metadata);
			}

			return map;
		}
		catch (JsonSyntaxException ex)
		{
			throw new IOException("Invalid JSON in /mapping response: " + ex.getMessage(), ex);
		}
	}

	public Map<Integer, ItemMetadata> getCachedMetadata()
	{
		return Collections.unmodifiableMap(metadataCache);
	}

	public ItemMetadata getItemMetadata(int itemId)
	{
		return metadataCache.get(itemId);
	}

	public void putMetadataForTesting(Map<Integer, ItemMetadata> items)
	{
		metadataCache.clear();
		if (items != null)
		{
			metadataCache.putAll(items);
		}
	}

	private static int getInt(JsonObject obj, String key, int defaultValue)
	{
		if (obj.has(key) && !obj.get(key).isJsonNull())
		{
			try
			{
				return (int) Math.round(obj.get(key).getAsDouble());
			}
			catch (Exception ignored)
			{
				return defaultValue;
			}
		}
		return defaultValue;
	}

	private static String getString(JsonObject obj, String key, String defaultValue)
	{
		if (obj.has(key) && !obj.get(key).isJsonNull())
		{
			try
			{
				return obj.get(key).getAsString();
			}
			catch (Exception ignored)
			{
				return defaultValue;
			}
		}
		return defaultValue;
	}

	private static boolean getBoolean(JsonObject obj, String key, boolean defaultValue)
	{
		if (obj.has(key) && !obj.get(key).isJsonNull())
		{
			try
			{
				return obj.get(key).getAsBoolean();
			}
			catch (Exception ignored)
			{
				return defaultValue;
			}
		}
		return defaultValue;
	}
}
