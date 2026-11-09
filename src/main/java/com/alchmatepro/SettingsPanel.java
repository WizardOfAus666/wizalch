package com.alchmatepro;

import com.alchmatepro.AlchUiComponents.ResponsiveGridLayout;
import com.alchmatepro.AlchUiComponents.VerticalStackLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import net.runelite.client.config.ConfigManager;

/**
 * Dedicated in-panel Settings editor (Section 16).
 * Synchronises all user preferences persistently with RuneLite's {@link ConfigManager}
 * and immediately applies runtime updates to {@link MarketDataService}.
 */
public class SettingsPanel extends JPanel
{
	private final AlchMateProConfig config;
	private final ConfigManager configManager;
	private final MarketDataService marketDataService;

	private final JLabel liveNatureRuneInfoLabel = new JLabel("Live GE Nature Rune (ID 561): Fetching...");
	private final JCheckBox useLiveNatureRuneCheck = new JCheckBox("Auto-use Live Nature Rune Price", true);
	private final JSpinner natureRuneCostSpinner = new JSpinner(new SpinnerNumberModel(180, 0, 10000, 1));
	private final JCheckBox useFireStaffCheck = new JCheckBox("Fire Staff Equipped (0 Fire Rune Cost)", true);
	private final JSpinner fireRuneCostSpinner = new JSpinner(new SpinnerNumberModel(20, 0, 5000, 1));

	private final JComboBox<PriceCalculationMethod> priceMethodCombo = new JComboBox<>(PriceCalculationMethod.values());
	private final JSpinner refreshIntervalSpinner = new JSpinner(new SpinnerNumberModel(60, 30, 900, 15));
	private final JSpinner maxDataAgeSpinner = new JSpinner(new SpinnerNumberModel(120, 5, 1440, 15));

