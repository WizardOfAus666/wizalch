package com.alchmatepro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link ProfitCalculator} covering all financial formulas,
 * edge cases, bulk quantities, fire rune assumptions, buy limits, and overflow safety.
 */
public class ProfitCalculatorTest
{
	private ProfitCalculator calculator;

	@Before
	public void setUp()
	{
		calculator = new ProfitCalculator();
	}

	@Test
	public void testSpecificationExampleCalculation()
	{
		// Specification example (Sections 08 & 21):
		// Item cost: 12,000 GP, High alchemy value: 15,000 GP, Nature rune: 150 GP, Quantity: 100
		AlchCalculation result = calculator.calculate(4151, 15_000L, 12_000L, 150L, 100);

		assertTrue(result.isValid());
		assertTrue(result.isProfitable());
		assertEquals(2_850L, result.getNetProfitPerAlch());
		assertEquals(0L, result.getLossPerAlch());
		assertEquals(12_150L, result.getTotalCostPerAlch());
		assertEquals(1_200_000L, result.getTotalPurchaseCost());
		assertEquals(15_000L, result.getTotalNatureRuneExpenditure());
		assertEquals(1_215_000L, result.getTotalInvestment());
		assertEquals(1_500_000L, result.getGrossAlchemyReturn());
		assertEquals(285_000L, result.getTotalNetProfit());
		assertEquals(14_850L, result.getBreakEvenPurchasePrice());

		// ROI = (2850 / 12150) * 100 = 23.45679%
		assertEquals(23.45679, result.getRoiPercent(), 0.001);
	}

	@Test
	public void testNegativeProfitCalculation()
	{
		// Item cost: 10,000 GP, High alch: 9,000 GP, Nature rune: 180 GP, Quantity: 50
		AlchCalculation result = calculator.calculate(1001, 9_000L, 10_000L, 180L, 50);

		assertTrue(result.isValid());
		assertFalse(result.isProfitable());
		assertEquals(-1_180L, result.getNetProfitPerAlch());
		assertEquals(1_180L, result.getLossPerAlch());
		assertEquals(-59_000L, result.getTotalNetProfit());
		assertTrue(result.getRoiPercent() < 0.0);
	}

	@Test
	public void testZeroBreakEvenProfitCalculation()
	{
		// Item cost: 5,850 GP, High alch: 6,000 GP, Nature rune: 150 GP -> 0 profit
		AlchCalculation result = calculator.calculate(1002, 6_000L, 5_850L, 150L, 25);

		assertTrue(result.isValid());
		assertFalse(result.isProfitable());
		assertEquals(0L, result.getNetProfitPerAlch());
		assertEquals(0L, result.getLossPerAlch());
		assertEquals(0L, result.getTotalNetProfit());
		assertEquals(0.0, result.getRoiPercent(), 0.0001);
		assertEquals(5_850L, result.getBreakEvenPurchasePrice());
	}

	@Test
	public void testFireRuneCostWhenNoFireStaffEquipped()
	{
		// Without fire staff: 5 fire runes @ 4 GP = 20 GP per cast
		AlchCalculation withStaff = calculator.calculate(1127, 38_400L, 37_800L, 150L, 0L, 10, 70);
		AlchCalculation withoutStaff = calculator.calculate(1127, 38_400L, 37_800L, 150L, 20L, 10, 70);

		assertEquals(450L, withStaff.getNetProfitPerAlch());
		assertEquals(430L, withoutStaff.getNetProfitPerAlch());
		assertEquals(200L, withoutStaff.getTotalFireRuneExpenditure());
		assertEquals(38_230L, withoutStaff.getBreakEvenPurchasePrice());
	}

	@Test
	public void testBuyLimitAndBulkProjections()
	{
		// Magic longbow (ID 859): High alch 1,536 GP, Buy 1,350 GP, Nat 150 GP, 4h Limit 18,000
		AlchCalculation calc = calculator.calculate(859, 1_536L, 1_350L, 150L, 0L, 250, 18_000);

		assertTrue(calc.isValid());
		assertEquals(36L, calc.getNetProfitPerAlch());
		assertEquals(3_600L, calc.getProfitPer100Alchs());
		assertEquals(36_000L, calc.getProfitPer1000Alchs());
		assertEquals(18_000, calc.getGeBuyLimit4h());
		assertEquals(18_000, calc.getMaxBuyLimitNatureRunes());
		assertEquals(18_000L * 1_500L, calc.getMaxBuyLimitInvestment());
		assertEquals(18_000L * 36L, calc.getMaxBuyLimitProfit4h());
	}

	@Test
	public void testMaxAffordablePriceFromTargetAndBudget()
	{
		// High alch 10,000, Nat 150, Fire 0, Target min profit 350 -> Max affordable = 10,000 - 150 - 350 = 9,500
		AlchCalculation calc = calculator.calculate(2001, 10_000L, 9_200L, 150L, 0L, 0L, 100, 125, 350L);
		assertEquals(9_850L, calc.getBreakEvenPurchasePrice());
		assertEquals(9_500L, calc.getMaxAffordablePurchasePrice());

		// Budget: 500,000 GP for 100 items -> 5,000 GP per cast total -> minus 150 GP nat = 4,850 GP max unit price
		long maxFromBudget = calculator.calculateMaxAffordableUnitPriceFromBudget(500_000L, 100, 150L, 0L, 0L);
		assertEquals(4_850L, maxFromBudget);
	}

	@Test
	public void testInvalidInputsHandledGracefully()
	{
		assertFalse(calculator.calculate(1, 10_000L, 9_000L, 150L, 0).isValid());
		assertFalse(calculator.calculate(1, 10_000L, 9_000L, 150L, -5).isValid());
		assertFalse(calculator.calculate(1, 0L, 9_000L, 150L, 10).isValid());
		assertFalse(calculator.calculate(1, 10_000L, 0L, 150L, 10).isValid());
		assertFalse(calculator.calculate(1, 10_000L, -100L, 150L, 10).isValid());
		assertFalse(calculator.calculate(1, 10_000L, 9_000L, -10L, 10).isValid());
	}

	@Test
	public void testIntegerOverflowProtection()
	{
		long clamped = ProfitCalculator.safeMultiply(Long.MAX_VALUE / 2 + 100L, 10L);
		assertEquals(Long.MAX_VALUE, clamped);

		AlchCalculation hugeCalc = calculator.calculate(
			9999,
			2_000_000_000L,
			1_500_000_000L,
			150L,
			1_000_000
		);
		assertTrue(hugeCalc.isValid());
		assertTrue(hugeCalc.getGrossAlchemyReturn() > 0L);
		assertTrue(hugeCalc.getTotalNetProfit() > 0L);
	}
}
