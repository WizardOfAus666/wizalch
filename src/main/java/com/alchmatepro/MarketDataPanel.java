package com.alchmatepro;

import com.alchmatepro.AlchUiComponents.VerticalStackLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import net.runelite.client.util.QuantityFormatter;

/**
 * Market Data Transparency & Provider Diagnostics page (Section 14).
 * Displays active and fallback provider status, timestamps, item/price validity counts,
 * stale data statistics, live rune market prices, cache health, and API error diagnostics.
 */
public class MarketDataPanel extends JPanel
{
	private final MarketDataService marketDataService;
	private final CacheService cacheService;

	private final JLabel connectionBadgeLabel = AlchTheme.createBadge("LIVE DATA", AlchTheme.SUCCESS_GREEN, AlchTheme.SUCCESS_BG);
	private final JLabel activeProviderLabel = new JLabel("-");
	private final JLabel secondaryProviderLabel = new JLabel("-");
	private final JLabel lastSuccessLabel = new JLabel("-");
	private final JLabel lastAttemptLabel = new JLabel("-");
	private final JLabel totalItemsLoadedLabel = new JLabel("0");
	private final JLabel validPricesCountLabel = new JLabel("0");
	private final JLabel profitableCountLabel = new JLabel("0");
	private final JLabel stalePricesCountLabel = new JLabel("0");
	private final JLabel failedItemsCountLabel = new JLabel("0");
	private final JLabel liveNatureRuneLabel = new JLabel("180 GP");
	private final JLabel liveFireRuneLabel = new JLabel("4 GP");
	private final JLabel cacheStatusLabel = new JLabel("-");

	private final JTextArea primaryProviderNoteArea = AlchUiComponents.createReadOnlyTextArea("", AlchTheme.TEXT_SECONDARY);
	private final JTextArea apiErrorArea = AlchUiComponents.createReadOnlyTextArea("", AlchTheme.SUCCESS_GREEN);

	public MarketDataPanel(MarketDataService marketDataService, CacheService cacheService)
	{
		super(new VerticalStackLayout(AlchTheme.SPACE_SM));
		this.marketDataService = marketDataService;
		this.cacheService = cacheService;

		setBackground(AlchTheme.BG_MAIN);
		setBorder(BorderFactory.createEmptyBorder(
			AlchTheme.SPACE_SM, AlchTheme.SPACE_SM, AlchTheme.SPACE_MD, AlchTheme.SPACE_SM
		));

		add(buildStatusHeaderCard());
		add(buildTelemetryMetricsCard());
		add(buildProviderArchitectureCard());
		add(buildErrorAndCacheActionsCard());
	}

