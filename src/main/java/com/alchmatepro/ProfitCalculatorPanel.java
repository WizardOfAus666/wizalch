package com.alchmatepro;

import com.alchmatepro.AlchUiComponents.MetricCard;
import com.alchmatepro.AlchUiComponents.ResponsiveGridLayout;
import com.alchmatepro.AlchUiComponents.VerticalStackLayout;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import net.runelite.client.util.QuantityFormatter;

/**
 * Dedicated Profit & Advanced Quantity Calculator panel (Sections 07 & 08).
 * Supports both live-item loading and custom scenario modelling with quick quantity
 * selectors (1, 10, 25, 50, 100, 250, 500, 1,000, Custom) and budget affordability analysis.
 */
public class ProfitCalculatorPanel extends JPanel
{
	private static final int[] QUICK_QUANTITIES = {1, 10, 25, 50, 100, 250, 500, 1000};

	private final MarketDataService marketDataService;
	private final ProfitCalculator profitCalculator;
	private final AlchMateProConfig config;

	private AlchMateItem selectedItem;

	private final JTextField itemLookupField = new JTextField();
	private final JComboBox<String> quickMatchedItemsCombo = new JComboBox<>();

	private final JSpinner purchaseCostSpinner = new JSpinner(new SpinnerNumberModel(12000, 1, 2147483647, 100));
	private final JSpinner highAlchValueSpinner = new JSpinner(new SpinnerNumberModel(15000, 1, 2147483647, 100));
	private final JSpinner natureRuneCostSpinner = new JSpinner(new SpinnerNumberModel(180, 0, 100000, 5));
	private final JCheckBox fireStaffCheckBox = new JCheckBox("Fire Staff Equipped (0 Fire Rune Cost)", true);
	private final JSpinner fireRuneCostSpinner = new JSpinner(new SpinnerNumberModel(20, 0, 100000, 5));
	private final JSpinner quantitySpinner = new JSpinner(new SpinnerNumberModel(100, 1, 10000000, 25));
	private final JSpinner targetMinProfitSpinner = new JSpinner(new SpinnerNumberModel(250, 0, 10000000, 50));
	private final JSpinner budgetCapitalSpinner = new JSpinner(new SpinnerNumberModel(1000000, 1000, 2147483647, 100000));

	// Hero summary cards
	private final MetricCard heroTotalProfitCard = new MetricCard("TOTAL NET PROFIT", "+282,000 GP", "Batch net return", AlchTheme.SUCCESS_GREEN);
	private final MetricCard heroUnitProfitCard = new MetricCard("PROFIT / ALCH", "+2,820 GP", "Per cast margin", AlchTheme.SUCCESS_GREEN);
	private final MetricCard heroRoiCard = new MetricCard("BATCH ROI %", "+23.2%", "Return on capital", AlchTheme.ACCENT_GOLD);

	// Output display labels
	private final JLabel outSelectedNameLabel = new JLabel("Custom Scenario");
	private final JLabel outItemQuantityLabel = new JLabel("100");
	private final JLabel outPurchaseCostLabel = new JLabel("1,200,000 GP");
	private final JLabel outNatureRunesRequiredLabel = new JLabel("100 (18,000 GP)");
	private final JLabel outFireRuneExpLabel = new JLabel("0 GP (Fire Staff)");
	private final JLabel outTotalInvestmentLabel = new JLabel("1,218,000 GP");
	private final JLabel outGrossAlchReturnLabel = new JLabel("1,500,000 GP");
	private final JLabel outNetProfitPerAlchLabel = new JLabel("+2,820 GP");
	private final JLabel outTotalProfitOrLossLabel = new JLabel("+282,000 GP");
	private final JLabel outHourlyProfitLabel = new JLabel("+3.38M GP/hr");
	private final JLabel outRoiPercentLabel = new JLabel("+23.2%");
	private final JLabel outTotalReturnRatioLabel = new JLabel("123.2%");
	private final JLabel outBreakEvenPriceLabel = new JLabel("14,820 GP");
	private final JLabel outMaxAffordableTargetLabel = new JLabel("14,570 GP");
	private final JLabel outMaxAffordableBudgetLabel = new JLabel("9,820 GP");

