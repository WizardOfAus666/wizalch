package com.alchmatepro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Singleton;
import lombok.Value;

/**
 * Validates Grand Exchange market prices, resolves purchase-side price estimates according
 * to the configured {@link PriceCalculationMethod}, checks data freshness, and flags
 * market safety risks such as low volume, instability, and potential price manipulation.
 */
@Singleton
public class PriceValidator
{
	@Value
	public static class ResolvedPurchasePrice
	{
		long priceGp;
		PriceSourceType sourceType;
		long ageSeconds;
		boolean stale;
		boolean valid;
	}

	/**
	 * Resolves the appropriate purchase-side price for an item based on the selected method
	 * and evaluates freshness against {@code maxDataAgeMinutes}.
	 */
	public ResolvedPurchasePrice resolvePurchasePrice(
		MarketPrice marketPrice,
		PriceCalculationMethod method,
		int maxDataAgeMinutes,
		long nowEpochSec)
	{
		if (marketPrice == null)
		{
			return new ResolvedPurchasePrice(0L, PriceSourceType.UNAVAILABLE, Long.MAX_VALUE, true, false);
		}

		PriceCalculationMethod effectiveMethod = method != null ? method : PriceCalculationMethod.INSTANT_BUY_HIGH;
		long resolvedPrice = 0L;
		PriceSourceType sourceType = PriceSourceType.UNAVAILABLE;

		switch (effectiveMethod)
		{
			case INSTANT_BUY_HIGH:
				if (isPositive(marketPrice.getInstantBuyPrice()))
				{
					resolvedPrice = marketPrice.getInstantBuyPrice();
					sourceType = PriceSourceType.INSTANT_BUY_PRICE;
				}
				else if (isPositive(marketPrice.getAvgHighPrice5m()))
				{
					resolvedPrice = marketPrice.getAvgHighPrice5m();
					sourceType = PriceSourceType.RECENT_5M_AVERAGE;
				}
				else if (isPositive(marketPrice.getAvgHighPrice1h()))
				{
					resolvedPrice = marketPrice.getAvgHighPrice1h();
					sourceType = PriceSourceType.STABLE_1H_AVERAGE;
				}
				else if (isPositive(marketPrice.getInstantSellPrice()))
				{
					resolvedPrice = marketPrice.getInstantSellPrice();
					sourceType = PriceSourceType.INSTANT_SELL_PRICE;
				}
				break;

			case STABLE_1H_HIGH:
				if (isPositive(marketPrice.getAvgHighPrice1h()))
				{
					resolvedPrice = marketPrice.getAvgHighPrice1h();
					sourceType = PriceSourceType.STABLE_1H_AVERAGE;
				}
				else if (isPositive(marketPrice.getAvgHighPrice5m()))
				{
					resolvedPrice = marketPrice.getAvgHighPrice5m();
					sourceType = PriceSourceType.RECENT_5M_AVERAGE;
				}
				else if (isPositive(marketPrice.getInstantBuyPrice()))
				{
					resolvedPrice = marketPrice.getInstantBuyPrice();
					sourceType = PriceSourceType.INSTANT_BUY_PRICE;
				}
				break;

			case ROLLING_5M_HIGH:
				if (isPositive(marketPrice.getAvgHighPrice5m()))
				{
					resolvedPrice = marketPrice.getAvgHighPrice5m();
					sourceType = PriceSourceType.RECENT_5M_AVERAGE;
				}
				else if (isPositive(marketPrice.getInstantBuyPrice()))
				{
					resolvedPrice = marketPrice.getInstantBuyPrice();
					sourceType = PriceSourceType.INSTANT_BUY_PRICE;
				}
				else if (isPositive(marketPrice.getAvgHighPrice1h()))
				{
					resolvedPrice = marketPrice.getAvgHighPrice1h();
					sourceType = PriceSourceType.STABLE_1H_AVERAGE;
				}
				break;

			case CONSERVATIVE_HIGH:
				long latestHigh = isPositive(marketPrice.getInstantBuyPrice()) ? marketPrice.getInstantBuyPrice() : 0L;
				long avg1hHigh = isPositive(marketPrice.getAvgHighPrice1h()) ? marketPrice.getAvgHighPrice1h() : 0L;
				long avg5mHigh = isPositive(marketPrice.getAvgHighPrice5m()) ? marketPrice.getAvgHighPrice5m() : 0L;
				resolvedPrice = Math.max(latestHigh, Math.max(avg1hHigh, avg5mHigh));
				if (resolvedPrice > 0)
				{
					sourceType = (latestHigh > 0 && avg1hHigh > 0)
						? PriceSourceType.CONSERVATIVE_ESTIMATE
						: (latestHigh > 0 ? PriceSourceType.INSTANT_BUY_PRICE : PriceSourceType.STABLE_1H_AVERAGE);
				}
				break;

			case PATIENT_OFFER_LOW:
				if (isPositive(marketPrice.getInstantSellPrice()))
				{
					resolvedPrice = marketPrice.getInstantSellPrice();
					sourceType = PriceSourceType.INSTANT_SELL_PRICE;
				}
				else if (isPositive(marketPrice.getAvgLowPrice1h()))
				{
					resolvedPrice = marketPrice.getAvgLowPrice1h();
					sourceType = PriceSourceType.STABLE_1H_AVERAGE;
				}
				else if (isPositive(marketPrice.getInstantBuyPrice()))
				{
					resolvedPrice = marketPrice.getInstantBuyPrice();
					sourceType = PriceSourceType.INSTANT_BUY_PRICE;
				}
				break;
		}

		if (resolvedPrice <= 0 && isPositive(marketPrice.getOfficialGePrice()))
		{
			resolvedPrice = marketPrice.getOfficialGePrice();
			sourceType = PriceSourceType.OFFICIAL_GE_GUIDE;
		}

		if (resolvedPrice <= 0)
		{
			return new ResolvedPurchasePrice(0L, PriceSourceType.UNAVAILABLE, Long.MAX_VALUE, true, false);
		}

		long tradeTime = marketPrice.getInstantBuyTimeEpochSec() != null && marketPrice.getInstantBuyTimeEpochSec() > 0
			? marketPrice.getInstantBuyTimeEpochSec()
			: marketPrice.getLatestTradeEpochSec();

		long ageSeconds = tradeTime > 0 && nowEpochSec >= tradeTime
			? (nowEpochSec - tradeTime)
			: (tradeTime > 0 ? 0L : Long.MAX_VALUE);

		long maxAgeSeconds = Math.max(60L, (long) maxDataAgeMinutes * 60L);
		boolean stale = (ageSeconds == Long.MAX_VALUE) || (ageSeconds > maxAgeSeconds);

		return new ResolvedPurchasePrice(resolvedPrice, sourceType, ageSeconds, stale, true);
	}

