package com.alchmatepro;

import javax.inject.Singleton;

/**
 * Dedicated financial calculation engine for Old School RuneScape High Level Alchemy.
 * Completely independent of UI components and uses 64-bit integer GP arithmetic
 * with overflow protection.
 *
 * Note: Grand Exchange tax is intentionally NOT applied because GE tax is paid by sellers,
 * and High Level Alchemy converts items directly into coins rather than selling on the GE.
 */
@Singleton
public class ProfitCalculator
{
	/**
	 * Calculates High Level Alchemy profit and return metrics using standard parameters
	 * (fire staff equipped = 0 fire rune cost, 0 additional cost, 0 buy limit).
	 */
	public AlchCalculation calculate(
		int itemId,
		long highAlchValue,
		long itemPurchaseCost,
		long natureRuneCost,
		int quantity)
	{
		return calculate(itemId, highAlchValue, itemPurchaseCost, natureRuneCost, 0L, 0L, quantity, 0, 0L);
	}

	/**
	 * Calculates High Level Alchemy profit and return metrics with fire rune cost and GE buy limit.
	 */
	public AlchCalculation calculate(
		int itemId,
		long highAlchValue,
		long itemPurchaseCost,
		long natureRuneCost,
		long fireRuneCostPerCast,
		int quantity,
		int geBuyLimit4h)
	{
		return calculate(
			itemId,
			highAlchValue,
			itemPurchaseCost,
			natureRuneCost,
			fireRuneCostPerCast,
			0L,
			quantity,
			geBuyLimit4h,
			0L
		);
	}

	/**
	 * Full High Level Alchemy calculation engine.
	 *
	 * @param itemId                  OSRS item ID
	 * @param highAlchValue           Coins generated per High Level Alchemy cast
	 * @param itemPurchaseCost        Estimated Grand Exchange unit purchase cost in GP
	 * @param natureRuneCost          Cost of 1 Nature rune in GP
	 * @param fireRuneCostPerCast     Cost of Fire runes per cast in GP (0 if fire staff equipped)
	 * @param otherCostPerCast        Any additional applicable cost per cast in GP
	 * @param quantity                Number of items to purchase and alch
	 * @param geBuyLimit4h            4-hour Grand Exchange buy limit (0 if unknown)
	 * @param targetMinProfitPerAlch  Target minimum profit per alch for max-affordable-price calculation
	 * @return Immutable {@link AlchCalculation} result
	 */
	public AlchCalculation calculate(
		int itemId,
		long highAlchValue,
		long itemPurchaseCost,
		long natureRuneCost,
		long fireRuneCostPerCast,
		long otherCostPerCast,
		int quantity,
		int geBuyLimit4h,
		long targetMinProfitPerAlch)
	{
		if (quantity <= 0)
		{
			return invalidResult(itemId, quantity, "Quantity must be greater than zero.");
		}
		if (highAlchValue <= 0)
		{
			return invalidResult(itemId, quantity, "High alchemy value must be positive.");
		}
		if (itemPurchaseCost <= 0)
		{
			return invalidResult(itemId, quantity, "Item purchase price is missing or invalid.");
		}
		if (natureRuneCost < 0 || fireRuneCostPerCast < 0 || otherCostPerCast < 0)
		{
			return invalidResult(itemId, quantity, "Rune and casting costs cannot be negative.");
		}

		long safeRuneAndOtherCost = safeAdd(safeAdd(natureRuneCost, fireRuneCostPerCast), otherCostPerCast);
		long totalCostPerAlch = safeAdd(itemPurchaseCost, safeRuneAndOtherCost);
		if (totalCostPerAlch <= 0)
		{
			return invalidResult(itemId, quantity, "Total cost per alch must be positive.");
		}

		long netProfitPerAlch = safeSubtract(highAlchValue, totalCostPerAlch);

		long grossAlchemyReturn = safeMultiply(highAlchValue, quantity);
		long totalPurchaseCost = safeMultiply(itemPurchaseCost, quantity);
		long totalNatureRuneExp = safeMultiply(natureRuneCost, quantity);
		long totalFireRuneExp = safeMultiply(fireRuneCostPerCast, quantity);
		long totalOtherExp = safeMultiply(otherCostPerCast, quantity);
		long totalInvestment = safeMultiply(totalCostPerAlch, quantity);
		long totalNetProfit = safeMultiply(netProfitPerAlch, quantity);

		double roiPercent = ((double) netProfitPerAlch / (double) totalCostPerAlch) * 100.0;
		double totalReturnRatioPercent = ((double) grossAlchemyReturn / (double) totalInvestment) * 100.0;

		long breakEvenPurchasePrice = Math.max(0L, safeSubtract(highAlchValue, safeRuneAndOtherCost));
		long maxAffordablePurchasePrice = Math.max(
			0L,
			safeSubtract(breakEvenPurchasePrice, Math.max(0L, targetMinProfitPerAlch))
		);

		int safeBuyLimit = Math.max(0, geBuyLimit4h);
		long maxBuyLimitInvestment = safeBuyLimit > 0 ? safeMultiply(totalCostPerAlch, safeBuyLimit) : 0L;
		long maxBuyLimitProfit4h = safeBuyLimit > 0 ? safeMultiply(netProfitPerAlch, safeBuyLimit) : 0L;

		long profitPer100 = safeMultiply(netProfitPerAlch, 100L);
		long profitPer1000 = safeMultiply(netProfitPerAlch, 1000L);

		return AlchCalculation.builder()
			.itemId(itemId)
			.quantity(quantity)
			.highAlchValuePerItem(highAlchValue)
			.purchaseCostPerItem(itemPurchaseCost)
			.natureRuneCostPerCast(natureRuneCost)
			.fireRuneCostPerCast(fireRuneCostPerCast)
			.otherCostPerCast(otherCostPerCast)
			.totalCostPerAlch(totalCostPerAlch)
			.netProfitPerAlch(netProfitPerAlch)
			.grossAlchemyReturn(grossAlchemyReturn)
			.totalPurchaseCost(totalPurchaseCost)
			.totalNatureRuneExpenditure(totalNatureRuneExp)
			.totalFireRuneExpenditure(totalFireRuneExp)
			.totalOtherExpenditure(totalOtherExp)
			.totalInvestment(totalInvestment)
			.totalNetProfit(totalNetProfit)
			.roiPercent(roiPercent)
			.totalReturnRatioPercent(totalReturnRatioPercent)
			.breakEvenPurchasePrice(breakEvenPurchasePrice)
			.maxAffordablePurchasePrice(maxAffordablePurchasePrice)
			.geBuyLimit4h(safeBuyLimit)
			.maxBuyLimitInvestment(maxBuyLimitInvestment)
			.maxBuyLimitProfit4h(maxBuyLimitProfit4h)
			.maxBuyLimitNatureRunes(safeBuyLimit)
			.profitPer100Alchs(profitPer100)
			.profitPer1000Alchs(profitPer1000)
			.valid(true)
			.validationMessage("OK")
			.build();
	}

