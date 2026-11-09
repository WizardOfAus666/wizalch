package com.alchmatepro;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Market safety warnings attached to items when risk or data quality flags trigger.
 */
@Getter
@RequiredArgsConstructor
public enum MarketWarning
{
	LOW_VOLUME("LOW VOLUME", "Low recent trade volume; Grand Exchange buy offers may fill slowly or partially."),
	PRICE_UNSTABLE("PRICE UNSTABLE", "Current instant-buy price diverges significantly from the 1-hour average."),
	STALE_DATA("STALE DATA", "Last recorded instant-buy trade is older than the configured freshness threshold."),
	HIGH_INVESTMENT("HIGH INVESTMENT", "High unit purchase price requires substantial capital relative to alch margin."),
	POSSIBLE_PRICE_MANIPULATION("POSSIBLE PRICE MANIPULATION", "Abnormal bid/ask spread or sudden price spike detected."),
	INSUFFICIENT_DATA("INSUFFICIENT DATA", "Missing verified buy price, high alchemy value, or recent trade volume.");

	private final String badgeText;
	private final String explanation;
}