	/**
	 * Evaluates market safety warnings for an item given its metadata, pricing, and calculation.
	 */
	public List<MarketWarning> evaluateWarnings(
		ItemMetadata metadata,
		MarketPrice marketPrice,
		ResolvedPurchasePrice resolvedPrice,
		AlchCalculation calculation)
	{
		List<MarketWarning> warnings = new ArrayList<>();

		if (metadata == null || !metadata.isAlchable() || marketPrice == null
			|| resolvedPrice == null || !resolvedPrice.isValid()
			|| calculation == null || !calculation.isValid())
		{
			warnings.add(MarketWarning.INSUFFICIENT_DATA);
			return Collections.unmodifiableList(warnings);
		}

		if (resolvedPrice.isStale())
		{
			warnings.add(MarketWarning.STALE_DATA);
		}

		int vol1h = marketPrice.getTotalVolume1h();
		int vol5m = marketPrice.getTotalVolume5m();
		if (vol1h < 25 && vol5m < 5)
		{
			warnings.add(MarketWarning.LOW_VOLUME);
		}

		Integer instantHigh = marketPrice.getInstantBuyPrice();
		Integer avg1hHigh = marketPrice.getAvgHighPrice1h();
		if (isPositive(instantHigh) && isPositive(avg1hHigh))
		{
			double diffRatio = Math.abs((double) (instantHigh - avg1hHigh) / (double) avg1hHigh);
			if (diffRatio > 0.12)
			{
				warnings.add(MarketWarning.PRICE_UNSTABLE);
			}
			if (diffRatio > 0.28)
			{
				warnings.add(MarketWarning.POSSIBLE_PRICE_MANIPULATION);
			}
		}

		Double spreadPct = marketPrice.getSpreadPercent();
		if (spreadPct != null && Math.abs(spreadPct) > 30.0 && metadata.getHighAlchValue() >= 1000)
		{
			if (!warnings.contains(MarketWarning.POSSIBLE_PRICE_MANIPULATION))
			{
				warnings.add(MarketWarning.POSSIBLE_PRICE_MANIPULATION);
			}
		}

		if (calculation.getNetProfitPerAlch() > 5000 && vol1h < 15)
		{
			if (!warnings.contains(MarketWarning.POSSIBLE_PRICE_MANIPULATION))
			{
				warnings.add(MarketWarning.POSSIBLE_PRICE_MANIPULATION);
			}
		}

		if (resolvedPrice.getPriceGp() >= 250_000L
			|| (resolvedPrice.getPriceGp() >= 100_000L && calculation.getRoiPercent() < 1.5))
		{
			warnings.add(MarketWarning.HIGH_INVESTMENT);
		}

		return Collections.unmodifiableList(warnings);
	}

	private static boolean isPositive(Integer value)
	{
		return value != null && value > 0;
	}
}
