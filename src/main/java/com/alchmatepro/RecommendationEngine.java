package com.alchmatepro;

import com.alchmatepro.PriceValidator.ResolvedPurchasePrice;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.util.QuantityFormatter;

/**
 * Intelligent multi-factor recommendation engine for High Level Alchemy opportunities.
 * Scores items transparently across profit, ROI, trading volume, GE buy limit,
 * price stability, and data freshness while penalising manipulation and stale-data risk.
 */
@Singleton
public class RecommendationEngine
{
	private final PriceValidator priceValidator;

	@Inject
	public RecommendationEngine(PriceValidator priceValidator)
	{
		this.priceValidator = priceValidator;
	}

	/**
	 * Evaluates a single item and returns a transparent {@link AlchRecommendation}.
	 */
	public AlchRecommendation evaluate(
		ItemMetadata metadata,
		MarketPrice marketPrice,
		ResolvedPurchasePrice resolvedPrice,
		AlchCalculation calculation)
	{
		int itemId = metadata != null ? metadata.getId() : 0;
		List<MarketWarning> warnings = priceValidator.evaluateWarnings(metadata, marketPrice, resolvedPrice, calculation);

		if (metadata == null || !metadata.isAlchable()
			|| resolvedPrice == null || !resolvedPrice.isValid()
			|| calculation == null || !calculation.isValid())
		{
			return AlchRecommendation.builder()
				.itemId(itemId)
				.overallScore(0.0)
				.warnings(warnings)
				.categories(EnumSet.noneOf(RecommendationCategory.class))
				.recommended(false)
				.explanation("Not recommended: pricing or item metadata is incomplete.")
				.build();
		}

		long profitPerAlch = calculation.getNetProfitPerAlch();
		double roi = calculation.getRoiPercent();
		int vol1h = marketPrice != null ? marketPrice.getTotalVolume1h() : 0;
		int buyLimit = Math.max(0, metadata.getBuyLimit());
		long purchasePrice = resolvedPrice.getPriceGp();

		// 1. Profit Score (0..100): 1,200+ GP/alch reaches 100
		double profitScore = profitPerAlch <= 0
			? 0.0
			: clamp0to100((Math.min(profitPerAlch, 1500.0) / 1200.0) * 100.0);

		// 2. ROI Score (0..100): 12%+ ROI reaches 100
		double roiScore = roi <= 0
			? 0.0
			: clamp0to100((Math.min(roi, 15.0) / 12.0) * 100.0);

		// 3. Volume Score (0..100): log-scaled 1h volume, 500+ items/hr reaches high score
		double volumeScore = vol1h <= 0
			? 10.0
			: clamp0to100((Math.log10(vol1h + 1) / Math.log10(1500.0)) * 100.0);

		// 4. Buy Limit Score (0..100): 125..1000+ 4h limit
		double limitScore = buyLimit <= 0
			? 20.0
			: clamp0to100((Math.log10(buyLimit + 1) / Math.log10(2000.0)) * 100.0);

		// 5. Stability Score (0..100): compare instantBuyPrice vs 1h avgHighPrice
		double stabilityScore = computeStabilityScore(marketPrice);

		// 6. Freshness Score (0..100): <5m = 100, degrades up to stale threshold
		double freshnessScore = computeFreshnessScore(resolvedPrice);

		// Weighted composite score
		double weightedScore = (profitScore * 0.30)
			+ (roiScore * 0.18)
			+ (volumeScore * 0.22)
			+ (limitScore * 0.10)
			+ (stabilityScore * 0.10)
			+ (freshnessScore * 0.10);

		// Risk penalties
		if (warnings.contains(MarketWarning.STALE_DATA))
		{
			weightedScore *= 0.35;
		}
		if (warnings.contains(MarketWarning.POSSIBLE_PRICE_MANIPULATION))
		{
			weightedScore *= 0.25;
		}
		if (warnings.contains(MarketWarning.PRICE_UNSTABLE))
		{
			weightedScore *= 0.70;
		}
		if (warnings.contains(MarketWarning.LOW_VOLUME))
		{
			weightedScore *= 0.65;
		}
		if (warnings.contains(MarketWarning.HIGH_INVESTMENT))
		{
			weightedScore *= 0.85;
		}
		if (profitPerAlch <= 0)
		{
			weightedScore = 0.0;
		}

		double overallScore = Math.round(clamp0to100(weightedScore) * 10.0) / 10.0;

		boolean confirmedOpportunity = profitPerAlch > 0
			&& !warnings.contains(MarketWarning.INSUFFICIENT_DATA)
			&& !warnings.contains(MarketWarning.STALE_DATA)
			&& !warnings.contains(MarketWarning.POSSIBLE_PRICE_MANIPULATION);

		Set<RecommendationCategory> categories = EnumSet.noneOf(RecommendationCategory.class);
		if (confirmedOpportunity)
		{
			if (overallScore >= 52.0 && vol1h >= 25)
			{
				categories.add(RecommendationCategory.BEST_OVERALL);
			}
			if (profitPerAlch >= 350 && vol1h >= 15)
			{
				categories.add(RecommendationCategory.BEST_PROFIT_PER_ALCH);
			}
			if (roi >= 4.0 && profitPerAlch >= 80 && vol1h >= 20)
			{
				categories.add(RecommendationCategory.BEST_ROI);
			}
			long cycleProfit = calculation.getMaxBuyLimitProfit4h();
			if (buyLimit >= 70 && cycleProfit >= 35_000L && vol1h >= 40)
			{
				categories.add(RecommendationCategory.BEST_BULK_ALCH);
			}
			if (purchasePrice > 0 && purchasePrice <= 5_000L && profitPerAlch >= 50 && vol1h >= 30)
			{
				categories.add(RecommendationCategory.LOWEST_INVESTMENT);
			}
			if (vol1h >= 250 && profitPerAlch >= 30)
			{
				categories.add(RecommendationCategory.HIGHEST_VOLUME);
			}
			if (stabilityScore >= 80.0 && freshnessScore >= 75.0 && vol1h >= 60 && !warnings.contains(MarketWarning.PRICE_UNSTABLE))
			{
				categories.add(RecommendationCategory.LOWEST_RISK);
			}
		}

		String explanation = buildExplanation(
			profitPerAlch,
			roi,
			vol1h,
			buyLimit,
			stabilityScore,
			warnings,
			confirmedOpportunity
		);

		return AlchRecommendation.builder()
			.itemId(itemId)
			.overallScore(overallScore)
			.profitScore(profitScore)
			.roiScore(roiScore)
			.volumeScore(volumeScore)
			.limitScore(limitScore)
			.stabilityScore(stabilityScore)
			.freshnessScore(freshnessScore)
			.categories(categories)
			.warnings(warnings)
			.recommended(confirmedOpportunity && overallScore >= 40.0)
			.explanation(explanation)
			.build();
	}