	private boolean suppressComboEvents = false;

	public ProfitCalculatorPanel(
		MarketDataService marketDataService,
		ProfitCalculator profitCalculator,
		AlchMateProConfig config)
	{
		super(new VerticalStackLayout(AlchTheme.SPACE_SM));
		this.marketDataService = marketDataService;
		this.profitCalculator = profitCalculator;
		this.config = config;

		setBackground(AlchTheme.BG_MAIN);
		setBorder(BorderFactory.createEmptyBorder(
			AlchTheme.SPACE_SM, AlchTheme.SPACE_SM, AlchTheme.SPACE_MD, AlchTheme.SPACE_SM
		));

		add(buildItemSelectorCard());
		add(buildInputsCard());
		add(buildQuickQuantitiesCard());
		add(buildHeroResultsStrip());
		add(buildResultsBreakdownCard());

		syncDefaultsFromConfig();
		recalculate();
	}

	private JPanel buildItemSelectorCard()
	{
		JPanel card = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
		card.setBackground(AlchTheme.BG_CARD);
		card.setBorder(AlchTheme.createCardBorder());

		JLabel title = AlchTheme.createSectionHeading("LOAD ITEM FROM LIVE MARKET");
		card.add(title);

		AlchTheme.styleTextField(itemLookupField);
		itemLookupField.setToolTipText("Type an item name and click Find");
		JButton findBtn = AlchTheme.createSecondaryButton("Find");
		findBtn.addActionListener(e -> searchMatchingItems());
		itemLookupField.addActionListener(e -> searchMatchingItems());

		JPanel searchGroup = AlchTheme.createSplitRow(itemLookupField, findBtn);
		card.add(AlchTheme.createFormControlRow("Search Item", searchGroup));

		AlchTheme.styleComboBox(quickMatchedItemsCombo);
		quickMatchedItemsCombo.addActionListener(e -> {
			if (suppressComboEvents)
			{
				return;
			}
			int idx = quickMatchedItemsCombo.getSelectedIndex();
			String selectedText = (String) quickMatchedItemsCombo.getSelectedItem();
			if (idx >= 0 && selectedText != null && selectedText.contains("[#"))
			{
				try
				{
					int start = selectedText.lastIndexOf("[#") + 2;
					int end = selectedText.lastIndexOf("]");
					int itemId = Integer.parseInt(selectedText.substring(start, end));
					marketDataService.getItemById(itemId).ifPresent(this::loadItem);
				}
				catch (Exception ignored)
				{
				}
			}
		});
		card.add(AlchTheme.createFormControlRow("Matched Items", quickMatchedItemsCombo));
		return card;
	}

	private JPanel buildInputsCard()
	{
		JPanel card = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
		card.setBackground(AlchTheme.BG_CARD);
		card.setBorder(AlchTheme.createCardBorder());

		JLabel title = AlchTheme.createSectionHeading("CALCULATION PARAMETERS");
		card.add(title);

		AlchTheme.styleSpinner(purchaseCostSpinner);
		AlchTheme.styleSpinner(highAlchValueSpinner);
		AlchTheme.styleSpinner(natureRuneCostSpinner);
		AlchTheme.styleSpinner(fireRuneCostSpinner);
		AlchTheme.styleSpinner(quantitySpinner);
		AlchTheme.styleSpinner(targetMinProfitSpinner);
		AlchTheme.styleSpinner(budgetCapitalSpinner);

		purchaseCostSpinner.addChangeListener(e -> recalculate());
		highAlchValueSpinner.addChangeListener(e -> recalculate());
		natureRuneCostSpinner.addChangeListener(e -> recalculate());
		fireRuneCostSpinner.addChangeListener(e -> recalculate());
		quantitySpinner.addChangeListener(e -> recalculate());
		targetMinProfitSpinner.addChangeListener(e -> recalculate());
		budgetCapitalSpinner.addChangeListener(e -> recalculate());

		AlchTheme.styleCheckBox(fireStaffCheckBox);
		fireRuneCostSpinner.setEnabled(!fireStaffCheckBox.isSelected());
		fireStaffCheckBox.addActionListener(e -> {
			fireRuneCostSpinner.setEnabled(!fireStaffCheckBox.isSelected());
			recalculate();
		});

		card.add(AlchTheme.createFormControlRow("Purchase Price (GP)", purchaseCostSpinner));
		card.add(AlchTheme.createFormControlRow("High Alch Value (GP)", highAlchValueSpinner));
		card.add(AlchTheme.createFormControlRow("Nature Rune Cost (GP)", natureRuneCostSpinner));
		card.add(AlchTheme.createFormControlRow("Fire Runes / Cast (GP)", fireRuneCostSpinner));
		card.add(AlchTheme.createFormControlRow("Target Profit / Alch", targetMinProfitSpinner));
		card.add(AlchTheme.createFormControlRow("Total Budget (GP)", budgetCapitalSpinner));
		card.add(fireStaffCheckBox);
		return card;
	}

