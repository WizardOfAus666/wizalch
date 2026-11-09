package com.alchmatepro;

import com.alchmatepro.AlchUiComponents.ResponsiveGridLayout;
import com.alchmatepro.AlchUiComponents.VerticalStackLayout;
import com.alchmatepro.FilterCriteria.MembershipFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import net.runelite.client.util.QuantityFormatter;

/**
 * Live Alch Scanner and Advanced Filter interface (Sections 06, 07, 08).
 * Evaluates all supported OSRS items, ranks them across 8 sorting modes,
 * supports multi-criteria filtering, and provides both a rich Card View (with
 * expandable details) and a Financial Terminal Table View.
 */
public class AlchScannerPanel extends JPanel
{
	private static final int PAGE_SIZE = 35;

	private static final String[] MIN_PROFIT_PRESETS = {
		"Any profit", "100 GP", "250 GP", "500 GP", "1,000 GP", "2,500 GP", "5,000 GP", "Custom"
	};

	private static final String[] MIN_ROI_PRESETS = {
		"Any", "1%", "2%", "5%", "10%", "15%", "Custom"
	};

	private static final String[] TABLE_COLUMNS = {
		"#", "Item Name", "Profit/Alch", "ROI %", "Buy Price", "Alch Value", "Profit/Hr", "4h Limit", "1h Vol"
	};

	private final MarketDataService marketDataService;
	private final WatchlistService watchlistService;
	private final IconService iconService;
	private final AlchMateProConfig config;
	private final NavigationController navigationController;

	private final JTextField searchField = new JTextField();
	private final JComboBox<SortOption> sortCombo = new JComboBox<>(SortOption.values());
	private final JComboBox<String> categoryCombo = new JComboBox<>();

