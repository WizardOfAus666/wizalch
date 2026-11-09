package com.alchmatepro;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summarises historical price movement and whether the high-alchemy margin is improving or declining.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceTrendSummary
{
	public enum MarginDirection
	{
		IMPROVING("Improving (Buy Price Falling)"),
		DECLINING("Declining (Buy Price Rising)"),
		STABLE("Stable Margin"),
		INSUFFICIENT_DATA("Insufficient History");

		private final String label;

		MarginDirection(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	private int itemId;
	@Builder.Default
	private List<PricePoint> points = Collections.emptyList();
	private int minBuyPrice;
	private int maxBuyPrice;
	private int averageBuyPrice;
	private int firstBuyPrice;
	private int latestBuyPrice;
	private long priceChangeGp;
	private double priceChangePercent;
	private double volatilityPercent;
	@Builder.Default
	private MarginDirection marginDirection = MarginDirection.INSUFFICIENT_DATA;
}
