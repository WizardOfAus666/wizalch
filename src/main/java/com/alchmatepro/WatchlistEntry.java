package com.alchmatepro;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Persistent watchlist item stored via RuneLite ConfigManager.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistEntry
{
	private int itemId;
	private String itemName;
	private long addedEpochSec;
	private boolean favourite;

	/** Optional user-configured target net profit per alch (GP). */
	private int targetProfitGp;

	/** Optional user-configured minimum ROI percentage. */
	private double minimumRoiPercent;

	/** Baseline purchase price when the item was added or last reset, for tracking price delta. */
	private long baselinePurchasePrice;

	/** Baseline net profit per alch when added or last reset. */
	private long baselineProfitPerAlch;

	/** Whether individual profit alerts are enabled for this watchlist item. */
	@Builder.Default
	private boolean alertEnabled = true;
}
