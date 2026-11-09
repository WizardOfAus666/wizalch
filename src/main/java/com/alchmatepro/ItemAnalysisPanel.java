package com.alchmatepro;

import com.alchmatepro.AlchUiComponents.MetricCard;
import com.alchmatepro.AlchUiComponents.ResponsiveGridLayout;
import com.alchmatepro.AlchUiComponents.VerticalStackLayout;
import com.alchmatepro.NavigationController.Section;
import com.alchmatepro.PriceTrendSummary.MarginDirection;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import net.runelite.client.util.QuantityFormatter;

/**
 * Detailed Item Analysis profile page (Sections 09 & 11).
 * Displays comprehensive pricing breakdown, hero metrics strip, 4-hour GE buy limit intelligence,
 * historical price trend chart, margin direction indicator, embedded calculator,
 * and watchlist controls.
 */
public class ItemAnalysisPanel extends JPanel
{
	private final MarketDataService marketDataService;
	private final ProfitCalculator profitCalculator;
	private final PriceHistoryService priceHistoryService;
	private final WatchlistService watchlistService;
	private final IconService iconService;
	private final NavigationController navigationController;

	private AlchMateItem currentItem;

	// Header & Hero UI
	private final JLabel itemIconLabel = new JLabel();
	private final JLabel itemNameLabel = new JLabel("Select an Item");
	private final JLabel itemMetaSubLabel = new JLabel("ID: - • -");
	private final JButton watchlistToggleBtn = AlchTheme.createSecondaryButton("☆ Add to Watchlist");

	private final MetricCard heroProfitCard = new MetricCard("PROFIT / ALCH", "0 GP", "Net per cast", AlchTheme.SUCCESS_GREEN);
	private final MetricCard heroRoiCard = new MetricCard("ROI %", "0.0%", "Return on cost", AlchTheme.ACCENT_GOLD);
	private final MetricCard heroHourlyCard = new MetricCard("PROFIT / HOUR", "0 GP/hr", "1,200 casts/hr", AlchTheme.SUCCESS_GREEN);

	// Core Price & Margin Labels
	private final JLabel highAlchValLabel = new JLabel("-");
	private final JLabel lowAlchValLabel = new JLabel("-");
	private final JLabel estBuyPriceLabel = new JLabel("-");
	private final JLabel instantBuyPriceLabel = new JLabel("-");
	private final JLabel instantSellPriceLabel = new JLabel("-");
	private final JLabel stable1hPriceLabel = new JLabel("-");
	private final JLabel avg5mPriceLabel = new JLabel("-");
	private final JLabel officialGePriceLabel = new JLabel("-");
	private final JLabel natureRunePriceLabel = new JLabel("-");
	private final JLabel breakEvenPriceLabel = new JLabel("-");
	private final JLabel netProfitLabel = new JLabel("-");
	private final JLabel roiLabel = new JLabel("-");
	private final JLabel volume1hLabel = new JLabel("-");
	private final JLabel lastUpdatedLabel = new JLabel("-");

	// Buy Limit Intelligence Labels
	private final JLabel buyLimit4hLabel = new JLabel("-");
	private final JLabel maxLimitInvestmentLabel = new JLabel("-");
	private final JLabel maxLimitProfitLabel = new JLabel("-");
	private final JLabel maxLimitRunesLabel = new JLabel("-");
	private final JTextArea buyLimitSupplyWarningArea = AlchUiComponents.createReadOnlyTextArea("", AlchTheme.ACCENT_GOLD);

