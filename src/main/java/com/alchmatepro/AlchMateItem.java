package com.alchmatepro;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Enriched item domain model combining metadata, live market pricing, calculation results,
 * freshness metadata, and smart recommendation telemetry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlchMateItem
{
	private ItemMetadata metadata;
	private MarketPrice marketPrice;
	private AlchCalculation calculation;
	private AlchRecommendation recommendation;

	private long estimatedPurchasePrice;
	private PriceSourceType priceSourceType;
	private boolean stale;
	private long priceAgeSeconds;

	public int getId()
	{
		return metadata != null ? metadata.getId() : 0;
	}

	public String getName()
	{
		return metadata != null && metadata.getName() != null ? metadata.getName() : "Unknown Item";
	}

	public boolean isMembers()
	{
		return metadata != null && metadata.isMembers();
	}

	public int getHighAlchValue()
	{
		return metadata != null ? metadata.getHighAlchValue() : 0;
	}

	public int getLowAlchValue()
	{
		return metadata != null ? metadata.getLowAlchValue() : 0;
	}

	public int getBuyLimit()
	{
		return metadata != null ? metadata.getBuyLimit() : 0;
	}

	public long getNetProfitPerAlch()
	{
		return calculation != null && calculation.isValid() ? calculation.getNetProfitPerAlch() : 0L;
	}

	public double getRoiPercent()
	{
		return calculation != null && calculation.isValid() ? calculation.getRoiPercent() : 0.0;
	}

	public int getVolume1h()
	{
		return marketPrice != null ? marketPrice.getTotalVolume1h() : 0;
	}

	public int getVolume5m()
	{
		return marketPrice != null ? marketPrice.getTotalVolume5m() : 0;
	}

	public boolean hasValidPrice()
	{
		return calculation != null && calculation.isValid() && estimatedPurchasePrice > 0;
	}

	public boolean isProfitable()
	{
		return hasValidPrice() && calculation.getNetProfitPerAlch() > 0;
	}

	public List<MarketWarning> getWarnings()
	{
		return recommendation != null && recommendation.getWarnings() != null
			? recommendation.getWarnings()
			: Collections.emptyList();
	}

	public String getFreshnessLabel()
	{
		if (!hasValidPrice() || priceAgeSeconds == Long.MAX_VALUE)
		{
			return "Unknown age";
		}
		if (priceAgeSeconds < 60)
		{
			return priceAgeSeconds + "s ago";
		}
		long minutes = priceAgeSeconds / 60;
		if (minutes < 60)
		{
			return minutes + "m ago";
		}
		long hours = minutes / 60;
		if (hours < 48)
		{
			return hours + "h ago";
		}
		return (hours / 24) + "d ago";
	}
}
