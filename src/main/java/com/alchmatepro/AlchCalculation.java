package com.alchmatepro;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Immutable financial calculation output produced by ProfitCalculator.
 * Uses 64-bit integer GP arithmetic to prevent 32-bit integer overflow on bulk quantities.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlchCalculation
{
	private int itemId;
	private int quantity;

	private long highAlchValuePerItem;
	private long purchaseCostPerItem;
	private long natureRuneCostPerCast;
	private long fireRuneCostPerCast;
	private long otherCostPerCast;

	private long totalCostPerAlch;
	private long netProfitPerAlch;

	private long grossAlchemyReturn;
	private long totalPurchaseCost;
	private long totalNatureRuneExpenditure;
	private long totalFireRuneExpenditure;
	private long totalOtherExpenditure;
	private long totalInvestment;
	private long totalNetProfit;

	private double roiPercent;
	private double totalReturnRatioPercent;

	private long breakEvenPurchasePrice;
	private long maxAffordablePurchasePrice;

	private int geBuyLimit4h;
	private long maxBuyLimitInvestment;
	private long maxBuyLimitProfit4h;
	private int maxBuyLimitNatureRunes;

	private long profitPer100Alchs;
	private long profitPer1000Alchs;

	private boolean valid;
	private String validationMessage;

	public boolean isProfitable()
	{
		return valid && netProfitPerAlch > 0;
	}

	public long getLossPerAlch()
	{
		if (!valid || netProfitPerAlch >= 0)
		{
			return 0L;
		}
		return Math.abs(netProfitPerAlch);
	}
}