	/**
	 * Filters and ranks items matching a specific {@link RecommendationCategory}.
	 */
	public List<AlchMateItem> getRecommendationsByCategory(List<AlchMateItem> items, RecommendationCategory category)
	{
		if (items == null || items.isEmpty() || category == null)
		{
			return List.of();
		}

		Comparator<AlchMateItem> comparator;
		switch (category)
		{
			case BEST_PROFIT_PER_ALCH:
				comparator = Comparator.comparingLong(AlchMateItem::getNetProfitPerAlch).reversed();
				break;
			case BEST_ROI:
				comparator = Comparator.comparingDouble(AlchMateItem::getRoiPercent).reversed();
				break;
			case BEST_BULK_ALCH:
				comparator = Comparator.comparingLong((AlchMateItem item) ->
					Math.min((long) item.getBuyLimit(), Math.max(1L, (long) item.getVolume1h() * 4L)) * item.getNetProfitPerAlch()
				).reversed();
				break;
			case LOWEST_INVESTMENT:
				comparator = Comparator.comparingLong(AlchMateItem::getEstimatedPurchasePrice)
					.thenComparing(Comparator.comparingLong(AlchMateItem::getNetProfitPerAlch).reversed());
				break;
			case HIGHEST_VOLUME:
				comparator = Comparator.comparingInt(AlchMateItem::getVolume1h).reversed();
				break;
			case LOWEST_RISK:
				comparator = Comparator.comparingDouble((AlchMateItem item) ->
					item.getRecommendation() != null
						? (item.getRecommendation().getStabilityScore() + item.getRecommendation().getFreshnessScore() + item.getRecommendation().getVolumeScore())
						: 0.0
				).reversed();
				break;
			case BEST_OVERALL:
			default:
				comparator = Comparator.comparingDouble((AlchMateItem item) ->
					item.getRecommendation() != null ? item.getRecommendation().getOverallScore() : 0.0
				).reversed();
				break;
		}

		List<AlchMateItem> strictMatches = items.stream()
			.filter(AlchMateItem::isProfitable)
			.filter(item -> !item.isStale())
			.filter(item -> item.getRecommendation() != null && item.getRecommendation().matchesCategory(category))
			.sorted(comparator)
			.collect(Collectors.toList());

		if (!strictMatches.isEmpty())
		{
			return strictMatches;
		}

		// Fallback to profitable non-stale items sorted by the category's primary metric
		return items.stream()
			.filter(AlchMateItem::isProfitable)
			.filter(item -> !item.isStale())
			.filter(item -> !item.getWarnings().contains(MarketWarning.POSSIBLE_PRICE_MANIPULATION))
			.sorted(comparator)
			.collect(Collectors.toList());
	}