	private final JPanel filterDrawerPanel = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_XS));
	private final JButton toggleFiltersBtn = AlchTheme.createSecondaryButton("Filters [+]");
	private final JButton toggleViewModeBtn = AlchTheme.createSecondaryButton("Table");

	// Filter controls
	private final JComboBox<String> minProfitPresetCombo = new JComboBox<>(MIN_PROFIT_PRESETS);
	private final JSpinner customMinProfitSpinner = new JSpinner(new SpinnerNumberModel(100, -1000000, 10000000, 50));
	private final JComboBox<String> minRoiPresetCombo = new JComboBox<>(MIN_ROI_PRESETS);
	private final JSpinner customMinRoiSpinner = new JSpinner(new SpinnerNumberModel(2.0, -100.0, 500.0, 0.5));
	private final JSpinner maxPurchasePriceSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 2147483647, 5000));
	private final JSpinner minVolumeSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 1000000, 25));
	private final JSpinner minBuyLimitSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100000, 50));
	private final JComboBox<MembershipFilter> membershipCombo = new JComboBox<>(MembershipFilter.values());

	private final JCheckBox profitableOnlyCheck = new JCheckBox("Profitable only", true);
	private final JCheckBox hideLossesCheck = new JCheckBox("Hide losses", true);
	private final JCheckBox hideStaleCheck = new JCheckBox("Hide stale data", false);
	private final JCheckBox hideInactiveCheck = new JCheckBox("Hide inactive items", false);

	private final JLabel resultsSummaryLabel = new JLabel("Scanning...");
	private final JLabel activeNatBadge = AlchTheme.createBadge("Nat: 180 GP", AlchTheme.ACCENT_GOLD, AlchTheme.ACCENT_GOLD_DIM);
	private final JPanel itemsListContainer = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
	private final JButton prevPageBtn = AlchTheme.createSecondaryButton("← Prev");
	private final JButton nextPageBtn = AlchTheme.createSecondaryButton("Next →");
	private final JLabel pageIndicatorLabel = new JLabel("Page 1");

	private boolean tableViewMode = false;
	private int currentPage = 0;
	private List<AlchMateItem> currentPageItems = new ArrayList<>();

	public AlchScannerPanel(
		MarketDataService marketDataService,
		WatchlistService watchlistService,
		IconService iconService,
		AlchMateProConfig config,
		NavigationController navigationController)
	{
		super(new VerticalStackLayout(AlchTheme.SPACE_SM));
		this.marketDataService = marketDataService;
		this.watchlistService = watchlistService;
		this.iconService = iconService;
		this.config = config;
		this.navigationController = navigationController;

		setBackground(AlchTheme.BG_MAIN);
		setBorder(BorderFactory.createEmptyBorder(
			AlchTheme.SPACE_SM, AlchTheme.SPACE_SM, AlchTheme.SPACE_MD, AlchTheme.SPACE_SM
		));

		add(buildControlsHeader());
		add(buildResultsListSection());

		applyConfigDefaultsToControls();
	}

	private JPanel buildControlsHeader()
	{
		JPanel headerCard = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
		headerCard.setBackground(AlchTheme.BG_CARD);
		headerCard.setBorder(AlchTheme.createCardBorder());

		JLabel title = AlchTheme.createSectionHeading("LIVE ALCH SCANNER & FILTERS");
		headerCard.add(title);

		// 1. Search bar row
		AlchTheme.styleTextField(searchField);
		searchField.setToolTipText("Search OSRS items by name or item ID");
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				onFilterOrSortChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				onFilterOrSortChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				onFilterOrSortChanged();
			}
		});

		JButton clearSearchBtn = AlchTheme.createSecondaryButton("Clear");
		clearSearchBtn.setToolTipText("Clear search box");
		clearSearchBtn.addActionListener(e -> searchField.setText(""));

		JPanel searchInputGroup = AlchTheme.createSplitRow(searchField, clearSearchBtn);
		headerCard.add(AlchTheme.createFormControlRow("Search Item", searchInputGroup));

		// 2. Sort & Category selectors
		AlchTheme.styleComboBox(sortCombo);
		sortCombo.addActionListener(e -> onFilterOrSortChanged());

		categoryCombo.addItem("All Categories (Smart Rank)");
		for (RecommendationCategory cat : RecommendationCategory.values())
		{
			categoryCombo.addItem("★ " + cat.getTitle());
		}
		AlchTheme.styleComboBox(categoryCombo);
		categoryCombo.addActionListener(e -> onFilterOrSortChanged());

		headerCard.add(AlchTheme.createFormControlRow("Sort Order", sortCombo));
		headerCard.add(AlchTheme.createFormControlRow("Category", categoryCombo));

		// 3. Action bar: Toggle Filters + Toggle Table/Cards + Reset + Rescan (Single Row)
		JPanel actionBar = new JPanel(new java.awt.GridLayout(1, 4, 4, 0));
		actionBar.setOpaque(false);

		toggleFiltersBtn.addActionListener(e -> {
			boolean visible = !filterDrawerPanel.isVisible();
			filterDrawerPanel.setVisible(visible);
			toggleFiltersBtn.setText(visible ? "Filters [-]" : "Filters [+]");
			revalidate();
			repaint();
		});

		toggleViewModeBtn.setToolTipText("Switch between expandable item cards and financial table view");
		toggleViewModeBtn.addActionListener(e -> {
			tableViewMode = !tableViewMode;
			toggleViewModeBtn.setText(tableViewMode ? "Cards" : "Table");
			refreshView();
		});

		JButton resetBtn = AlchTheme.createSecondaryButton("Reset");
		resetBtn.setToolTipText("Reset all scanner filters to default");
		resetBtn.addActionListener(e -> resetFilters());

		JButton scanNowBtn = AlchTheme.createPrimaryButton("Rescan");
		scanNowBtn.setToolTipText("Refresh market prices from live data provider");
		scanNowBtn.addActionListener(e -> marketDataService.refreshAsync());

		actionBar.add(toggleFiltersBtn);
		actionBar.add(toggleViewModeBtn);
		actionBar.add(resetBtn);
		actionBar.add(scanNowBtn);
		headerCard.add(actionBar);

		// 4. Collapsible Filter Drawer
		buildFilterDrawer();
		filterDrawerPanel.setVisible(false);
		headerCard.add(filterDrawerPanel);

		return headerCard;
	}

	private void buildFilterDrawer()
	{
		filterDrawerPanel.setBackground(AlchTheme.BG_PANEL);
		filterDrawerPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(AlchTheme.BORDER_DEFAULT, 1),
			BorderFactory.createEmptyBorder(AlchTheme.SPACE_MD, AlchTheme.SPACE_MD, AlchTheme.SPACE_MD, AlchTheme.SPACE_MD)
		));

		AlchTheme.styleComboBox(minProfitPresetCombo);
		AlchTheme.styleSpinner(customMinProfitSpinner);
		customMinProfitSpinner.setEnabled(false);
		minProfitPresetCombo.addActionListener(e -> {
			boolean isCustom = "Custom".equals(minProfitPresetCombo.getSelectedItem());
			customMinProfitSpinner.setEnabled(isCustom);
			onFilterOrSortChanged();
		});
		customMinProfitSpinner.addChangeListener(e -> onFilterOrSortChanged());

		AlchTheme.styleComboBox(minRoiPresetCombo);
		AlchTheme.styleSpinner(customMinRoiSpinner);
		customMinRoiSpinner.setEnabled(false);
		minRoiPresetCombo.addActionListener(e -> {
			boolean isCustom = "Custom".equals(minRoiPresetCombo.getSelectedItem());
			customMinRoiSpinner.setEnabled(isCustom);
			onFilterOrSortChanged();
		});
		customMinRoiSpinner.addChangeListener(e -> onFilterOrSortChanged());

		AlchTheme.styleSpinner(maxPurchasePriceSpinner);
		maxPurchasePriceSpinner.addChangeListener(e -> onFilterOrSortChanged());

		AlchTheme.styleSpinner(minVolumeSpinner);
		minVolumeSpinner.addChangeListener(e -> onFilterOrSortChanged());

		AlchTheme.styleSpinner(minBuyLimitSpinner);
		minBuyLimitSpinner.addChangeListener(e -> onFilterOrSortChanged());

		AlchTheme.styleComboBox(membershipCombo);
		membershipCombo.addActionListener(e -> onFilterOrSortChanged());

		filterDrawerPanel.add(AlchTheme.createFormControlRow("Min Profit Preset", minProfitPresetCombo));
		filterDrawerPanel.add(AlchTheme.createFormControlRow("Custom Min Profit (GP)", customMinProfitSpinner));
		filterDrawerPanel.add(AlchTheme.createFormControlRow("Min ROI % Preset", minRoiPresetCombo));
		filterDrawerPanel.add(AlchTheme.createFormControlRow("Custom Min ROI %", customMinRoiSpinner));
		filterDrawerPanel.add(AlchTheme.createFormControlRow("Max Buy Price (GP)", maxPurchasePriceSpinner));
		filterDrawerPanel.add(AlchTheme.createFormControlRow("Min 1h Trade Volume", minVolumeSpinner));
		filterDrawerPanel.add(AlchTheme.createFormControlRow("Min 4h Buy Limit", minBuyLimitSpinner));
		filterDrawerPanel.add(AlchTheme.createFormControlRow("Membership", membershipCombo));

		JPanel checkGrid = new JPanel(new ResponsiveGridLayout(140, 2, AlchTheme.SPACE_SM, AlchTheme.SPACE_XS));
		checkGrid.setOpaque(false);
		checkGrid.setBorder(BorderFactory.createEmptyBorder(AlchTheme.SPACE_SM, 0, 0, 0));

		styleFilterCheckBox(profitableOnlyCheck);
		styleFilterCheckBox(hideLossesCheck);
		styleFilterCheckBox(hideStaleCheck);
		styleFilterCheckBox(hideInactiveCheck);

		checkGrid.add(profitableOnlyCheck);
		checkGrid.add(hideLossesCheck);
		checkGrid.add(hideStaleCheck);
		checkGrid.add(hideInactiveCheck);

		filterDrawerPanel.add(checkGrid);
	}

	private JPanel buildResultsListSection()
	{
		JPanel wrapper = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
		wrapper.setOpaque(false);

		resultsSummaryLabel.setFont(AlchTheme.getBodyFont(true));
		resultsSummaryLabel.setForeground(AlchTheme.TEXT_SECONDARY);
		JPanel statusRow = AlchTheme.createSplitRow(resultsSummaryLabel, activeNatBadge);

		itemsListContainer.setOpaque(false);

		JPanel paginationBar = new JPanel(new BorderLayout(AlchTheme.SPACE_SM, 0));
		paginationBar.setBackground(AlchTheme.BG_CARD);
		paginationBar.setBorder(AlchTheme.createCardBorder());

		prevPageBtn.addActionListener(e -> {
			if (currentPage > 0)
			{
				currentPage--;
				refreshView();
			}
		});
		nextPageBtn.addActionListener(e -> {
			currentPage++;
			refreshView();
		});
		pageIndicatorLabel.setHorizontalAlignment(SwingConstants.CENTER);
		pageIndicatorLabel.setFont(AlchTheme.getBodyFont(true));
		pageIndicatorLabel.setForeground(AlchTheme.TEXT_PRIMARY);

		paginationBar.add(prevPageBtn, BorderLayout.WEST);
		paginationBar.add(pageIndicatorLabel, BorderLayout.CENTER);
		paginationBar.add(nextPageBtn, BorderLayout.EAST);

		wrapper.add(statusRow);
		wrapper.add(itemsListContainer);
		wrapper.add(paginationBar);
		return wrapper;
	}

	private void applyConfigDefaultsToControls()
	{
		if (config == null)
		{
			return;
		}
		if (config.defaultSorting() != null)
		{
			sortCombo.setSelectedItem(config.defaultSorting());
		}
		minVolumeSpinner.setValue(Math.max(0, config.minimumTradeVolume()));
		maxPurchasePriceSpinner.setValue(Math.max(0, config.maximumPurchaseCost()));
		profitableOnlyCheck.setSelected(!config.showUnprofitableItems());
		hideLossesCheck.setSelected(!config.showUnprofitableItems());
	}

	public void resetFilters()
	{
		searchField.setText("");
		minProfitPresetCombo.setSelectedIndex(0);
		customMinProfitSpinner.setValue(0);
		minRoiPresetCombo.setSelectedIndex(0);
		customMinRoiSpinner.setValue(0.0);
		maxPurchasePriceSpinner.setValue(0);
		minVolumeSpinner.setValue(0);
		minBuyLimitSpinner.setValue(0);
		membershipCombo.setSelectedItem(MembershipFilter.ALL);
		profitableOnlyCheck.setSelected(false);
		hideLossesCheck.setSelected(false);
		hideStaleCheck.setSelected(false);
		hideInactiveCheck.setSelected(false);
		categoryCombo.setSelectedIndex(0);
		sortCombo.setSelectedItem(SortOption.HIGHEST_PROFIT_PER_ALCH);
		currentPage = 0;
		refreshView();
	}

	private void onFilterOrSortChanged()
	{
		currentPage = 0;
		refreshView();
	}

	public FilterCriteria buildCurrentFilterCriteria()
	{
		Long minProfit = resolveSelectedMinProfit();
		Double minRoi = resolveSelectedMinRoi();
		long maxPriceVal = ((Number) maxPurchasePriceSpinner.getValue()).longValue();
		int minVol = ((Number) minVolumeSpinner.getValue()).intValue();
		int minLimit = ((Number) minBuyLimitSpinner.getValue()).intValue();

		RecommendationCategory selectedCat = null;
		int catIdx = categoryCombo.getSelectedIndex();
		if (catIdx > 0 && catIdx <= RecommendationCategory.values().length)
		{
			selectedCat = RecommendationCategory.values()[catIdx - 1];
		}

		return FilterCriteria.builder()
			.searchQuery(searchField.getText())
			.minProfitGp(minProfit)
			.minRoiPercent(minRoi)
			.maxPurchasePriceGp(maxPriceVal > 0 ? maxPriceVal : null)
			.minTradeVolume1h(Math.max(0, minVol))
			.minBuyLimit(Math.max(0, minLimit))
			.membershipFilter((MembershipFilter) membershipCombo.getSelectedItem())
			.profitableOnly(profitableOnlyCheck.isSelected())
			.hideLosses(hideLossesCheck.isSelected())
			.hideStaleData(hideStaleCheck.isSelected())
			.hideInactiveItems(hideInactiveCheck.isSelected())
			.categoryFilter(selectedCat)
			.sortOption((SortOption) sortCombo.getSelectedItem())
			.build();
	}

	private Long resolveSelectedMinProfit()
	{
		String selected = (String) minProfitPresetCombo.getSelectedItem();
		if (selected == null)
		{
			return null;
		}
		switch (selected)
		{
			case "Any profit":
				return profitableOnlyCheck.isSelected() ? 1L : null;
			case "100 GP":
				return 100L;
			case "250 GP":
				return 250L;
			case "500 GP":
				return 500L;
			case "1,000 GP":
				return 1000L;
			case "2,500 GP":
				return 2500L;
			case "5,000 GP":
				return 5000L;
			case "Custom":
				return ((Number) customMinProfitSpinner.getValue()).longValue();
			default:
				return null;
		}
	}

	private Double resolveSelectedMinRoi()
	{
		String selected = (String) minRoiPresetCombo.getSelectedItem();
		if (selected == null || "Any".equals(selected))
		{
			return null;
		}
		switch (selected)
		{
			case "1%":
				return 1.0;
			case "2%":
				return 2.0;
			case "5%":
				return 5.0;
			case "10%":
				return 10.0;
			case "15%":
				return 15.0;
			case "Custom":
				return ((Number) customMinRoiSpinner.getValue()).doubleValue();
			default:
				return null;
		}
	}

	public void refreshView()
	{
		activeNatBadge.setText("Nat: " + AlchTheme.formatGp(marketDataService.getEffectiveNatureRuneCost()));

		FilterCriteria criteria = buildCurrentFilterCriteria();
		List<AlchMateItem> matchingItems = marketDataService.filterAndSort(criteria);

		itemsListContainer.removeAll();

		if (marketDataService.isRefreshInProgress() && marketDataService.getAllAnalysedItems().isEmpty())
		{
			resultsSummaryLabel.setText("Scanning OSRS Grand Exchange prices...");
			JPanel loadingCard = AlchTheme.createCardPanel();
			JLabel loadingLbl = new JLabel("Loading live item metadata and market prices...");
			loadingLbl.setFont(AlchTheme.getBodyFont(false));
			loadingLbl.setForeground(AlchTheme.ACCENT_GOLD);
			loadingCard.add(loadingLbl, BorderLayout.CENTER);
			itemsListContainer.add(loadingCard);
			prevPageBtn.setEnabled(false);
			nextPageBtn.setEnabled(false);
			pageIndicatorLabel.setText("Scanning...");
			revalidate();
			repaint();
			return;
		}

		if (matchingItems.isEmpty())
		{
			resultsSummaryLabel.setText("0 matching items");
			JPanel emptyCard = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
			emptyCard.setBackground(AlchTheme.BG_CARD);
			emptyCard.setBorder(AlchTheme.createSpaciousCardBorder());

			JLabel emptyTitle = new JLabel("No Matching Items Found");
			emptyTitle.setFont(AlchTheme.getSectionHeadingFont());
			emptyTitle.setForeground(AlchTheme.ACCENT_GOLD);

			JLabel emptyHint = new JLabel("Try relaxing your minimum profit/ROI filters or resetting search criteria.");
			emptyHint.setFont(AlchTheme.getBodyFont(false));
			emptyHint.setForeground(AlchTheme.TEXT_SECONDARY);

			JButton resetEmptyBtn = AlchTheme.createSecondaryButton("Reset All Filters");
			resetEmptyBtn.addActionListener(e -> resetFilters());

			emptyCard.add(emptyTitle);
			emptyCard.add(emptyHint);
			emptyCard.add(resetEmptyBtn);

			itemsListContainer.add(emptyCard);
			prevPageBtn.setEnabled(false);
			nextPageBtn.setEnabled(false);
			pageIndicatorLabel.setText("0 / 0");
			revalidate();
			repaint();
			return;
		}

		int total = matchingItems.size();
		int totalPages = Math.max(1, (int) Math.ceil((double) total / PAGE_SIZE));
		if (currentPage >= totalPages)
		{
			currentPage = totalPages - 1;
		}
		int startIdx = currentPage * PAGE_SIZE;
		int endIdx = Math.min(total, startIdx + PAGE_SIZE);

		resultsSummaryLabel.setText(String.format("Showing %d–%d of %d items", startIdx + 1, endIdx, total));
		prevPageBtn.setEnabled(currentPage > 0);
		nextPageBtn.setEnabled(currentPage < totalPages - 1);
		pageIndicatorLabel.setText(String.format("Page %d / %d", currentPage + 1, totalPages));

		this.currentPageItems = matchingItems.subList(startIdx, endIdx);

		if (tableViewMode)
		{
			itemsListContainer.add(buildFinancialTablePanel(startIdx, currentPageItems));
		}
		else
		{
			for (int i = 0; i < currentPageItems.size(); i++)
			{
				AlchMateItem item = currentPageItems.get(i);
				itemsListContainer.add(AlchUiComponents.createAlchItemCard(
					startIdx + i + 1,
					item,
					iconService,
					watchlistService,
					navigationController,
					this::refreshView
				));
			}
		}

		itemsListContainer.revalidate();
		itemsListContainer.repaint();
		revalidate();
		repaint();
	}

	private JPanel buildFinancialTablePanel(int startIdx, List<AlchMateItem> slice)
	{
		DefaultTableModel model = new DefaultTableModel(TABLE_COLUMNS, 0)
		{
			@Override
			public boolean isCellEditable(int row, int column)
			{
				return false;
			}
		};

		for (int i = 0; i < slice.size(); i++)
		{
			AlchMateItem item = slice.get(i);
			long profit = item.getNetProfitPerAlch();
			model.addRow(new Object[]{
				startIdx + i + 1,
				item.getName(),
				AlchTheme.formatSignedGp(profit),
				AlchTheme.formatRoi(item.getRoiPercent()),
				AlchTheme.formatGp(item.getEstimatedPurchasePrice()),
				AlchTheme.formatGp(item.getHighAlchValue()),
				AlchTheme.formatHourlyProfit(profit),
				item.getBuyLimit() > 0 ? QuantityFormatter.formatNumber(item.getBuyLimit()) : "-",
				QuantityFormatter.formatNumber(item.getVolume1h())
			});
		}

		JTable table = new JTable(model);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		AlchUiComponents.styleFinancialTable(table, new int[]{0, 2, 3, 4, 5, 6, 7, 8}, 2);

		int[] colWidths = {44, 175, 110, 75, 110, 110, 125, 80, 80};
		for (int c = 0; c < colWidths.length && c < table.getColumnCount(); c++)
		{
			table.getColumnModel().getColumn(c).setPreferredWidth(colWidths[c]);
			table.getColumnModel().getColumn(c).setMinWidth(40);
		}

		table.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				int row = table.getSelectedRow();
				if (row >= 0 && row < slice.size())
				{
					navigationController.openItemAnalysis(slice.get(row));
				}
			}
		});

		JScrollPane tableScroll = new JScrollPane(table);
		tableScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		tableScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		AlchTheme.styleScrollPane(tableScroll);
		tableScroll.setBorder(BorderFactory.createLineBorder(AlchTheme.BORDER_DEFAULT, 1));
		int tableHeight = Math.min(540, 38 + slice.size() * 34);
		tableScroll.setPreferredSize(new Dimension(320, tableHeight));

		JPanel container = new JPanel(new BorderLayout(0, AlchTheme.SPACE_XS));
		container.setOpaque(false);
		JLabel hint = new JLabel("Click any row to open full Item Analysis • Scroll horizontally for all columns");
		hint.setFont(AlchTheme.getSupportFont(false));
		hint.setForeground(AlchTheme.TEXT_MUTED);
		container.add(hint, BorderLayout.NORTH);
		container.add(tableScroll, BorderLayout.CENTER);
		return container;
	}

	private void styleFilterCheckBox(JCheckBox cb)
	{
		AlchTheme.styleCheckBox(cb);
		cb.addActionListener(e -> onFilterOrSortChanged());
	}
}
