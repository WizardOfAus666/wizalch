package com.alchmatepro;

import com.alchmatepro.AlchUiComponents.MetricCard;
import com.alchmatepro.AlchUiComponents.ResponsiveGridLayout;
import com.alchmatepro.AlchUiComponents.VerticalStackLayout;
import com.alchmatepro.NavigationController.Section;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Overview Dashboard panel (Section 05).
 * Structured into Section C (Live Data Status Card), Section D (Responsive Performance Cards),
 * and Section E (Best Items to High Alch with expandable details).
 */
public class DashboardPanel extends JPanel
{
	private final MarketDataService marketDataService;
	private final RecommendationEngine recommendationEngine;
	private final IconService iconService;
	private final NavigationController navigationController;

	// Section C: Live Data Status labels
	private final JLabel statusBadgeLabel = AlchTheme.createBadge("LIVE DATA", AlchTheme.SUCCESS_GREEN, AlchTheme.SUCCESS_BG);
	private final JLabel connectionStateValueLabel = new JLabel("Connected");
	private final JLabel lastUpdatedValueLabel = new JLabel("Syncing...");
	private final JLabel natureRuneValueLabel = new JLabel("180 GP (Live GE)");
	private final JLabel analysedCountValueLabel = new JLabel("0");
	private final JLabel providerNameValueLabel = new JLabel("OSRS Wiki API");

	// Section D: 4 Responsive Performance Metric Cards
	private final MetricCard cardMostProfitable = new MetricCard(
		"MOST PROFITABLE ITEM", "0 GP", "Scanning market...", AlchTheme.SUCCESS_GREEN
	);
	private final MetricCard cardBestRoi = new MetricCard(
		"BEST ROI", "0.0%", "Scanning market...", AlchTheme.ACCENT_GOLD
	);
	private final MetricCard cardProfitableCount = new MetricCard(
		"PROFITABLE ITEMS", "0 items", "Active margins", AlchTheme.SUCCESS_GREEN
	);
	private final MetricCard cardBestBulk = new MetricCard(
		"BEST BULK (4H)", "0 GP", "Awaiting volume...", AlchTheme.ACCENT_GOLD
	);

