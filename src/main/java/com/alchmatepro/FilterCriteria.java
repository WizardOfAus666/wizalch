package com.alchmatepro;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Comprehensive filtering and sorting criteria for the Live Alch Scanner (Section 15).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterCriteria
{
	public enum MembershipFilter
	{
		ALL("All Items"),
		MEMBERS_ONLY("Members Only (P2P)"),
		FREE_TO_PLAY_ONLY("Free-to-Play Only (F2P)");

		private final String label;

		MembershipFilter(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@Builder.Default
	private String searchQuery = "";

	/** Minimum net profit in GP (null if any profit/loss allowed unless profitableOnly is set). */
	private Long minProfitGp;

	/** Minimum ROI percentage (null if any ROI allowed). */
	private Double minRoiPercent;

	/** Maximum unit purchase price in GP (0 or null = no maximum). */
	private Long maxPurchasePriceGp;

	/** Minimum 1-hour trading volume (0 = no minimum). */
	@Builder.Default
	private int minTradeVolume1h = 0;

	/** Minimum 4-hour GE buying limit (0 = no minimum). */
	@Builder.Default
	private int minBuyLimit = 0;

	@Builder.Default
	private MembershipFilter membershipFilter = MembershipFilter.ALL;

	@Builder.Default
	private boolean profitableOnly = false;

	@Builder.Default
	private boolean hideLosses = false;

	@Builder.Default
	private boolean hideStaleData = false;

	@Builder.Default
	private boolean hideInactiveItems = false;

	private RecommendationCategory categoryFilter;

	@Builder.Default
	private SortOption sortOption = SortOption.HIGHEST_PROFIT_PER_ALCH;
}
