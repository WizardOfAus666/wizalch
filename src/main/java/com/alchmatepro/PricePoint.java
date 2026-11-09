package com.alchmatepro;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Single historical time-series data point from the OSRS Wiki /timeseries endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricePoint
{
	private long timestampEpochSec;
	private Integer avgHighPrice;
	private int highPriceVolume;
	private Integer avgLowPrice;
	private int lowPriceVolume;

	public int getTotalVolume()
	{
		return Math.max(0, highPriceVolume) + Math.max(0, lowPriceVolume);
	}

	public Integer getEffectiveBuyPrice()
	{
		if (avgHighPrice != null && avgHighPrice > 0)
		{
			return avgHighPrice;
		}
		return avgLowPrice;
	}
}