	// Section E: Top 10 Items container
	private final JPanel topItemsContainer = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));

	public DashboardPanel(
		MarketDataService marketDataService,
		RecommendationEngine recommendationEngine,
		IconService iconService,
		NavigationController navigationController)
	{
		super(new VerticalStackLayout(AlchTheme.SPACE_MD));
		this.marketDataService = marketDataService;
		this.recommendationEngine = recommendationEngine;
		this.iconService = iconService;
		this.navigationController = navigationController;

		setBackground(AlchTheme.BG_MAIN);
		setBorder(BorderFactory.createEmptyBorder(
			AlchTheme.SPACE_SM, AlchTheme.SPACE_SM, AlchTheme.SPACE_MD, AlchTheme.SPACE_SM
		));

		add(buildLiveDataStatusCard());
		add(buildPerformanceCardsSection());
		add(buildTopItemsSection());
	}

	private JPanel buildLiveDataStatusCard()
	{
		JPanel card = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
		card.setBackground(AlchTheme.BG_CARD);
		card.setBorder(AlchTheme.createGoldCardBorder());

		JPanel statusLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, AlchTheme.SPACE_SM, 0));
		statusLeft.setOpaque(false);
		JLabel cardTitle = new JLabel("LIVE MARKET STATUS");
		cardTitle.setFont(AlchTheme.getSectionHeadingFont());
		cardTitle.setForeground(AlchTheme.TEXT_PRIMARY);
		statusLeft.add(statusBadgeLabel);
		statusLeft.add(cardTitle);

		JButton refreshBtn = AlchTheme.createSecondaryButton("Refresh");
		refreshBtn.setToolTipText("Fetch latest Grand Exchange prices now");
		refreshBtn.addActionListener(e -> marketDataService.refreshAsync());

		JPanel topHeader = AlchTheme.createSplitRow(statusLeft, refreshBtn);

		JPanel rowsStack = new JPanel(new VerticalStackLayout(2));
		rowsStack.setOpaque(false);
		rowsStack.setBorder(BorderFactory.createEmptyBorder(AlchTheme.SPACE_XS, 0, 0, 0));

		connectionStateValueLabel.setForeground(AlchTheme.SUCCESS_GREEN);
		lastUpdatedValueLabel.setForeground(AlchTheme.TEXT_PRIMARY);
		natureRuneValueLabel.setForeground(AlchTheme.ACCENT_GOLD);
		analysedCountValueLabel.setForeground(AlchTheme.TEXT_PRIMARY);
		providerNameValueLabel.setForeground(AlchTheme.INFO_BLUE);

		rowsStack.add(AlchTheme.createKeyValueRow("Connection Status", connectionStateValueLabel, true));
		rowsStack.add(AlchTheme.createKeyValueRow("Last Update", lastUpdatedValueLabel, false));
		rowsStack.add(AlchTheme.createKeyValueRow("Nature Rune Price", natureRuneValueLabel, true));
		rowsStack.add(AlchTheme.createKeyValueRow("Items Analysed", analysedCountValueLabel, false));
		rowsStack.add(AlchTheme.createKeyValueRow("Data Source", providerNameValueLabel, true));

		card.add(topHeader);
		card.add(rowsStack);
		return card;
	}

	private JPanel buildPerformanceCardsSection()
	{
		JPanel section = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
		section.setOpaque(false);

		JLabel heading = AlchTheme.createSectionHeading("MARKET PERFORMANCE HIGHLIGHTS");
		section.add(heading);

		JPanel grid = new JPanel(new ResponsiveGridLayout(115, 4, 6, 6));
		grid.setOpaque(false);
		grid.add(cardMostProfitable);
		grid.add(cardBestRoi);
		grid.add(cardProfitableCount);
		grid.add(cardBestBulk);

		section.add(grid);
		return section;
	}

	private JPanel buildTopItemsSection()
	{
		JPanel wrapper = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_MD));
		wrapper.setOpaque(false);

		JPanel titleStack = new JPanel(new VerticalStackLayout(2));
		titleStack.setOpaque(false);
		JLabel title = AlchTheme.createSectionHeading("BEST ITEMS TO HIGH ALCH");
		JLabel sub = new JLabel("Top 10 verified profitable opportunities ranked by net profit per cast");
		sub.setFont(AlchTheme.getSupportFont(false));
		sub.setForeground(AlchTheme.TEXT_SECONDARY);
		titleStack.add(title);
		titleStack.add(sub);

		JButton viewAllBtn = AlchTheme.createPrimaryButton("VIEW ALL");
		viewAllBtn.setToolTipText("Open the full Live Alch Scanner");
		viewAllBtn.addActionListener(e -> navigationController.navigateTo(Section.ALCH_SCANNER));

		JPanel sectionHeader = AlchTheme.createSplitRow(titleStack, viewAllBtn);

		topItemsContainer.setOpaque(false);

		JButton bottomViewAllBtn = AlchTheme.createSecondaryButton("Explore All Supported OSRS Items in Scanner →");
		bottomViewAllBtn.addActionListener(e -> navigationController.navigateTo(Section.ALCH_SCANNER));

		wrapper.add(sectionHeader);
		wrapper.add(topItemsContainer);
		wrapper.add(bottomViewAllBtn);
		return wrapper;
	}

	/**
	 * Refreshes dashboard telemetry, performance cards, and top 10 items list.
	 */
	public void refreshView()
	{
		ProviderStatusSnapshot status = marketDataService.getStatusSnapshot();
		List<AlchMateItem> allItems = marketDataService.getAllAnalysedItems();

		if (status != null)
		{
			String stateLabel = status.getConnectionState().getLabel();
			statusBadgeLabel.setText(stateLabel);
			connectionStateValueLabel.setText(stateLabel);
			switch (status.getConnectionState())
			{
				case LIVE:
					statusBadgeLabel.setForeground(AlchTheme.SUCCESS_GREEN);
					statusBadgeLabel.setBackground(AlchTheme.SUCCESS_BG);
					connectionStateValueLabel.setForeground(AlchTheme.SUCCESS_GREEN);
					break;
				case CACHED:
				case DEGRADED_FALLBACK:
				case REFRESHING:
					statusBadgeLabel.setForeground(AlchTheme.ACCENT_GOLD);
					statusBadgeLabel.setBackground(AlchTheme.ACCENT_GOLD_DIM);
					connectionStateValueLabel.setForeground(AlchTheme.ACCENT_GOLD);
					break;
				case OFFLINE_ERROR:
				default:
					statusBadgeLabel.setForeground(AlchTheme.DANGER_RED);
					statusBadgeLabel.setBackground(AlchTheme.DANGER_BG);
					connectionStateValueLabel.setForeground(AlchTheme.DANGER_RED);
					break;
			}

			lastUpdatedValueLabel.setText(AlchTheme.formatEpochTimestamp(status.getLastSuccessfulUpdateEpochSec()));
			String natSourceTag = marketDataService.isUsingLiveNatureRunePrice() ? " (Live GE)" : " (Manual)";
			natureRuneValueLabel.setText(AlchTheme.formatGp(marketDataService.getEffectiveNatureRuneCost()) + natSourceTag);

			MarketPrice natMp = marketDataService.getNatureRuneMarketPrice();
			if (natMp != null)
			{
				natureRuneValueLabel.setToolTipText(String.format(
					"Nature Rune (ID 561) — Instant Buy: %s | Instant Sell: %s | 1h Avg: %s",
					natMp.getInstantBuyPrice() != null ? AlchTheme.formatGp(natMp.getInstantBuyPrice()) : "N/A",
					natMp.getInstantSellPrice() != null ? AlchTheme.formatGp(natMp.getInstantSellPrice()) : "N/A",
					natMp.getAvgHighPrice1h() != null ? AlchTheme.formatGp(natMp.getAvgHighPrice1h()) : "N/A"
				));
			}

			analysedCountValueLabel.setText(String.format(
				"%d valid / %d total",
				status.getValidPricesCount(),
				status.getTotalItemsLoaded()
			));
			providerNameValueLabel.setText(status.getActiveProviderName() != null
				? status.getActiveProviderName().replace("Real-Time Prices ", "")
				: "OSRS Wiki API");
		}

		List<AlchMateItem> cleanProfitable = allItems.stream()
			.filter(AlchMateItem::isProfitable)
			.filter(item -> !item.isStale())
			.filter(item -> !item.getWarnings().contains(MarketWarning.POSSIBLE_PRICE_MANIPULATION))
			.collect(Collectors.toList());

		List<AlchMateItem> candidatePool = !cleanProfitable.isEmpty()
			? cleanProfitable
			: allItems.stream().filter(AlchMateItem::isProfitable).collect(Collectors.toList());

		// 1. Most Profitable Item
		Optional<AlchMateItem> mostProfitable = candidatePool.stream()
			.max(Comparator.comparingLong(AlchMateItem::getNetProfitPerAlch));
		if (mostProfitable.isPresent())
		{
			AlchMateItem item = mostProfitable.get();
			cardMostProfitable.updateCard(
				AlchTheme.formatSignedGp(item.getNetProfitPerAlch()),
				item.getName(),
				AlchTheme.SUCCESS_GREEN
			);
		}
		else
		{
			cardMostProfitable.updateCard("0 GP", "No active margin", AlchTheme.TEXT_SECONDARY);
		}

		// 2. Best ROI
		Optional<AlchMateItem> bestRoi = candidatePool.stream()
			.filter(i -> i.getNetProfitPerAlch() >= 30)
			.max(Comparator.comparingDouble(AlchMateItem::getRoiPercent));
		if (bestRoi.isPresent())
		{
			AlchMateItem item = bestRoi.get();
			cardBestRoi.updateCard(
				AlchTheme.formatRoi(item.getRoiPercent()),
				item.getName(),
				AlchTheme.ACCENT_GOLD
			);
		}
		else
		{
			cardBestRoi.updateCard("0.0%", "No active ROI", AlchTheme.TEXT_SECONDARY);
		}

		// 3. Profitable Items
		long totalProfitableCount = allItems.stream().filter(AlchMateItem::isProfitable).count();
		cardProfitableCount.updateCard(
			totalProfitableCount + " items",
			cleanProfitable.size() + " fresh & verified",
			AlchTheme.SUCCESS_GREEN
		);

		// 4. Best Bulk Opportunity (4H)
		Optional<AlchMateItem> bestBulk = recommendationEngine.findBestBulkOpportunity(allItems);
		if (bestBulk.isPresent())
		{
			AlchMateItem item = bestBulk.get();
			long cycleProfit = item.getCalculation().getMaxBuyLimitProfit4h();
			cardBestBulk.updateCard(
				AlchTheme.formatCompactGp(cycleProfit) + " GP",
				item.getName() + " (4h limit)",
				AlchTheme.ACCENT_GOLD
			);
		}
		else
		{
			cardBestBulk.updateCard("0 GP", "Awaiting volume", AlchTheme.TEXT_SECONDARY);
		}

		// Section E: Top 10 Items List
		topItemsContainer.removeAll();
		List<AlchMateItem> top10 = candidatePool.stream()
			.sorted(Comparator.comparingLong(AlchMateItem::getNetProfitPerAlch).reversed())
			.limit(10)
			.collect(Collectors.toList());

		if (top10.isEmpty())
		{
			JPanel emptyCard = AlchTheme.createCardPanel();
			JLabel emptyLbl = new JLabel(
				marketDataService.isRefreshInProgress()
					? "Scanning live Grand Exchange market data..."
					: "No profitable items available yet. Click Refresh to sync."
			);
			emptyLbl.setFont(AlchTheme.getBodyFont(false));
			emptyLbl.setForeground(AlchTheme.TEXT_SECONDARY);
			emptyCard.add(emptyLbl, BorderLayout.CENTER);
			topItemsContainer.add(emptyCard);
		}
		else
		{
			int rank = 1;
			for (AlchMateItem item : top10)
			{
				topItemsContainer.add(AlchUiComponents.createAlchItemCard(
					rank++,
					item,
					iconService,
					null,
					navigationController,
					null
				));
			}
		}

		topItemsContainer.revalidate();
		topItemsContainer.repaint();
		revalidate();
		repaint();
	}
}
