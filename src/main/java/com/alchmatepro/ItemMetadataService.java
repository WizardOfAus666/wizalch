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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
 * Retrieves and caches OSRS item metadata from the OSRS Wiki /mapping endpoint
 * (https://prices.runescape.wiki/api/v1/osrs/mapping).
 * Extracts item ID, name, examine text, members status, low alch, high alch, and 4h GE buy limit.
 */
@Slf4j
@Singleton
public class ItemMetadataService implements ItemMetadataProvider
{
	public static final String DEFAULT_MAPPING_URL = "https://prices.runescape.wiki/api/v1/osrs/mapping";
	public static final String DEFAULT_UNALCHABLE_CATEGORY_URL =
		"https://oldschool.runescape.wiki/api.php?action=query&list=categorymembers&cmtitle=Category:Items_that_cannot_be_alchemised&cmlimit=500&format=json";
	private static final long UNALCHABLE_RETRY_COOLDOWN_SEC = 600L;

	private static final Set<Integer> BUILTIN_UNALCHABLE_IDS = Set.of(
		995,   // Coins
		6306,  // Trading sticks
		13190, // Old school bond
		13192, // Old school bond (untradeable)
		13204, // Platinum token
		21555  // Numulite
	);

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final CacheService cacheService;
	private final Map<Integer, ItemMetadata> metadataCache = new ConcurrentHashMap<>();
	private final Set<String> unalchableNames = ConcurrentHashMap.newKeySet();

	private volatile boolean unalchableListLoaded = false;
	private volatile long lastUnalchableAttemptEpochSec = 0L;

	@Getter
	@Setter
	private volatile String mappingUrl = DEFAULT_MAPPING_URL;

	@Getter
	@Setter
	private volatile String unalchableCategoryUrl = DEFAULT_UNALCHABLE_CATEGORY_URL;

	@Inject
	public ItemMetadataService(OkHttpClient okHttpClient, Gson gson, CacheService cacheService)
	{
		this.httpClient = okHttpClient;
		this.gson = gson;
		this.cacheService = cacheService;
		seedBuiltinUnalchableNames();
	}

	private void seedBuiltinUnalchableNames()
	{
		unalchableNames.add("old school bond");
		unalchableNames.add("old school bond (untradeable)");
		unalchableNames.add("coins");
		unalchableNames.add("platinum token");
		unalchableNames.add("trading sticks");
		unalchableNames.add("numulite");
		unalchableNames.add("shantay pass");
		unalchableNames.add("mole claw");
		unalchableNames.add("mole skin");
		unalchableNames.add("ecumenical key");
	}

	@Override
	public synchronized Map<Integer, ItemMetadata> fetchItemMetadata() throws IOException
	{
		if (!metadataCache.isEmpty())
		{
			if (!unalchableListLoaded && lastUnalchableAttemptEpochSec > 0)
			{
				loadUnalchableItemsIfNeeded();
			}
			return Collections.unmodifiableMap(metadataCache);
		}

		loadUnalchableItemsIfNeeded();

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
				if (id != 561 && id != 554 && isUnalchable(id, name))
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

	/**
	 * Fetches the OSRS Wiki Category:Items_that_cannot_be_alchemised once per session.
	 * If the wiki cannot be reached, the scanner continues unfiltered and retries after 10 minutes.
	 */
	public void loadUnalchableItemsIfNeeded()
	{
		if (unalchableListLoaded)
		{
			return;
		}
		long nowSec = Instant.now().getEpochSecond();
		if (lastUnalchableAttemptEpochSec > 0 && (nowSec - lastUnalchableAttemptEpochSec) < UNALCHABLE_RETRY_COOLDOWN_SEC)
		{
			return;
		}
		lastUnalchableAttemptEpochSec = nowSec;

		try
		{
			String cmContinue = null;
			for (int page = 0; page < 5; page++)
			{
				String url = cmContinue == null
					? unalchableCategoryUrl
					: unalchableCategoryUrl + "&cmcontinue=" + cmContinue;

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
						return;
					}
					String json = response.body().string();
					cmContinue = parseUnalchableCategoryJson(json);
					if (cmContinue == null || cmContinue.isEmpty())
					{
						break;
					}
				}
			}
			unalchableListLoaded = true;
			metadataCache.entrySet().removeIf(e ->
				e.getKey() != 561 && e.getKey() != 554 && isUnalchable(e.getValue())
			);
		}
		catch (Exception ex)
		{
			log.debug("Could not load unalchable category from OSRS Wiki (will retry in 10m): {}", ex.getMessage());
		}
	}

	/**
	 * Parses a MediaWiki categorymembers JSON response, adds all item titles to {@code unalchableNames},
	 * and returns the next {@code cmcontinue} token if additional pages exist.
	 */
	String parseUnalchableCategoryJson(String json) throws IOException
	{
		if (json == null || json.trim().isEmpty())
		{
			return null;
		}
		try
		{
			JsonElement rootEl = gson.fromJson(json, JsonElement.class);
			if (rootEl == null || !rootEl.isJsonObject())
			{
				return null;
			}
			JsonObject root = rootEl.getAsJsonObject();
			if (root.has("query") && root.get("query").isJsonObject())
			{
				JsonObject query = root.getAsJsonObject("query");
				if (query.has("categorymembers") && query.get("categorymembers").isJsonArray())
				{
					JsonArray members = query.getAsJsonArray("categorymembers");
					for (JsonElement el : members)
					{
						if (el.isJsonObject())
						{
							String title = getString(el.getAsJsonObject(), "title", "");
							if (!title.isEmpty() && !title.startsWith("Category:"))
							{
								unalchableNames.add(normalizeItemName(title));
							}
						}
					}
				}
			}
			if (root.has("continue") && root.get("continue").isJsonObject())
			{
				JsonObject cont = root.getAsJsonObject("continue");
				String nextToken = getString(cont, "cmcontinue", "");
				return nextToken.isEmpty() ? null : nextToken;
			}
			return null;
		}
		catch (JsonSyntaxException ex)
		{
			throw new IOException("Invalid JSON from unalchable category endpoint", ex);
		}
	}

	public boolean isUnalchable(ItemMetadata metadata)
	{
		if (metadata == null)
		{
			return true;
		}
		return isUnalchable(metadata.getId(), metadata.getName());
	}

	public boolean isUnalchable(int id, String name)
	{
		if (BUILTIN_UNALCHABLE_IDS.contains(id))
		{
			return true;
		}
		if (name == null || name.isEmpty())
		{
			return false;
		}
		return unalchableNames.contains(normalizeItemName(name));
	}

	private static String normalizeItemName(String name)
	{
		return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
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
