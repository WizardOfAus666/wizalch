package com.alchmatepro;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Multi-horizon Grand Exchange price and volume snapshot for a single item.
 * Distinguishes between instant-buy (high), instant-sell (low), 5m rolling average,
 * 1h stable average, and official GE guide price.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketPrice
{
	private int itemId;

	/** Latest instant-buy price (buyer pays to buy immediately). */
	private Integer instantBuyPrice;
	private Long instantBuyTimeEpochSec;

	/** Latest instant-sell price (seller receives when selling immediately). */
	private Integer instantSellPrice;
	private Long instantSellTimeEpochSec;

	/** 5-minute rolling average prices and volumes. */
	private Integer avgHighPrice5m;
	private int highVolume5m;
	private Integer avgLowPrice5m;
	private int lowVolume5m;

	/** 1-hour stable rolling average prices and volumes. */
	private Integer avgHighPrice1h;
	private int highVolume1h;
	private Integer avgLowPrice1h;
	private int lowVolume1h;

	/** Official Grand Exchange guide price where available. */
	private Integer officialGePrice;

	/** Timestamp when this record was fetched by the client (epoch seconds). */
	private long fetchedAtEpochSec;

	public int getTotalVolume5m()
	{
		return Math.max(0, highVolume5m) + Math.max(0, lowVolume5m);
	}

	public int getTotalVolume1h()
	{
		return Math.max(0, highVolume1h) + Math.max(0, lowVolume1h);
	}

	public long getEstimatedDailyVolume()
	{
		return (long) getTotalVolume1h() * 24L;
	}

	public long getLatestTradeEpochSec()
	{
		long highT = instantBuyTimeEpochSec != null ? instantBuyTimeEpochSec : 0L;
		long lowT = instantSellTimeEpochSec != null ? instantSellTimeEpochSec : 0L;
		return Math.max(highT, lowT);
	}

	public long getBuyTradeAgeSeconds(long nowEpochSec)
	{
		if (instantBuyTimeEpochSec == null || instantBuyTimeEpochSec <= 0)
		{
			return Long.MAX_VALUE;
		}
		return Math.max(0L, nowEpochSec - instantBuyTimeEpochSec);
	}

	public Integer getSpreadGp()
	{
		if (instantBuyPrice != null && instantBuyPrice > 0 && instantSellPrice != null && instantSellPrice > 0)
		{
			return instantBuyPrice - instantSellPrice;
		}
		return null;
	}

	public Double getSpreadPercent()
	{
		if (instantBuyPrice != null && instantBuyPrice > 0 && instantSellPrice != null && instantSellPrice > 0)
		{
			return ((double) (instantBuyPrice - instantSellPrice) / (double) instantSellPrice) * 100.0;
		}
		return null;
	}

	/**
	 * Resolves the stable reference price (prefers 1h avg high, then 5m avg high, then official GE).
	 */
	public Integer getStableReferencePrice()
	{
		if (avgHighPrice1h != null && avgHighPrice1h > 0)
		{
			return avgHighPrice1h;
		}
		if (avgHighPrice5m != null && avgHighPrice5m > 0)
		{
			return avgHighPrice5m;
		}
		if (avgLowPrice1h != null && avgLowPrice1h > 0)
		{
			return avgLowPrice1h;
		}
		if (officialGePrice != null && officialGePrice > 0)
		{
			return officialGePrice;
		}
		return instantBuyPrice;
	}
}