	/**
	 * Finds the single best bulk opportunity factoring in both 4h buy limit and realistic 4h trade volume.
	 */
	public Optional<AlchMateItem> findBestBulkOpportunity(List<AlchMateItem> items)
	{
		if (items == null || items.isEmpty())
		{
			return Optional.empty();
		}
		return items.stream()
			.filter(AlchMateItem::isProfitable)
			.filter(item -> !item.isStale())
			.filter(item -> item.getBuyLimit() > 0 && item.getVolume1h() >= 15)
			.filter(item -> !item.getWarnings().contains(MarketWarning.POSSIBLE_PRICE_MANIPULATION))
			.max(Comparator.comparingLong(item -> {
				long realistic4hCap = Math.min((long) item.getBuyLimit(), Math.max(10L, (long) item.getVolume1h() * 4L));
				return realistic4hCap * item.getNetProfitPerAlch();
			}));
	}

	private double computeStabilityScore(MarketPrice marketPrice)
	{
		if (marketPrice == null)
		{
			return 40.0;
		}
		Integer instantHigh = marketPrice.getInstantBuyPrice();
		Integer avg1hHigh = marketPrice.getAvgHighPrice1h();
		if (instantHigh != null && instantHigh > 0 && avg1hHigh != null && avg1hHigh > 0)
		{
			double diffPct = Math.abs((double) (instantHigh - avg1hHigh) / (double) avg1hHigh) * 100.0;
			return clamp0to100(100.0 - (diffPct * 4.5));
		}
		return 65.0;
	}

	private double computeFreshnessScore(ResolvedPurchasePrice resolvedPrice)
	{
		if (resolvedPrice == null || !resolvedPrice.isValid() || resolvedPrice.getAgeSeconds() == Long.MAX_VALUE)
		{
			return 0.0;
		}
		if (resolvedPrice.isStale())
		{
			return 15.0;
		}
		long ageMin = resolvedPrice.getAgeSeconds() / 60L;
		if (ageMin <= 5)
		{
			return 100.0;
		}
		if (ageMin <= 15)
		{
			return 90.0;
		}
		if (ageMin <= 30)
		{
			return 80.0;
		}
		if (ageMin <= 60)
		{
			return 65.0;
		}
		return 50.0;
	}

	private String buildExplanation(
		long profitPerAlch,
		double roi,
		int vol1h,
		int buyLimit,
		double stabilityScore,
		List<MarketWarning> warnings,
		boolean confirmedOpportunity)
	{
		if (profitPerAlch <= 0)
		{
			return String.format(
				"Unprofitable at current estimate (%s GP/alch, %.1f%% ROI).",
				QuantityFormatter.formatNumber(profitPerAlch),
				roi
			);
		}

		if (!confirmedOpportunity)
		{
			if (warnings.contains(MarketWarning.STALE_DATA))
			{
				return "Caution: Potential margin exists, but the last recorded trade is stale. Verify in-game before buying.";
			}
			if (warnings.contains(MarketWarning.POSSIBLE_PRICE_MANIPULATION))
			{
				return "Caution: Price spread or recent spike suggests possible manipulation or illiquid market.";
			}
		}

		StringBuilder sb = new StringBuilder("Recommended because it offers ");
		sb.append("+").append(QuantityFormatter.formatNumber(profitPerAlch)).append(" GP/alch ")
			.append(String.format("(%.1f%% ROI)", roi));

		if (vol1h >= 150)
		{
			sb.append(", high 1h volume (").append(QuantityFormatter.formatNumber(vol1h)).append(" traded)");
		}
		else if (vol1h >= 30)
		{
			sb.append(", healthy trading volume (").append(QuantityFormatter.formatNumber(vol1h)).append("/hr)");
		}
		else
		{
			sb.append(", modest volume (").append(QuantityFormatter.formatNumber(vol1h)).append("/hr)");
		}

		if (stabilityScore >= 80.0)
		{
			sb.append(" and stable recent pricing");
		}
		if (buyLimit >= 500)
		{
			sb.append(" with a generous ").append(QuantityFormatter.formatNumber(buyLimit)).append(" GE buy limit");
		}
		sb.append(".");
		return sb.toString();
	}

	private static double clamp0to100(double value)
	{
		return Math.max(0.0, Math.min(100.0, value));
	}
}
