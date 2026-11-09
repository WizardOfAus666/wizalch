package com.alchmatepro;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

/**
 * Main RuneLite Plugin entry point for AlchMate Pro.
 * Registers the sidebar NavigationButton, initialises background market polling,
 * and coordinates configuration updates without blocking the RuneLite client thread.
 */
@Slf4j
@PluginDescriptor(
	name = "WIZALCH",
	description = "Professional OSRS High Alchemy profit scanner, Grand Exchange price analyser, buy-limit calculator, watchlist, and smart recommendation engine",
	tags = {
		"wizalch",
		"alch",
		"alchemy",
		"high alch",
		"profit",
		"money making",
		"grand exchange",
		"ge",
		"margin",
		"calculator",
		"scanner",
		"magic"
	}
)
public class AlchMateProPlugin extends Plugin
{
	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private AlchMateProConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private MarketDataService marketDataService;

	@Inject
	private ProfitCalculator profitCalculator;

	@Inject
	private RecommendationEngine recommendationEngine;

	@Inject
	private PriceHistoryService priceHistoryService;

	@Inject
	private WatchlistService watchlistService;

	@Inject
	private AlertService alertService;

	@Inject
	private CacheService cacheService;

	@Inject
	private IconService iconService;

	private AlchMateProPanel pluginPanel;
	private NavigationButton navButton;

	@Provides
	AlchMateProConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AlchMateProConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		log.info("Starting WIZALCH - High Alchemy Intelligence Plugin");

		pluginPanel = new AlchMateProPanel(
			marketDataService,
			profitCalculator,
			recommendationEngine,
			priceHistoryService,
			watchlistService,
			alertService,
			cacheService,
			iconService,
			config,
			configManager
		);

		BufferedImage icon = AlchTheme.createPluginToolbarIcon();

		navButton = NavigationButton.builder()
			.tooltip("WIZALCH - High Alch Profit Scanner")
			.icon(icon)
			.priority(5)
			.panel(pluginPanel)
			.build();

		clientToolbar.addNavigation(navButton);
		marketDataService.startUp();
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Shutting down WIZALCH");
		if (navButton != null && clientToolbar != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		if (marketDataService != null)
		{
			marketDataService.shutDown();
		}
		if (pluginPanel != null)
		{
			pluginPanel.shutDown();
			pluginPanel = null;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event == null || !AlchMateProConfig.CONFIG_GROUP.equals(event.getGroup()))
		{
			return;
		}

		// Ignore internal watchlist persistence writes so we don't loop
		if (AlchMateProConfig.WATCHLIST_DATA_KEY.equals(event.getKey()))
		{
			return;
		}

		marketDataService.syncSettingsFromConfig();

		if ("enableAutoRefresh".equals(event.getKey()) || "refreshIntervalSeconds".equals(event.getKey()))
		{
			marketDataService.rescheduleAutoRefresh();
		}

		marketDataService.recalculateAllItems();

		if (pluginPanel != null)
		{
			SwingUtilities.invokeLater(() -> pluginPanel.refreshAllPanels());
		}
	}
}