	private JPanel buildQuickQuantitiesCard()
	{
		JPanel card = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
		card.setBackground(AlchTheme.BG_CARD);
		card.setBorder(AlchTheme.createCardBorder());

		JLabel title = AlchTheme.createSectionHeading("ADVANCED QUANTITY SELECTOR");
		card.add(title);
		card.add(AlchTheme.createFormControlRow("Custom Quantity", quantitySpinner));

		JPanel presetGrid = new JPanel(new ResponsiveGridLayout(64, 4, AlchTheme.SPACE_XS, AlchTheme.SPACE_XS));
		presetGrid.setOpaque(false);

		for (int qty : QUICK_QUANTITIES)
		{
			JButton btn = AlchTheme.createSecondaryButton(QuantityFormatter.formatNumber(qty));
			btn.addActionListener(e -> quantitySpinner.setValue(qty));
			presetGrid.add(btn);
		}
		card.add(presetGrid);

		JButton useGeLimitBtn = AlchTheme.createSecondaryButton("Use Selected Item 4-Hour GE Buy Limit");
		useGeLimitBtn.addActionListener(e -> {
			if (selectedItem != null && selectedItem.getBuyLimit() > 0)
			{
				quantitySpinner.setValue(selectedItem.getBuyLimit());
			}
		});
		card.add(useGeLimitBtn);
		return card;
	}

	private JPanel buildHeroResultsStrip()
	{
		JPanel strip = new JPanel(new ResponsiveGridLayout(130, 3, AlchTheme.SPACE_SM, AlchTheme.SPACE_SM));
		strip.setOpaque(false);
		strip.add(heroTotalProfitCard);
		strip.add(heroUnitProfitCard);
		strip.add(heroRoiCard);
		return strip;
	}

