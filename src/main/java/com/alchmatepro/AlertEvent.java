package com.alchmatepro;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a triggered profit/ROI/price alert event with timestamp and details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvent
{
	public enum AlertType
	{
		PROFIT_THRESHOLD_EXCEEDED("Profit Threshold Met"),
		ROI_THRESHOLD_EXCEEDED("ROI Target Met"),
		BECAME_PROFITABLE("Item Became Profitable"),
		PROFIT_DROPPED_BELOW("Profit Dropped Below Target"),
		SIGNIFICANT_PRICE_CHANGE("Significant Price Movement");

		private final String label;

		AlertType(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	private int itemId;
	private String itemName;
	private AlertType alertType;
	private String message;
	private long triggeredAtEpochSec;
	private long netProfitPerAlch;
	private double roiPercent;
	private long purchasePrice;
}