	/**
	 * Calculates the maximum unit purchase price a player can afford given a total capital budget
	 * and desired quantity, factoring in rune expenditure per cast.
	 */
	public long calculateMaxAffordableUnitPriceFromBudget(
		long totalBudgetGp,
		int quantity,
		long natureRuneCost,
		long fireRuneCostPerCast,
		long otherCostPerCast)
	{
		if (totalBudgetGp <= 0 || quantity <= 0)
		{
			return 0L;
		}
		long maxCostPerCast = totalBudgetGp / quantity;
		long runeOverhead = safeAdd(safeAdd(Math.max(0L, natureRuneCost), Math.max(0L, fireRuneCostPerCast)), Math.max(0L, otherCostPerCast));
		return Math.max(0L, maxCostPerCast - runeOverhead);
	}

	private AlchCalculation invalidResult(int itemId, int quantity, String reason)
	{
		return AlchCalculation.builder()
			.itemId(itemId)
			.quantity(Math.max(0, quantity))
			.valid(false)
			.validationMessage(reason)
			.build();
	}

	public static long safeAdd(long a, long b)
	{
		try
		{
			return Math.addExact(a, b);
		}
		catch (ArithmeticException ex)
		{
			return (a > 0 && b > 0) ? Long.MAX_VALUE : Long.MIN_VALUE;
		}
	}

	public static long safeSubtract(long a, long b)
	{
		try
		{
			return Math.subtractExact(a, b);
		}
		catch (ArithmeticException ex)
		{
			return (a > 0 && b < 0) ? Long.MAX_VALUE : Long.MIN_VALUE;
		}
	}

	public static long safeMultiply(long a, long b)
	{
		try
		{
			return Math.multiplyExact(a, b);
		}
		catch (ArithmeticException ex)
		{
			boolean positive = (a >= 0 && b >= 0) || (a < 0 && b < 0);
			return positive ? Long.MAX_VALUE : Long.MIN_VALUE;
		}
	}
}
