package com.alchmatepro;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Smart recommendation categories evaluated by the RecommendationEngine.
 */
@Getter
@RequiredArgsConstructor
public enum RecommendationCategory
{
	BEST_OVERALL("Best Overall", "Balanced profit, healthy volume, strong buy limit, and stable pricing"),
	BEST_PROFIT_PER_ALCH("Best Profit / Alch", "Highest net GP profit per individual High Level Alchemy cast"),
	BEST_ROI("Best ROI", "Highest percentage return on total GP invested"),
	BEST_BULK_ALCH("Best Bulk Alch", "Highest theoretical 4-hour cycle profit backed by real trading volume"),
	LOWEST_INVESTMENT("Lowest Investment", "Low capital requirement per item with reliable positive return"),
	HIGHEST_VOLUME("Highest Volume", "Most actively traded profitable items for fast GE order fulfilment"),
	LOWEST_RISK("Lowest Risk", "Tight buy/sell spread, fresh trades, and high price stability");

	private final String title;
	private final String summary;

	@Override
	public String toString()
	{
		return title;
	}
}