	// Recommendation & Warnings Box
	private final JTextArea recommendationTextArea = AlchUiComponents.createReadOnlyTextArea("", AlchTheme.TEXT_PRIMARY);
	private final JPanel warningBadgesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));

	// Price History & Trend Graph
	private final PriceTrendChartPanel trendChartPanel = new PriceTrendChartPanel();
	private final JLabel marginDirectionLabel = new JLabel("-");
	private final JLabel priceChangeLabel = new JLabel("-");
	private final JLabel volatilityLabel = new JLabel("-");

	// Embedded Quantity Calculator
	private final JSpinner embeddedQuantitySpinner = new JSpinner(new SpinnerNumberModel(100, 1, 1000000, 25));
	private final JLabel calcTotalCostLabel = new JLabel("-");
	private final JLabel calcNatureRunesLabel = new JLabel("-");
	private final JLabel calcTotalReturnLabel = new JLabel("-");
	private final JLabel calcTotalProfitLabel = new JLabel("-");

	public ItemAnalysisPanel(
		MarketDataService marketDataService,
		ProfitCalculator profitCalculator,
		PriceHistoryService priceHistoryService,
		WatchlistService watchlistService,
		IconService iconService,
		NavigationController navigationController)
	{
		super(new VerticalStackLayout(AlchTheme.SPACE_SM));
		this.marketDataService = marketDataService;
		this.profitCalculator = profitCalculator;
		this.priceHistoryService = priceHistoryService;
		this.watchlistService = watchlistService;
		this.iconService = iconService;
		this.navigationController = navigationController;

		setBackground(AlchTheme.BG_MAIN);
		setBorder(BorderFactory.createEmptyBorder(
			AlchTheme.SPACE_SM, AlchTheme.SPACE_SM, AlchTheme.SPACE_MD, AlchTheme.SPACE_SM
		));

		add(buildHeaderAndHeroCard());
		add(buildRecommendationCard());
		add(buildPricingCard());
		add(buildBuyLimitCard());
		add(buildPriceHistoryCard());
		add(buildEmbeddedCalculatorCard());
	}

	private JPanel buildHeaderAndHeroCard()
	{
		JPanel wrapper = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_MD));
		wrapper.setOpaque(false);

		JPanel card = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_MD));
		card.setBackground(AlchTheme.BG_CARD);
		card.setBorder(AlchTheme.createGoldCardBorder());

		JButton backBtn = AlchTheme.createSecondaryButton("← Scanner");
		backBtn.addActionListener(e -> navigationController.navigateTo(Section.ALCH_SCANNER));

		watchlistToggleBtn.addActionListener(e -> {
			if (currentItem != null)
			{
				watchlistService.toggleItem(currentItem);
				updateWatchlistButtonState();
			}
		});

		JPanel topNav = AlchTheme.createSplitRow(backBtn, watchlistToggleBtn);

		JPanel identityRow = new JPanel(new GridBagLayout());
		identityRow.setOpaque(false);

		GridBagConstraints gbcIcon = new GridBagConstraints();
		gbcIcon.gridx = 0;
		gbcIcon.gridy = 0;
		gbcIcon.insets = new Insets(0, 0, 0, AlchTheme.SPACE_MD);
		gbcIcon.anchor = GridBagConstraints.WEST;
		identityRow.add(itemIconLabel, gbcIcon);

		JPanel nameStack = new JPanel(new VerticalStackLayout(2));
		nameStack.setOpaque(false);

		itemNameLabel.setFont(AlchTheme.getPageHeadingFont());
		itemNameLabel.setForeground(AlchTheme.ACCENT_GOLD);

		itemMetaSubLabel.setFont(AlchTheme.getBodyFont(false));
		itemMetaSubLabel.setForeground(AlchTheme.TEXT_SECONDARY);

		nameStack.add(itemNameLabel);
		nameStack.add(itemMetaSubLabel);

		GridBagConstraints gbcName = new GridBagConstraints();
		gbcName.gridx = 1;
		gbcName.gridy = 0;
		gbcName.weightx = 1.0;
		gbcName.fill = GridBagConstraints.HORIZONTAL;
		gbcName.anchor = GridBagConstraints.WEST;
		identityRow.add(nameStack, gbcName);

		card.add(topNav);
		card.add(identityRow);

		JPanel heroStrip = new JPanel(new ResponsiveGridLayout(130, 3, AlchTheme.SPACE_SM, AlchTheme.SPACE_SM));
		heroStrip.setOpaque(false);
		heroStrip.add(heroProfitCard);
		heroStrip.add(heroRoiCard);
		heroStrip.add(heroHourlyCard);

		wrapper.add(card);
		wrapper.add(heroStrip);
		return wrapper;
	}

	private JPanel buildRecommendationCard()
	{
		JPanel card = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
		card.setBackground(AlchTheme.BG_CARD);
		card.setBorder(AlchTheme.createCardBorder());

		JLabel title = AlchTheme.createSectionHeading("INTELLIGENCE ASSESSMENT");
		warningBadgesPanel.setOpaque(false);

		card.add(title);
		card.add(warningBadgesPanel);
		card.add(recommendationTextArea);
		return card;
	}

	private JPanel buildPricingCard()
	{
		JPanel card = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
		card.setBackground(AlchTheme.BG_CARD);
		card.setBorder(AlchTheme.createCardBorder());

		JLabel title = AlchTheme.createSectionHeading("MARKET PRICING & ALCHEMY MARGIN");
		card.add(title);

		JPanel rows = new JPanel(new VerticalStackLayout(2));
		rows.setOpaque(false);

		netProfitLabel.setForeground(AlchTheme.SUCCESS_GREEN);
		roiLabel.setForeground(AlchTheme.ACCENT_GOLD);
		highAlchValLabel.setForeground(AlchTheme.ACCENT_GOLD);
		lowAlchValLabel.setForeground(AlchTheme.TEXT_SECONDARY);
		estBuyPriceLabel.setForeground(AlchTheme.TEXT_PRIMARY);
		instantBuyPriceLabel.setForeground(AlchTheme.TEXT_PRIMARY);
		instantSellPriceLabel.setForeground(AlchTheme.TEXT_SECONDARY);
		stable1hPriceLabel.setForeground(AlchTheme.TEXT_PRIMARY);
		avg5mPriceLabel.setForeground(AlchTheme.TEXT_SECONDARY);
		officialGePriceLabel.setForeground(AlchTheme.TEXT_MUTED);
		natureRunePriceLabel.setForeground(AlchTheme.TEXT_SECONDARY);
		breakEvenPriceLabel.setForeground(AlchTheme.ACCENT_GOLD);
		volume1hLabel.setForeground(AlchTheme.INFO_BLUE);
		lastUpdatedLabel.setForeground(AlchTheme.TEXT_MUTED);

		rows.add(AlchTheme.createKeyValueRow("Net Profit / Alch", netProfitLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Return on Investment", roiLabel, false));
		rows.add(AlchTheme.createKeyValueRow("High Alchemy Value", highAlchValLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Low Alchemy Value", lowAlchValLabel, false));
		rows.add(AlchTheme.createKeyValueRow("Estimated Purchase Price", estBuyPriceLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Latest Instant-Buy", instantBuyPriceLabel, false));
		rows.add(AlchTheme.createKeyValueRow("Latest Instant-Sell", instantSellPriceLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Stable 1h Average", stable1hPriceLabel, false));
		rows.add(AlchTheme.createKeyValueRow("5m Rolling Average", avg5mPriceLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Official GE Guide Price", officialGePriceLabel, false));
		rows.add(AlchTheme.createKeyValueRow("Nature Rune Cost", natureRunePriceLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Break-Even Buy Price", breakEvenPriceLabel, false));
		rows.add(AlchTheme.createKeyValueRow("1h Trading Volume", volume1hLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Last Trade Recorded", lastUpdatedLabel, false));

		card.add(rows);
		return card;
	}

	private JPanel buildBuyLimitCard()
	{
		JPanel card = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
		card.setBackground(AlchTheme.BG_CARD);
		card.setBorder(AlchTheme.createCardBorder());

		JLabel title = AlchTheme.createSectionHeading("4-HOUR GE BUY LIMIT INTELLIGENCE");
		card.add(title);

		JPanel rows = new JPanel(new VerticalStackLayout(2));
		rows.setOpaque(false);

		buyLimit4hLabel.setForeground(AlchTheme.TEXT_PRIMARY);
		maxLimitInvestmentLabel.setForeground(AlchTheme.TEXT_PRIMARY);
		maxLimitProfitLabel.setForeground(AlchTheme.SUCCESS_GREEN);
		maxLimitRunesLabel.setForeground(AlchTheme.TEXT_SECONDARY);

		rows.add(AlchTheme.createKeyValueRow("4-Hour GE Buy Limit", buyLimit4hLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Cycle Investment", maxLimitInvestmentLabel, false));
		rows.add(AlchTheme.createKeyValueRow("Theoretical 4h Profit", maxLimitProfitLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Nature Runes / Casts", maxLimitRunesLabel, false));

		JTextArea disclaimer = AlchUiComponents.createReadOnlyTextArea(
			"Note: GE buy limits are theoretical 4-hour caps and do not guarantee immediate order fill.",
			AlchTheme.TEXT_MUTED
		);
		disclaimer.setFont(AlchTheme.getSupportFont(false));

		card.add(rows);
		card.add(buyLimitSupplyWarningArea);
		card.add(disclaimer);
		return card;
	}

	private JPanel buildPriceHistoryCard()
	{
		JPanel card = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
		card.setBackground(AlchTheme.BG_CARD);
		card.setBorder(AlchTheme.createCardBorder());

		JLabel title = AlchTheme.createSectionHeading("PRICE HISTORY & MARGIN TREND");
		card.add(title);
		card.add(trendChartPanel);

		JPanel rows = new JPanel(new VerticalStackLayout(2));
		rows.setOpaque(false);
		marginDirectionLabel.setForeground(AlchTheme.TEXT_PRIMARY);
		priceChangeLabel.setForeground(AlchTheme.TEXT_SECONDARY);
		volatilityLabel.setForeground(AlchTheme.TEXT_MUTED);

		rows.add(AlchTheme.createKeyValueRow("Margin Status", marginDirectionLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Buy Price Delta", priceChangeLabel, false));
		rows.add(AlchTheme.createKeyValueRow("Price Range & Volatility", volatilityLabel, true));

		card.add(rows);
		return card;
	}

	private JPanel buildEmbeddedCalculatorCard()
	{
		JPanel card = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
		card.setBackground(AlchTheme.BG_CARD);
		card.setBorder(AlchTheme.createCardBorder());

		JLabel title = AlchTheme.createSectionHeading("QUICK QUANTITY CALCULATOR");
		card.add(title);

		AlchTheme.styleSpinner(embeddedQuantitySpinner);
		embeddedQuantitySpinner.addChangeListener(e -> recalculateEmbeddedQuantity());
		card.add(AlchTheme.createFormControlRow("Batch Quantity", embeddedQuantitySpinner));

		JPanel presetButtons = new JPanel(new ResponsiveGridLayout(56, 5, AlchTheme.SPACE_XS, AlchTheme.SPACE_XS));
		presetButtons.setOpaque(false);
		int[] presets = {10, 50, 100, 500, 1000};
		for (int p : presets)
		{
			JButton b = AlchTheme.createSecondaryButton(QuantityFormatter.formatNumber(p));
			b.addActionListener(e -> embeddedQuantitySpinner.setValue(p));
			presetButtons.add(b);
		}
		card.add(presetButtons);

		JPanel resultRows = new JPanel(new VerticalStackLayout(2));
		resultRows.setOpaque(false);
		calcTotalCostLabel.setForeground(AlchTheme.TEXT_PRIMARY);
		calcNatureRunesLabel.setForeground(AlchTheme.TEXT_SECONDARY);
		calcTotalReturnLabel.setForeground(AlchTheme.ACCENT_GOLD);
		calcTotalProfitLabel.setForeground(AlchTheme.SUCCESS_GREEN);

		resultRows.add(AlchTheme.createKeyValueRow("Purchase Cost", calcTotalCostLabel, true));
		resultRows.add(AlchTheme.createKeyValueRow("Nature Runes Cost", calcNatureRunesLabel, false));
		resultRows.add(AlchTheme.createKeyValueRow("Total Alch Return", calcTotalReturnLabel, true));
		resultRows.add(AlchTheme.createKeyValueRow("Total Net Profit", calcTotalProfitLabel, false));
		card.add(resultRows);

		JButton openFullCalcBtn = AlchTheme.createPrimaryButton("Open in Advanced Profit Calculator →");
		openFullCalcBtn.addActionListener(e -> {
			if (currentItem != null)
			{
				navigationController.openProfitCalculator(currentItem);
			}
		});
		card.add(openFullCalcBtn);
		return card;
	}

	/**
	 * Populates the Item Analysis view for the selected {@link AlchMateItem}.
	 */
	public void displayItem(AlchMateItem item)
	{
		if (item == null)
		{
			List<AlchMateItem> all = marketDataService.getAllAnalysedItems();
			if (!all.isEmpty())
			{
				item = all.get(0);
			}
		}
		this.currentItem = item;
		if (item == null)
		{
			itemNameLabel.setText("No Item Selected");
			recommendationTextArea.setText("Select an item from the Dashboard or Live Alch Scanner to inspect its market profile.");
			return;
		}

		iconService.attachItemIcon(itemIconLabel, item.getId(), item.getName());
		itemNameLabel.setText(item.getName());
		itemMetaSubLabel.setText(String.format(
			"ID: %d • %s • Source: %s",
			item.getId(),
			item.isMembers() ? "Members (P2P)" : "Free-to-Play (F2P)",
			item.getPriceSourceType() != null ? item.getPriceSourceType().getLabel() : "GE Estimate"
		));
		updateWatchlistButtonState();

		MarketPrice mp = item.getMarketPrice();
		AlchCalculation calc = item.getCalculation();
		AlchRecommendation rec = item.getRecommendation();

		long profit = item.getNetProfitPerAlch();
		var profitColor = AlchTheme.getProfitColor(profit);

		heroProfitCard.updateCard(
			AlchTheme.formatSignedGp(profit),
			profit >= 0 ? "Profitable margin" : "Net loss per cast",
			profitColor
		);
		heroRoiCard.updateCard(
			AlchTheme.formatRoi(item.getRoiPercent()),
			"Break-even: " + AlchTheme.formatGp(calc.getBreakEvenPurchasePrice()),
			profit >= 0 ? AlchTheme.ACCENT_GOLD : AlchTheme.DANGER_RED
		);
		heroHourlyCard.updateCard(
			AlchTheme.formatHourlyProfit(profit),
			"At 1,200 casts / hr",
			profitColor
		);

		netProfitLabel.setText(AlchTheme.formatSignedGp(profit));
		netProfitLabel.setForeground(profitColor);

		roiLabel.setText(AlchTheme.formatRoi(item.getRoiPercent()));
		roiLabel.setForeground(profit >= 0 ? AlchTheme.ACCENT_GOLD : AlchTheme.DANGER_RED);

		highAlchValLabel.setText(AlchTheme.formatGp(item.getHighAlchValue()));
		lowAlchValLabel.setText(AlchTheme.formatGp(item.getLowAlchValue()));
		estBuyPriceLabel.setText(AlchTheme.formatGp(item.getEstimatedPurchasePrice()));

		instantBuyPriceLabel.setText(mp != null && mp.getInstantBuyPrice() != null
			? AlchTheme.formatGp(mp.getInstantBuyPrice())
			: "Unavailable");
		instantSellPriceLabel.setText(mp != null && mp.getInstantSellPrice() != null
			? AlchTheme.formatGp(mp.getInstantSellPrice())
			: "Unavailable");
		stable1hPriceLabel.setText(mp != null && mp.getAvgHighPrice1h() != null
			? AlchTheme.formatGp(mp.getAvgHighPrice1h())
			: "Unavailable");
		avg5mPriceLabel.setText(mp != null && mp.getAvgHighPrice5m() != null
			? AlchTheme.formatGp(mp.getAvgHighPrice5m())
			: "Unavailable");
		officialGePriceLabel.setText(mp != null && mp.getOfficialGePrice() != null && mp.getOfficialGePrice() > 0
			? AlchTheme.formatGp(mp.getOfficialGePrice())
			: "N/A");

		natureRunePriceLabel.setText(AlchTheme.formatGp(calc.getNatureRuneCostPerCast()));
		breakEvenPriceLabel.setText(AlchTheme.formatGp(calc.getBreakEvenPurchasePrice()));
		volume1hLabel.setText(QuantityFormatter.formatNumber(item.getVolume1h()) + " items / hr");
		lastUpdatedLabel.setText(item.getFreshnessLabel() + (item.isStale() ? " (STALE)" : ""));
		lastUpdatedLabel.setForeground(item.isStale() ? AlchTheme.ACCENT_GOLD : AlchTheme.TEXT_MUTED);

		// Buy Limit Intelligence
		int limit = item.getBuyLimit();
		if (limit > 0)
		{
			buyLimit4hLabel.setText(QuantityFormatter.formatNumber(limit) + " items");
			maxLimitInvestmentLabel.setText(AlchTheme.formatGp(calc.getMaxBuyLimitInvestment()));
			maxLimitProfitLabel.setText(AlchTheme.formatSignedGp(calc.getMaxBuyLimitProfit4h()));
			maxLimitProfitLabel.setForeground(AlchTheme.getProfitColor(calc.getMaxBuyLimitProfit4h()));
			maxLimitRunesLabel.setText(QuantityFormatter.formatNumber(limit) + " Nats (" + QuantityFormatter.formatNumber(limit) + " casts)");

			int estimated4hVol = item.getVolume1h() * 4;
			if (estimated4hVol < limit)
			{
				buyLimitSupplyWarningArea.setText(String.format(
					"⚠ Low Supply Warning: Est. 4h market volume (%s) is below the %s buy limit.",
					QuantityFormatter.formatNumber(estimated4hVol),
					QuantityFormatter.formatNumber(limit)
				));
				buyLimitSupplyWarningArea.setForeground(AlchTheme.ACCENT_GOLD);
			}
			else
			{
				buyLimitSupplyWarningArea.setText("✓ Healthy liquidity: 4h trade volume exceeds GE buy limit.");
				buyLimitSupplyWarningArea.setForeground(AlchTheme.SUCCESS_GREEN);
			}
		}
		else
		{
			buyLimit4hLabel.setText("Unknown");
			maxLimitInvestmentLabel.setText("-");
			maxLimitProfitLabel.setText("-");
			maxLimitRunesLabel.setText("-");
			buyLimitSupplyWarningArea.setText("⚠ GE buying limit metadata is not listed for this item.");
			buyLimitSupplyWarningArea.setForeground(AlchTheme.ACCENT_GOLD);
		}

		// Recommendation & Warning Badges
		warningBadgesPanel.removeAll();
		if (rec != null)
		{
			recommendationTextArea.setText(rec.getExplanation());
			JLabel scoreBadge = AlchTheme.createBadge(
				String.format("Smart Score: %.1f / 100", rec.getOverallScore()),
				AlchTheme.ACCENT_GOLD,
				AlchTheme.ACCENT_GOLD_DIM
			);
			warningBadgesPanel.add(scoreBadge);

			for (RecommendationCategory cat : rec.getCategories())
			{
				warningBadgesPanel.add(AlchTheme.createBadge(
					cat.getTitle(),
					AlchTheme.SUCCESS_GREEN,
					AlchTheme.SUCCESS_BG
				));
			}
			for (MarketWarning warn : rec.getWarnings())
			{
				JLabel wb = AlchTheme.createBadge(warn.getBadgeText(), AlchTheme.DANGER_RED, AlchTheme.DANGER_BG);
				wb.setToolTipText(warn.getExplanation());
				warningBadgesPanel.add(wb);
			}
		}

		recalculateEmbeddedQuantity();
		loadPriceHistoryAsync(item);
		revalidate();
		repaint();
	}

	private void recalculateEmbeddedQuantity()
	{
		if (currentItem == null)
		{
			return;
		}
		int qty = ((Number) embeddedQuantitySpinner.getValue()).intValue();
		AlchCalculation base = currentItem.getCalculation();
		AlchCalculation customCalc = profitCalculator.calculate(
			currentItem.getId(),
			currentItem.getHighAlchValue(),
			currentItem.getEstimatedPurchasePrice(),
			base.getNatureRuneCostPerCast(),
			base.getFireRuneCostPerCast(),
			qty,
			currentItem.getBuyLimit()
		);

		calcTotalCostLabel.setText(AlchTheme.formatGp(customCalc.getTotalPurchaseCost()));
		calcNatureRunesLabel.setText(String.format(
			"%s (%s)",
			QuantityFormatter.formatNumber(qty),
			AlchTheme.formatGp(customCalc.getTotalNatureRuneExpenditure())
		));
		calcTotalReturnLabel.setText(AlchTheme.formatGp(customCalc.getGrossAlchemyReturn()));
		calcTotalProfitLabel.setText(AlchTheme.formatSignedGp(customCalc.getTotalNetProfit())
			+ " (" + AlchTheme.formatRoi(customCalc.getRoiPercent()) + ")");
		calcTotalProfitLabel.setForeground(AlchTheme.getProfitColor(customCalc.getTotalNetProfit()));
	}

	private void loadPriceHistoryAsync(AlchMateItem item)
	{
		final int targetId = item.getId();
		final long breakEven = item.getCalculation().getBreakEvenPurchasePrice();

		marginDirectionLabel.setText("Loading history...");
		priceChangeLabel.setText("Fetching /timeseries...");
		volatilityLabel.setText("-");
		trendChartPanel.setTrendData(List.of(), breakEven);

		marketDataService.submitBackgroundTask(() -> {
			try
			{
				PriceTrendSummary summary = priceHistoryService.fetchTrendSummary(targetId, "1h");
				SwingUtilities.invokeLater(() -> {
					if (currentItem == null || currentItem.getId() != targetId)
					{
						return;
					}
					applyTrendSummary(summary, breakEven);
				});
			}
			catch (Exception ex)
			{
				SwingUtilities.invokeLater(() -> {
					if (currentItem == null || currentItem.getId() != targetId)
					{
						return;
					}
					marginDirectionLabel.setText("Historical series unavailable");
					priceChangeLabel.setText("Using 1h & 5m snapshot comparison");
				});
			}
		});
	}

	private void applyTrendSummary(PriceTrendSummary summary, long breakEven)
	{
		if (summary == null || summary.getPoints().isEmpty())
		{
			marginDirectionLabel.setText("Insufficient historical points");
			return;
		}
		trendChartPanel.setTrendData(summary.getPoints(), breakEven);

		MarginDirection dir = summary.getMarginDirection();
		marginDirectionLabel.setText(dir.getLabel());
		if (dir == MarginDirection.IMPROVING)
		{
			marginDirectionLabel.setForeground(AlchTheme.SUCCESS_GREEN);
		}
		else if (dir == MarginDirection.DECLINING)
		{
			marginDirectionLabel.setForeground(AlchTheme.DANGER_RED);
		}
		else
		{
			marginDirectionLabel.setForeground(AlchTheme.ACCENT_GOLD);
		}

		priceChangeLabel.setText(String.format(
			"%s (%s%.1f%%)",
			AlchTheme.formatSignedGp(summary.getPriceChangeGp()),
			summary.getPriceChangePercent() > 0 ? "+" : "",
			summary.getPriceChangePercent()
		));
		volatilityLabel.setText(String.format(
			"%s – %s (%.1f%% vol)",
			AlchTheme.formatUnsignedCompactGp(summary.getMinBuyPrice()),
			AlchTheme.formatUnsignedCompactGp(summary.getMaxBuyPrice()),
			summary.getVolatilityPercent()
		));
	}

	private void updateWatchlistButtonState()
	{
		if (currentItem != null && watchlistService.isWatched(currentItem.getId()))
		{
			watchlistToggleBtn.setText("★ Remove from Watchlist");
			watchlistToggleBtn.setForeground(AlchTheme.ACCENT_GOLD);
		}
		else
		{
			watchlistToggleBtn.setText("☆ Add to Watchlist");
			watchlistToggleBtn.setForeground(AlchTheme.TEXT_PRIMARY);
		}
	}

	public void refreshCurrentItem()
	{
		if (currentItem != null)
		{
			marketDataService.getItemById(currentItem.getId()).ifPresent(this::displayItem);
		}
		else
		{
			List<AlchMateItem> all = marketDataService.getAllAnalysedItems();
			if (!all.isEmpty())
			{
				displayItem(all.get(0));
			}
		}
	}

	public void shutDown()
	{
	}
}
