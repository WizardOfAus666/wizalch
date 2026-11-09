package com.alchmatepro;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Transparent multi-factor recommendation score, matched categories, warnings, and human-readable reasoning.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlchRecommendation
{
	private int itemId;
	private double overallScore;
	private double profitScore;
	private double roiScore;
	private double volumeScore;
	private double limitScore;
	private double stabilityScore;
	private double freshnessScore;

	@Builder.Default
	private Set<RecommendationCategory> categories = Collections.emptySet();

	@Builder.Default
	private List<MarketWarning> warnings = Collections.emptyList();

	private String explanation;
	private boolean recommended;

	public boolean hasWarning(MarketWarning warning)
	{
		return warnings != null && warnings.contains(warning);
	}

	public boolean matchesCategory(RecommendationCategory category)
	{
		return categories != null && categories.contains(category);
	}
}