	private JPanel buildResultsBreakdownCard()
	{
		JPanel card = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
		card.setBackground(AlchTheme.BG_CARD);
		card.setBorder(AlchTheme.createGoldCardBorder());

		JLabel title = AlchTheme.createSectionHeading("PROFIT & EXPENDITURE BREAKDOWN");
		outSelectedNameLabel.setFont(AlchTheme.getBodyFont(true));
		outSelectedNameLabel.setForeground(AlchTheme.ACCENT_GOLD);

		card.add(AlchTheme.createSplitRow(title, outSelectedNameLabel));

		JPanel rows = new JPanel(new VerticalStackLayout(2));
		rows.setOpaque(false);

		outItemQuantityLabel.setForeground(AlchTheme.TEXT_PRIMARY);
		outPurchaseCostLabel.setForeground(AlchTheme.TEXT_PRIMARY);
		outNatureRunesRequiredLabel.setForeground(AlchTheme.TEXT_SECONDARY);
		outFireRuneExpLabel.setForeground(AlchTheme.TEXT_SECONDARY);
		outTotalInvestmentLabel.setForeground(AlchTheme.TEXT_PRIMARY);
		outGrossAlchReturnLabel.setForeground(AlchTheme.ACCENT_GOLD);
		outNetProfitPerAlchLabel.setForeground(AlchTheme.SUCCESS_GREEN);
		outTotalProfitOrLossLabel.setForeground(AlchTheme.SUCCESS_GREEN);
		outHourlyProfitLabel.setForeground(AlchTheme.SUCCESS_GREEN);
		outRoiPercentLabel.setForeground(AlchTheme.ACCENT_GOLD);
		outTotalReturnRatioLabel.setForeground(AlchTheme.TEXT_SECONDARY);
		outBreakEvenPriceLabel.setForeground(AlchTheme.ACCENT_GOLD);
		outMaxAffordableTargetLabel.setForeground(AlchTheme.TEXT_PRIMARY);
		outMaxAffordableBudgetLabel.setForeground(AlchTheme.INFO_BLUE);

		rows.add(AlchTheme.createKeyValueRow("Item Quantity", outItemQuantityLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Total Purchase Cost", outPurchaseCostLabel, false));
		rows.add(AlchTheme.createKeyValueRow("Nature Runes Required", outNatureRunesRequiredLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Fire Runes Expenditure", outFireRuneExpLabel, false));
		rows.add(AlchTheme.createKeyValueRow("Total Capital Investment", outTotalInvestmentLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Gross Alchemy Return", outGrossAlchReturnLabel, false));
		rows.add(AlchTheme.createKeyValueRow("Net Profit / Alch", outNetProfitPerAlchLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Total Net Profit / Loss", outTotalProfitOrLossLabel, false));
		rows.add(AlchTheme.createKeyValueRow("Estimated Profit / Hour", outHourlyProfitLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Return on Investment (ROI)", outRoiPercentLabel, false));
		rows.add(AlchTheme.createKeyValueRow("Gross Return Ratio", outTotalReturnRatioLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Break-Even Purchase Price", outBreakEvenPriceLabel, false));
		rows.add(AlchTheme.createKeyValueRow("Max Buy Price (For Target Profit)", outMaxAffordableTargetLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Max Buy Price (Within Budget)", outMaxAffordableBudgetLabel, false));

		card.add(rows);
		return card;
	}

	/**
	 * Loads an {@link AlchMateItem} into the calculator fields and immediately computes results.
	 */
	public void loadItem(AlchMateItem item)
	{
		if (item == null)
		{
			return;
		}
		this.selectedItem = item;
		outSelectedNameLabel.setText(item.getName() + " (#" + item.getId() + ")");
		purchaseCostSpinner.setValue((int) Math.min(Integer.MAX_VALUE, Math.max(1L, item.getEstimatedPurchasePrice())));
		highAlchValueSpinner.setValue(Math.max(1, item.getHighAlchValue()));
		natureRuneCostSpinner.setValue((int) marketDataService.getEffectiveNatureRuneCost());
		recalculate();
	}

	public void syncDefaultsFromConfig()
	{
		if (marketDataService == null)
		{
			return;
		}
		natureRuneCostSpinner.setValue((int) marketDataService.getEffectiveNatureRuneCost());
		fireStaffCheckBox.setSelected(marketDataService.isRuntimeUseFireStaff());
		fireRuneCostSpinner.setValue(Math.max(0, marketDataService.getRuntimeManualFireRuneCost()));
		fireRuneCostSpinner.setEnabled(!marketDataService.isRuntimeUseFireStaff());
	}

	private void searchMatchingItems()
	{
		String q = itemLookupField.getText() != null ? itemLookupField.getText().trim().toLowerCase(Locale.ROOT) : "";
		List<AlchMateItem> all = marketDataService.getAllAnalysedItems();

		suppressComboEvents = true;
		try
		{
			quickMatchedItemsCombo.removeAllItems();
			int count = 0;
			for (AlchMateItem item : all)
			{
				if (q.isEmpty() || item.getName().toLowerCase(Locale.ROOT).contains(q))
				{
					quickMatchedItemsCombo.addItem(item.getName() + " [#" + item.getId() + "]");
					count++;
					if (count >= 35)
					{
						break;
					}
				}
			}
			if (quickMatchedItemsCombo.getItemCount() == 0)
			{
				quickMatchedItemsCombo.addItem("No matching live items found");
			}
		}
		finally
		{
			suppressComboEvents = false;
		}

		if (quickMatchedItemsCombo.getItemCount() > 0)
		{
			quickMatchedItemsCombo.setSelectedIndex(0);
		}
	}

	public void recalculate()
	{
		long buyCost = ((Number) purchaseCostSpinner.getValue()).longValue();
		long alchVal = ((Number) highAlchValueSpinner.getValue()).longValue();
		long natCost = ((Number) natureRuneCostSpinner.getValue()).longValue();
		long fireCost = fireStaffCheckBox.isSelected() ? 0L : ((Number) fireRuneCostSpinner.getValue()).longValue();
		int qty = ((Number) quantitySpinner.getValue()).intValue();
		long targetMinProfit = ((Number) targetMinProfitSpinner.getValue()).longValue();
		long budget = ((Number) budgetCapitalSpinner.getValue()).longValue();
		int buyLimit = selectedItem != null ? selectedItem.getBuyLimit() : 0;

		AlchCalculation calc = profitCalculator.calculate(
			selectedItem != null ? selectedItem.getId() : 0,
			alchVal,
			buyCost,
			natCost,
			fireCost,
			0L,
			qty,
			buyLimit,
			targetMinProfit
		);

		long maxUnitFromBudget = profitCalculator.calculateMaxAffordableUnitPriceFromBudget(
			budget,
			qty,
			natCost,
			fireCost,
			0L
		);

		long unitProfit = calc.getNetProfitPerAlch();
		long totalProfit = calc.getTotalNetProfit();
		var profitColor = AlchTheme.getProfitColor(unitProfit);

		heroTotalProfitCard.updateCard(
			AlchTheme.formatSignedGp(totalProfit),
			QuantityFormatter.formatNumber(qty) + " items",
			profitColor
		);
		heroUnitProfitCard.updateCard(
			AlchTheme.formatSignedGp(unitProfit),
			AlchTheme.formatHourlyProfit(unitProfit),
			profitColor
		);
		heroRoiCard.updateCard(
			AlchTheme.formatRoi(calc.getRoiPercent()),
			"Break-even: " + AlchTheme.formatGp(calc.getBreakEvenPurchasePrice()),
			unitProfit >= 0 ? AlchTheme.ACCENT_GOLD : AlchTheme.DANGER_RED
		);

		outItemQuantityLabel.setText(QuantityFormatter.formatNumber(qty));
		outPurchaseCostLabel.setText(AlchTheme.formatGp(calc.getTotalPurchaseCost()));
		outNatureRunesRequiredLabel.setText(String.format(
			"%s (%s)",
			QuantityFormatter.formatNumber(qty),
			AlchTheme.formatGp(calc.getTotalNatureRuneExpenditure())
		));
		outFireRuneExpLabel.setText(fireStaffCheckBox.isSelected()
			? "0 GP (Fire Staff)"
			: AlchTheme.formatGp(calc.getTotalFireRuneExpenditure()));
		outTotalInvestmentLabel.setText(AlchTheme.formatGp(calc.getTotalInvestment()));
		outGrossAlchReturnLabel.setText(AlchTheme.formatGp(calc.getGrossAlchemyReturn()));

		outNetProfitPerAlchLabel.setText(AlchTheme.formatSignedGp(unitProfit));
		outNetProfitPerAlchLabel.setForeground(profitColor);

		outTotalProfitOrLossLabel.setText(AlchTheme.formatSignedGp(totalProfit));
		outTotalProfitOrLossLabel.setForeground(profitColor);

		outHourlyProfitLabel.setText(AlchTheme.formatHourlyProfit(unitProfit));
		outHourlyProfitLabel.setForeground(profitColor);

		outRoiPercentLabel.setText(AlchTheme.formatRoi(calc.getRoiPercent()));
		outRoiPercentLabel.setForeground(unitProfit >= 0 ? AlchTheme.ACCENT_GOLD : AlchTheme.DANGER_RED);

		outTotalReturnRatioLabel.setText(String.format("%.1f%%", calc.getTotalReturnRatioPercent()));
		outBreakEvenPriceLabel.setText(AlchTheme.formatGp(calc.getBreakEvenPurchasePrice()));
		outMaxAffordableTargetLabel.setText(AlchTheme.formatGp(calc.getMaxAffordablePurchasePrice()));
		outMaxAffordableBudgetLabel.setText(AlchTheme.formatGp(maxUnitFromBudget));
	}
}
