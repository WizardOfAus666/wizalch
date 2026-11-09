package com.alchmatepro;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Sorting methods supported by the Live Alch Scanner.
 */
@Getter
@RequiredArgsConstructor
public enum SortOption
{
	HIGHEST_PROFIT_PER_ALCH("Highest Profit / Alch"),
	HIGHEST_ROI("Highest ROI %"),
	HIGHEST_PROFIT_100("Highest Profit / 100 Alchs"),
	HIGHEST_PROFIT_1000("Highest Profit / 1,000 Alchs"),
	HIGHEST_VOLUME("Highest Trading Volume"),
	LOWEST_INVESTMENT("Lowest Investment Required"),
	HIGHEST_ALCH_VALUE("Highest Alchemy Value"),
	HIGHEST_BUY_LIMIT("Highest GE Buying Limit");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
