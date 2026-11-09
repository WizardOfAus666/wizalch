package com.alchmatepro;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

/**
 * Thread-safe in-memory and disk cache for item metadata and market prices.
 * Ensures fast startup, resilience during temporary API outages, and transparent
 * cache telemetry for the Market Data page.
 */
@Slf4j
@Singleton
public class CacheService
{
	private static final String CACHE_SUBDIR = "alchmate-pro";
	private static final String METADATA_CACHE_FILE = "item_metadata_cache.json";
	private static final String PRICES_CACHE_FILE = "market_prices_cache.json";

	private final Gson gson;
	private final File cacheDirectory;

	private final Map<Integer, ItemMetadata> memoryMetadata = new ConcurrentHashMap<>();
	private final Map<Integer, MarketPrice> memoryPrices = new ConcurrentHashMap<>();

	@Getter
	private volatile long lastCacheWriteEpochSec = 0L;

	@Getter
	private volatile boolean diskCacheHealthy = true;

	@Inject
	public CacheService(Gson gson)
	{
		this(gson, resolveDefaultCacheDir());
	}

	public CacheService(Gson gson, File cacheDirectory)
	{
		this.gson = gson;
		this.cacheDirectory = cacheDirectory;
		try
		{
			if (this.cacheDirectory != null && !this.cacheDirectory.exists())
			{
				this.cacheDirectory.mkdirs();
			}
		}
		catch (Exception ex)
		{
			log.debug("Could not create cache directory: {}", ex.getMessage());
			this.diskCacheHealthy = false;
		}
	}

	private static File resolveDefaultCacheDir()
	{
		try
		{
			if (RuneLite.CACHE_DIR != null)
			{
				return new File(RuneLite.CACHE_DIR, CACHE_SUBDIR);
			}
		}
		catch (Throwable ignored)
		{
		}
		return new File(System.getProperty("java.io.tmpdir"), CACHE_SUBDIR);
	}

	public void saveMetadataCache(Map<Integer, ItemMetadata> metadataMap)
	{
		if (metadataMap == null || metadataMap.isEmpty())
		{
			return;
		}
		memoryMetadata.clear();
		memoryMetadata.putAll(metadataMap);
		lastCacheWriteEpochSec = Instant.now().getEpochSecond();

		if (cacheDirectory == null)
		{
			return;
		}
		File target = new File(cacheDirectory, METADATA_CACHE_FILE);
		try (FileWriter writer = new FileWriter(target, StandardCharsets.UTF_8))
		{
			gson.toJson(metadataMap, writer);
			diskCacheHealthy = true;
		}
		catch (Exception ex)
		{
			log.debug("Failed to persist metadata cache to disk: {}", ex.getMessage());
			diskCacheHealthy = false;
		}
	}

	public Map<Integer, ItemMetadata> loadMetadataCache()
	{
		if (!memoryMetadata.isEmpty())
		{
			return Collections.unmodifiableMap(memoryMetadata);
		}
		if (cacheDirectory == null)
		{
			return Collections.emptyMap();
		}
		File target = new File(cacheDirectory, METADATA_CACHE_FILE);
		if (!target.exists() || !target.isFile())
		{
			return Collections.emptyMap();
		}
		try (FileReader reader = new FileReader(target, StandardCharsets.UTF_8))
		{
			Map<Integer, ItemMetadata> loaded = gson.fromJson(
				reader,
				new TypeToken<Map<Integer, ItemMetadata>>()
				{
				}.getType()
			);
			if (loaded != null && !loaded.isEmpty())
			{
				memoryMetadata.putAll(loaded);
				return Collections.unmodifiableMap(memoryMetadata);
			}
		}
		catch (Exception ex)
		{
			log.warn("Failed to read metadata cache from disk: {}", ex.getMessage());
			diskCacheHealthy = false;
		}
		return Collections.emptyMap();
	}

	public void savePricesCache(Map<Integer, MarketPrice> pricesMap)
	{
		if (pricesMap == null || pricesMap.isEmpty())
		{
			return;
		}
		memoryPrices.clear();
		memoryPrices.putAll(pricesMap);
		lastCacheWriteEpochSec = Instant.now().getEpochSecond();

		if (cacheDirectory == null)
		{
			return;
		}
		File target = new File(cacheDirectory, PRICES_CACHE_FILE);
		try (FileWriter writer = new FileWriter(target, StandardCharsets.UTF_8))
		{
			gson.toJson(pricesMap, writer);
			diskCacheHealthy = true;
		}
		catch (Exception ex)
		{
			log.debug("Failed to persist prices cache to disk: {}", ex.getMessage());
			diskCacheHealthy = false;
		}
	}

	public Map<Integer, MarketPrice> loadPricesCache()
	{
		if (!memoryPrices.isEmpty())
		{
			return Collections.unmodifiableMap(memoryPrices);
		}
		if (cacheDirectory == null)
		{
			return Collections.emptyMap();
		}
		File target = new File(cacheDirectory, PRICES_CACHE_FILE);
		if (!target.exists() || !target.isFile())
		{
			return Collections.emptyMap();
		}
		try (FileReader reader = new FileReader(target, StandardCharsets.UTF_8))
		{
			Map<Integer, MarketPrice> loaded = gson.fromJson(
				reader,
				new TypeToken<Map<Integer, MarketPrice>>()
				{
				}.getType()
			);
			if (loaded != null && !loaded.isEmpty())
			{
				memoryPrices.putAll(loaded);
				return Collections.unmodifiableMap(memoryPrices);
			}
		}
		catch (Exception ex)
		{
			log.warn("Failed to read prices cache from disk: {}", ex.getMessage());
			diskCacheHealthy = false;
		}
		return Collections.emptyMap();
	}

	public void clearCache()
	{
		memoryMetadata.clear();
		memoryPrices.clear();
		if (cacheDirectory != null)
		{
			new File(cacheDirectory, METADATA_CACHE_FILE).delete();
			new File(cacheDirectory, PRICES_CACHE_FILE).delete();
		}
	}

	public String getCacheStatusSummary()
	{
		int metaSize = memoryMetadata.size();
		int priceSize = memoryPrices.size();
		if (metaSize == 0 && priceSize == 0)
		{
			return "Warming up (0 cached records)";
		}
		return String.format(
			"Active (%d metadata, %d prices cached | Disk: %s)",
			metaSize,
			priceSize,
			diskCacheHealthy ? "OK" : "Memory-Only"
		);
	}
}
