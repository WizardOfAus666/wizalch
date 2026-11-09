package com.alchmatepro;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Explicitly distinguishes the underlying market price type used for an estimate.
 */
@Getter
@RequiredArgsConstructor
public enum PriceSourceType
{
	INSTANT_BUY_PRICE("Instant Buy (Latest High)", false),
	INSTANT_SELL_PRICE("Instant Sell (Latest Low)", true),
	RECENT_5M_AVERAGE("5m Avg Buy (Estimate)", true),
	STABLE_1H_AVERAGE("Stable 1h Avg (Estimate)", true),
	CONSERVATIVE_ESTIMATE("Conservative Max (Estimate)", true),
	OFFICIAL_GE_GUIDE("Official GE Guide (Estimate)", true),
	UNAVAILABLE("Price Unavailable", true);

	private final String label;
	private final boolean estimate;
}
