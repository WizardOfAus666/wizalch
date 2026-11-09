package com.alchmatepro;

import java.io.IOException;
import java.util.Map;

/**
 * Abstraction for Grand Exchange market price providers (such as {@link WikiPriceDataProvider})
 * without coupling business logic or UI components to a single HTTP endpoint.
 */
public interface MarketDataProvider
{
	/**
	 * Display name of the market data provider.
	 */
	String getProviderName();

	/**
	 * Fetches a bulk map of item ID -> {@link MarketPrice} for all available OSRS items.
	 *
	 * @throws IOException if the provider is unavailable, rate-limited, or returns invalid data
	 */
	Map<Integer, MarketPrice> fetchMarketPrices() throws IOException;

	/**
	 * Returns true if the provider is currently healthy and operational.
	 */
	boolean isAvailable();

	/**
	 * Returns a human-readable status or diagnostic note for the Market Data transparency page.
	 */
	String getStatusSummary();
}
