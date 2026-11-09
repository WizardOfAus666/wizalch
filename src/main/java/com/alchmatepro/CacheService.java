package com.alchmatepro;

import com.google.gson.Gson;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;

/**
 * Thread-safe in-memory cache for item metadata and market prices.
 * Ensures resilience during temporary API outages and transparent
 * cache telemetry for the Market Data page without using java.io/java.nio file access.
 */
@Singleton
public class CacheService
{
	private final Map<Integer, ItemMetadata> memoryMetadata = new ConcurrentHashMap<>();
	private final Map<Integer, MarketPrice> memoryPrices = new ConcurrentHashMap<>();

	@Getter
	private volatile long lastCacheWriteEpochSec = 0L;

	@Getter
	private volatile boolean diskCacheHealthy = true;

	@Inject
	public CacheService(Gson gson)
	{
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
	}

	public Map<Integer, ItemMetadata> loadMetadataCache()
	{
		if (!memoryMetadata.isEmpty())
		{
			return Collections.unmodifiableMap(memoryMetadata);
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
	}

	public Map<Integer, MarketPrice> loadPricesCache()
	{
		if (!memoryPrices.isEmpty())
		{
			return Collections.unmodifiableMap(memoryPrices);
		}
		return Collections.emptyMap();
	}

	public void clearCache()
	{
		memoryMetadata.clear();
		memoryPrices.clear();
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
			"Active (%d metadata, %d prices cached in memory)",
			metaSize,
			priceSize
		);
	}
}