	private final JSpinner minProfitSpinner = new JSpinner(new SpinnerNumberModel(1, -1000000, 10000000, 50));
	private final JSpinner minRoiSpinner = new JSpinner(new SpinnerNumberModel(0, -100, 1000, 1));
	private final JSpinner minVolumeSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 1000000, 25));
	private final JSpinner maxPurchaseCostSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 2147483647, 10000));
	private final JSpinner defaultQuantitySpinner = new JSpinner(new SpinnerNumberModel(100, 1, 1000000, 25));
	private final JComboBox<SortOption> defaultSortCombo = new JComboBox<>(SortOption.values());

	private final JCheckBox enableNotificationsCheck = new JCheckBox("Enable Profit Notifications", true);
	private final JCheckBox enableAutoRefreshCheck = new JCheckBox("Enable Automatic Background Refresh", true);
	private final JCheckBox showMembersCheck = new JCheckBox("Show Members (P2P) Items", true);
	private final JCheckBox showUnprofitableCheck = new JCheckBox("Show Unprofitable Items", true);

	private final JLabel statusFeedbackLabel = new JLabel("Settings synced with live market engine.");

	public SettingsPanel(
		AlchMateProConfig config,
		ConfigManager configManager,
		MarketDataService marketDataService)
	{
		super(new VerticalStackLayout(AlchTheme.SPACE_SM));
		this.config = config;
		this.configManager = configManager;
		this.marketDataService = marketDataService;

		setBackground(AlchTheme.BG_MAIN);
		setBorder(BorderFactory.createEmptyBorder(
			AlchTheme.SPACE_SM, AlchTheme.SPACE_SM, AlchTheme.SPACE_MD, AlchTheme.SPACE_SM
		));

		add(buildRuneAndPriceCard());
		add(buildScannerDefaultsCard());
		add(buildTogglesAndSaveCard());

		loadFromConfig();
	}

	private JPanel buildRuneAndPriceCard()
	{
		JPanel card = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
		card.setBackground(AlchTheme.BG_CARD);
		card.setBorder(AlchTheme.createCardBorder());

		JLabel title = AlchTheme.createSectionHeading("RUNE COSTS & PRICING ENGINE");
		card.add(title);

		JPanel liveNatBanner = new JPanel(new VerticalStackLayout(2));
		liveNatBanner.setBackground(AlchTheme.SUCCESS_BG);
		liveNatBanner.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(AlchTheme.SUCCESS_GREEN, 1),
			BorderFactory.createEmptyBorder(AlchTheme.SPACE_SM, AlchTheme.SPACE_SM, AlchTheme.SPACE_SM, AlchTheme.SPACE_SM)
		));
		liveNatureRuneInfoLabel.setFont(AlchTheme.getBodyFont(true));
		liveNatureRuneInfoLabel.setForeground(AlchTheme.SUCCESS_GREEN);
		liveNatBanner.add(liveNatureRuneInfoLabel);
		card.add(liveNatBanner);

		AlchTheme.styleCheckBox(useLiveNatureRuneCheck);
		useLiveNatureRuneCheck.addActionListener(e -> {
			natureRuneCostSpinner.setEnabled(!useLiveNatureRuneCheck.isSelected());
			if (useLiveNatureRuneCheck.isSelected())
			{
				ProviderStatusSnapshot snap = marketDataService.getStatusSnapshot();
				if (snap != null && snap.getNatureRuneLivePrice() > 0)
				{
					natureRuneCostSpinner.setValue((int) snap.getNatureRuneLivePrice());
				}
			}
			saveToConfig();
		});

		AlchTheme.styleCheckBox(useFireStaffCheck);
		useFireStaffCheck.addActionListener(e -> {
			fireRuneCostSpinner.setEnabled(!useFireStaffCheck.isSelected());
			saveToConfig();
		});

		AlchTheme.styleSpinner(natureRuneCostSpinner);
		AlchTheme.styleSpinner(fireRuneCostSpinner);
		AlchTheme.styleComboBox(priceMethodCombo);
		AlchTheme.styleSpinner(refreshIntervalSpinner);
		AlchTheme.styleSpinner(maxDataAgeSpinner);

		priceMethodCombo.addActionListener(e -> saveToConfig());

		card.add(useLiveNatureRuneCheck);
		card.add(useFireStaffCheck);
		card.add(AlchTheme.createFormControlRow("Manual Nature Rune (GP)", natureRuneCostSpinner));
		card.add(AlchTheme.createFormControlRow("Fire Runes / Cast (GP)", fireRuneCostSpinner));
		card.add(AlchTheme.createFormControlRow("Price Method", priceMethodCombo));
		card.add(AlchTheme.createFormControlRow("Refresh Interval (sec)", refreshIntervalSpinner));
		card.add(AlchTheme.createFormControlRow("Max Data Age (min)", maxDataAgeSpinner));
		return card;
	}

	private JPanel buildScannerDefaultsCard()
	{
		JPanel card = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
		card.setBackground(AlchTheme.BG_CARD);
		card.setBorder(AlchTheme.createCardBorder());

		JLabel title = AlchTheme.createSectionHeading("SCANNER & CALCULATOR DEFAULTS");
		card.add(title);

		AlchTheme.styleSpinner(minProfitSpinner);
		AlchTheme.styleSpinner(minRoiSpinner);
		AlchTheme.styleSpinner(minVolumeSpinner);
		AlchTheme.styleSpinner(maxPurchaseCostSpinner);
		AlchTheme.styleSpinner(defaultQuantitySpinner);
		AlchTheme.styleComboBox(defaultSortCombo);

		card.add(AlchTheme.createFormControlRow("Minimum Profit (GP)", minProfitSpinner));
		card.add(AlchTheme.createFormControlRow("Minimum ROI (%)", minRoiSpinner));
		card.add(AlchTheme.createFormControlRow("Minimum 1h Volume", minVolumeSpinner));
		card.add(AlchTheme.createFormControlRow("Max Buy Price (0=Any)", maxPurchaseCostSpinner));
		card.add(AlchTheme.createFormControlRow("Default Quantity", defaultQuantitySpinner));
		card.add(AlchTheme.createFormControlRow("Default Sorting", defaultSortCombo));
		return card;
	}

	private JPanel buildTogglesAndSaveCard()
	{
		JPanel card = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
		card.setBackground(AlchTheme.BG_CARD);
		card.setBorder(AlchTheme.createGoldCardBorder());

		JLabel title = AlchTheme.createSectionHeading("PREFERENCES & PERSISTENCE");
		card.add(title);

		AlchTheme.styleCheckBox(enableNotificationsCheck);
		AlchTheme.styleCheckBox(enableAutoRefreshCheck);
		AlchTheme.styleCheckBox(showMembersCheck);
		AlchTheme.styleCheckBox(showUnprofitableCheck);

		card.add(enableNotificationsCheck);
		card.add(enableAutoRefreshCheck);
		card.add(showMembersCheck);
		card.add(showUnprofitableCheck);

		JPanel btnRow = new JPanel(new ResponsiveGridLayout(120, 2, AlchTheme.SPACE_SM, AlchTheme.SPACE_XS));
		btnRow.setOpaque(false);

		JButton saveBtn = AlchTheme.createPrimaryButton("Save & Apply");
		saveBtn.addActionListener(e -> saveToConfig());

		JButton resetDefaultsBtn = AlchTheme.createSecondaryButton("Reset Defaults");
		resetDefaultsBtn.addActionListener(e -> resetToDefaults());

		btnRow.add(saveBtn);
		btnRow.add(resetDefaultsBtn);
		card.add(btnRow);

		statusFeedbackLabel.setFont(AlchTheme.getBodyFont(true));
		statusFeedbackLabel.setForeground(AlchTheme.SUCCESS_GREEN);
		card.add(statusFeedbackLabel);
		return card;
	}

	public final void loadFromConfig()
	{
		if (marketDataService == null)
		{
			return;
		}
		ProviderStatusSnapshot snap = marketDataService.getStatusSnapshot();
		if (snap != null && snap.getNatureRuneLivePrice() > 0)
		{
			MarketPrice natMp = marketDataService.getNatureRuneMarketPrice();
			if (natMp != null && natMp.getInstantBuyPrice() != null)
			{
				liveNatureRuneInfoLabel.setText(String.format(
					"Live GE Nature Rune: %s (Sell: %s)",
					AlchTheme.formatGp(natMp.getInstantBuyPrice()),
					natMp.getInstantSellPrice() != null ? AlchTheme.formatGp(natMp.getInstantSellPrice()) : "N/A"
				));
			}
			else
			{
				liveNatureRuneInfoLabel.setText("Live GE Nature Rune: " + AlchTheme.formatGp(snap.getNatureRuneLivePrice()));
			}
		}

		boolean useLiveNat = marketDataService.isRuntimeUseLiveNatureRune();
		useLiveNatureRuneCheck.setSelected(useLiveNat);
		natureRuneCostSpinner.setEnabled(!useLiveNat);
		if (useLiveNat)
		{
			natureRuneCostSpinner.setValue((int) marketDataService.getEffectiveNatureRuneCost());
		}
		else
		{
			natureRuneCostSpinner.setValue(marketDataService.getRuntimeManualNatureRuneCost());
		}

		boolean useFireStaff = marketDataService.isRuntimeUseFireStaff();
		useFireStaffCheck.setSelected(useFireStaff);
		fireRuneCostSpinner.setEnabled(!useFireStaff);
		fireRuneCostSpinner.setValue(marketDataService.getRuntimeManualFireRuneCost());

		priceMethodCombo.setSelectedItem(marketDataService.getRuntimePriceMethod());
		refreshIntervalSpinner.setValue(marketDataService.getRuntimeRefreshIntervalSec());
		maxDataAgeSpinner.setValue(marketDataService.getRuntimeMaxDataAgeMin());

		minProfitSpinner.setValue(marketDataService.getRuntimeMinProfit());
		minRoiSpinner.setValue(marketDataService.getRuntimeMinRoi());
		minVolumeSpinner.setValue(marketDataService.getRuntimeMinVolume());
		maxPurchaseCostSpinner.setValue(marketDataService.getRuntimeMaxPurchaseCost());
		defaultQuantitySpinner.setValue(marketDataService.getRuntimeDefaultQuantity());
		defaultSortCombo.setSelectedItem(marketDataService.getRuntimeDefaultSorting());

		enableNotificationsCheck.setSelected(marketDataService.isRuntimeEnableNotifications());
		enableAutoRefreshCheck.setSelected(marketDataService.isRuntimeEnableAutoRefresh());
		showMembersCheck.setSelected(marketDataService.isRuntimeShowMembers());
		showUnprofitableCheck.setSelected(marketDataService.isRuntimeShowUnprofitable());
	}

	public void saveToConfig()
	{
		boolean useLiveNat = useLiveNatureRuneCheck.isSelected();
		int manualNatCost = ((Number) natureRuneCostSpinner.getValue()).intValue();
		boolean useFireStaff = useFireStaffCheck.isSelected();
		int manualFireCost = ((Number) fireRuneCostSpinner.getValue()).intValue();
		PriceCalculationMethod method = (PriceCalculationMethod) priceMethodCombo.getSelectedItem();
		int refreshSec = ((Number) refreshIntervalSpinner.getValue()).intValue();
		int maxAgeMin = ((Number) maxDataAgeSpinner.getValue()).intValue();
		int minProfit = ((Number) minProfitSpinner.getValue()).intValue();
		int minRoi = ((Number) minRoiSpinner.getValue()).intValue();
		int minVol = ((Number) minVolumeSpinner.getValue()).intValue();
		int maxCost = ((Number) maxPurchaseCostSpinner.getValue()).intValue();
		int defaultQty = ((Number) defaultQuantitySpinner.getValue()).intValue();
		SortOption defaultSort = (SortOption) defaultSortCombo.getSelectedItem();
		boolean notify = enableNotificationsCheck.isSelected();
		boolean autoRefresh = enableAutoRefreshCheck.isSelected();
		boolean showMembers = showMembersCheck.isSelected();
		boolean showUnprofitable = showUnprofitableCheck.isSelected();

		if (configManager != null)
		{
			String g = AlchMateProConfig.CONFIG_GROUP;
			configManager.setConfiguration(g, "useLiveNatureRunePrice", useLiveNat);
			configManager.setConfiguration(g, "natureRuneCost", manualNatCost);
			configManager.setConfiguration(g, "useFireStaff", useFireStaff);
			configManager.setConfiguration(g, "fireRuneCostPerCast", manualFireCost);

			configManager.setConfiguration(g, "priceCalculationMethod", method);
			configManager.setConfiguration(g, "refreshIntervalSeconds", refreshSec);
			configManager.setConfiguration(g, "maxDataAgeMinutes", maxAgeMin);

			configManager.setConfiguration(g, "minimumProfit", minProfit);
			configManager.setConfiguration(g, "minimumRoiPercent", minRoi);
			configManager.setConfiguration(g, "minimumTradeVolume", minVol);
			configManager.setConfiguration(g, "maximumPurchaseCost", maxCost);
			configManager.setConfiguration(g, "defaultQuantity", defaultQty);
			configManager.setConfiguration(g, "defaultSorting", defaultSort);

			configManager.setConfiguration(g, "enableNotifications", notify);
			configManager.setConfiguration(g, "enableAutoRefresh", autoRefresh);
			configManager.setConfiguration(g, "showMembersItems", showMembers);
			configManager.setConfiguration(g, "showUnprofitableItems", showUnprofitable);
		}

		marketDataService.applyRuntimeSettings(
			useLiveNat,
			manualNatCost,
			useFireStaff,
			manualFireCost,
			method,
			refreshSec,
			maxAgeMin,
			minProfit,
			minRoi,
			minVol,
			maxCost,
			defaultQty,
			defaultSort,
			notify,
			autoRefresh,
			showMembers,
			showUnprofitable
		);

		statusFeedbackLabel.setText(String.format(
			"✓ Applied! Active Nature Rune Cost: %s (%s)",
			AlchTheme.formatGp(marketDataService.getEffectiveNatureRuneCost()),
			marketDataService.isUsingLiveNatureRunePrice() ? "Live GE" : "Manual"
		));
	}

	private void resetToDefaults()
	{
		useLiveNatureRuneCheck.setSelected(true);
		natureRuneCostSpinner.setEnabled(false);
		natureRuneCostSpinner.setValue((int) marketDataService.getEffectiveNatureRuneCost());
		useFireStaffCheck.setSelected(true);
		fireRuneCostSpinner.setEnabled(false);
		fireRuneCostSpinner.setValue(20);
		priceMethodCombo.setSelectedItem(PriceCalculationMethod.INSTANT_BUY_HIGH);
		refreshIntervalSpinner.setValue(60);
		maxDataAgeSpinner.setValue(120);
		minProfitSpinner.setValue(1);
		minRoiSpinner.setValue(0);
		minVolumeSpinner.setValue(0);
		maxPurchaseCostSpinner.setValue(0);
		defaultQuantitySpinner.setValue(100);
		defaultSortCombo.setSelectedItem(SortOption.HIGHEST_PROFIT_PER_ALCH);
		enableNotificationsCheck.setSelected(true);
		enableAutoRefreshCheck.setSelected(true);
		showMembersCheck.setSelected(true);
		showUnprofitableCheck.setSelected(true);
		saveToConfig();
	}
}