	private JPanel buildStatusHeaderCard()
	{
		JPanel card = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_MD));
		card.setBackground(AlchTheme.BG_CARD);
		card.setBorder(AlchTheme.createGoldCardBorder());

		JLabel title = AlchTheme.createSectionHeading("MARKET DATA TRANSPARENCY");
		JPanel topRow = AlchTheme.createSplitRow(title, connectionBadgeLabel);

		JButton refreshNowBtn = AlchTheme.createPrimaryButton("Manual Refresh Live Market Data");
		refreshNowBtn.addActionListener(e -> marketDataService.refreshAsync());

		card.add(topRow);
		card.add(refreshNowBtn);
		return card;
	}

	private JPanel buildTelemetryMetricsCard()
	{
		JPanel card = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
		card.setBackground(AlchTheme.BG_CARD);
		card.setBorder(AlchTheme.createCardBorder());

		JLabel title = AlchTheme.createSectionHeading("PROVIDER & DATASET DIAGNOSTICS");
		card.add(title);

		JPanel rows = new JPanel(new VerticalStackLayout(2));
		rows.setOpaque(false);

		activeProviderLabel.setForeground(AlchTheme.TEXT_PRIMARY);
		secondaryProviderLabel.setForeground(AlchTheme.INFO_BLUE);
		lastSuccessLabel.setForeground(AlchTheme.TEXT_PRIMARY);
		lastAttemptLabel.setForeground(AlchTheme.TEXT_SECONDARY);
		totalItemsLoadedLabel.setForeground(AlchTheme.TEXT_PRIMARY);
		validPricesCountLabel.setForeground(AlchTheme.SUCCESS_GREEN);
		profitableCountLabel.setForeground(AlchTheme.ACCENT_GOLD);
		stalePricesCountLabel.setForeground(AlchTheme.ACCENT_GOLD);
		failedItemsCountLabel.setForeground(AlchTheme.TEXT_MUTED);
		liveNatureRuneLabel.setForeground(AlchTheme.ACCENT_GOLD);
		liveFireRuneLabel.setForeground(AlchTheme.TEXT_SECONDARY);
		cacheStatusLabel.setForeground(AlchTheme.TEXT_SECONDARY);

		rows.add(AlchTheme.createKeyValueRow("Active Provider", activeProviderLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Wiki API Endpoints", secondaryProviderLabel, false));
		rows.add(AlchTheme.createKeyValueRow("Last Successful Sync", lastSuccessLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Last Attempted Sync", lastAttemptLabel, false));
		rows.add(AlchTheme.createKeyValueRow("Items Loaded (/mapping)", totalItemsLoadedLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Valid Market Prices", validPricesCountLabel, false));
		rows.add(AlchTheme.createKeyValueRow("Profitable Items", profitableCountLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Stale Prices Flagged", stalePricesCountLabel, false));
		rows.add(AlchTheme.createKeyValueRow("Unpriced / Inactive Items", failedItemsCountLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Live Nature Rune (ID 561)", liveNatureRuneLabel, false));
		rows.add(AlchTheme.createKeyValueRow("Live Fire Rune (ID 554)", liveFireRuneLabel, true));
		rows.add(AlchTheme.createKeyValueRow("Local Cache Status", cacheStatusLabel, false));

		card.add(rows);
		return card;
	}

	private JPanel buildProviderArchitectureCard()
	{
		JPanel card = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
		card.setBackground(AlchTheme.BG_CARD);
		card.setBorder(AlchTheme.createCardBorder());

		JLabel title = AlchTheme.createSectionHeading("DATA SOURCE ARCHITECTURE");
		card.add(title);
		card.add(primaryProviderNoteArea);
		return card;
	}

	private JPanel buildErrorAndCacheActionsCard()
	{
		JPanel card = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
		card.setBackground(AlchTheme.BG_CARD);
		card.setBorder(AlchTheme.createCardBorder());

		JLabel title = AlchTheme.createSectionHeading("API HEALTH & CACHE CONTROLS");

		JButton clearCacheBtn = AlchTheme.createSecondaryButton("Clear Local Cache & Resync");
		clearCacheBtn.addActionListener(e -> {
			cacheService.clearCache();
			marketDataService.refreshAsync();
		});

		card.add(title);
		card.add(apiErrorArea);
		card.add(clearCacheBtn);
		return card;
	}

	public void refreshView()
	{
		ProviderStatusSnapshot s = marketDataService.getStatusSnapshot();
		if (s == null)
		{
			return;
		}

		connectionBadgeLabel.setText(s.getConnectionState().getLabel());
		switch (s.getConnectionState())
		{
			case LIVE:
				connectionBadgeLabel.setForeground(AlchTheme.SUCCESS_GREEN);
				connectionBadgeLabel.setBackground(AlchTheme.SUCCESS_BG);
				break;
			case CACHED:
			case DEGRADED_FALLBACK:
			case REFRESHING:
				connectionBadgeLabel.setForeground(AlchTheme.ACCENT_GOLD);
				connectionBadgeLabel.setBackground(AlchTheme.ACCENT_GOLD_DIM);
				break;
			case OFFLINE_ERROR:
			default:
				connectionBadgeLabel.setForeground(AlchTheme.DANGER_RED);
				connectionBadgeLabel.setBackground(AlchTheme.DANGER_BG);
				break;
		}

		activeProviderLabel.setText(s.getActiveProviderName() != null ? s.getActiveProviderName() : "OSRS Wiki API");
		secondaryProviderLabel.setText("/mapping, /latest, /5m, /1h");
		lastSuccessLabel.setText(AlchTheme.formatEpochTimestamp(s.getLastSuccessfulUpdateEpochSec()));
		lastAttemptLabel.setText(AlchTheme.formatEpochTimestamp(s.getLastAttemptedUpdateEpochSec()));
		totalItemsLoadedLabel.setText(QuantityFormatter.formatNumber(s.getTotalItemsLoaded()));
		validPricesCountLabel.setText(QuantityFormatter.formatNumber(s.getValidPricesCount()));
		profitableCountLabel.setText(QuantityFormatter.formatNumber(s.getProfitableItemsCount()));
		stalePricesCountLabel.setText(QuantityFormatter.formatNumber(s.getStalePricesCount()));
		failedItemsCountLabel.setText(QuantityFormatter.formatNumber(s.getFailedOrMissingItemsCount()));
		liveNatureRuneLabel.setText(AlchTheme.formatGp(s.getNatureRuneLivePrice()));
		liveFireRuneLabel.setText(AlchTheme.formatGp(s.getFireRuneLivePrice()));
		cacheStatusLabel.setText(s.getCacheStatusDescription() != null ? s.getCacheStatusDescription() : "Ready");

		primaryProviderNoteArea.setText(
			"Primary Provider Check: " + s.getPrimaryProviderStatusNote()
				+ "\n\nSecondary Active Feed: OSRS Wiki Real-Time Prices API (official RuneLite partner feed) providing instant-buy, instant-sell, 5m rolling, 1h stable, and historical time-series pricing."
		);

		if (s.getLastErrorMessage() != null && !s.getLastErrorMessage().isEmpty())
		{
			apiErrorArea.setForeground(AlchTheme.ACCENT_GOLD);
			apiErrorArea.setText("⚠ Warning / Diagnostic: " + s.getLastErrorMessage()
				+ "\nOutdated information is never silently substituted as live.");
		}
		else
		{
			apiErrorArea.setForeground(AlchTheme.SUCCESS_GREEN);
			apiErrorArea.setText("✓ All bulk market endpoints operating normally. Zero API errors reported.");
		}
		revalidate();
		repaint();
	}
}
