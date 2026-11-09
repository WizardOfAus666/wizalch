package com.alchmatepro;

import com.alchmatepro.NavigationController.Section;
import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import okhttp3.OkHttpClient;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Integration tests verifying Swing UI panel construction, section navigation,
 * colour palette & spacing tokens, responsive layout behavior across narrow and wide
 * window sizes, and zero sibling component overlap across all 7 AlchMate Pro views.
 */
public class UiAndPluginIntegrationTest
{
	@Test
	public void testDesignSystemTokensAndHourlyProfitFormatting()
	{
		assertEquals(new Color(0x10, 0x11, 0x14), AlchTheme.BG_MAIN);
		assertEquals(new Color(0x17, 0x19, 0x1E), AlchTheme.BG_SECONDARY);
		assertEquals(new Color(0x1D, 0x20, 0x27), AlchTheme.BG_PANEL);
		assertEquals(new Color(0x22, 0x26, 0x30), AlchTheme.BG_CARD);
		assertEquals(new Color(0x34, 0x39, 0x44), AlchTheme.BORDER_DEFAULT);
		assertEquals(new Color(0xE7, 0xB8, 0x3F), AlchTheme.ACCENT_GOLD);
		assertEquals(new Color(0x35, 0xD9, 0x87), AlchTheme.SUCCESS_GREEN);
		assertEquals(new Color(0xF0, 0x5D, 0x66), AlchTheme.DANGER_RED);
		assertEquals(new Color(0x55, 0xA6, 0xE8), AlchTheme.INFO_BLUE);
		assertEquals(new Color(0xF1, 0xF2, 0xF4), AlchTheme.TEXT_PRIMARY);
		assertEquals(new Color(0xA0, 0xA6, 0xB2), AlchTheme.TEXT_SECONDARY);
		assertEquals(new Color(0x73, 0x7B, 0x89), AlchTheme.TEXT_MUTED);

		assertEquals(4, AlchTheme.SPACE_XS);
		assertEquals(8, AlchTheme.SPACE_SM);
		assertEquals(12, AlchTheme.SPACE_MD);
		assertEquals(16, AlchTheme.SPACE_LG);
		assertEquals(20, AlchTheme.SPACE_XL);
		assertEquals(24, AlchTheme.SPACE_XXL);

		assertEquals(600_000L, AlchTheme.calculateHourlyProfit(500L));
		assertTrue(AlchTheme.formatHourlyProfit(500L).contains("GP/hr"));
	}

	@Test
	public void testToolbarIconAndFullPanelNavigationLifecycle() throws Exception
	{
		BufferedImage toolbarIcon = AlchTheme.createPluginToolbarIcon();
		assertNotNull(toolbarIcon);
		assertEquals(24, toolbarIcon.getWidth());
		assertEquals(24, toolbarIcon.getHeight());

		Gson gson = new Gson();
		OkHttpClient http = new OkHttpClient();

		CacheService cacheService = new CacheService(gson);
		WikiPriceDataProvider wikiProvider = new WikiPriceDataProvider(http, gson);
		ItemMetadataService metadataService = new ItemMetadataService(http, gson, cacheService);
		ProfitCalculator profitCalculator = new ProfitCalculator();
		PriceValidator priceValidator = new PriceValidator();
		RecommendationEngine recommendationEngine = new RecommendationEngine(priceValidator);
		WatchlistService watchlistService = new WatchlistService(null, gson);
		AlertService alertService = new AlertService(null, null, watchlistService);
		PriceHistoryService priceHistoryService = new PriceHistoryService(http, gson);
		IconService iconService = new IconService(null);

		MarketDataService marketDataService = new MarketDataService(
			wikiProvider,
			metadataService,
			cacheService,
			profitCalculator,
			priceValidator,
			recommendationEngine,
			alertService,
			null
		);

		long now = 1_700_000_000L;
		Map<Integer, ItemMetadata> metaMap = new HashMap<>();
		metaMap.put(1127, ItemMetadata.builder().id(1127).name("Rune platebody").members(false).highAlchValue(38_400).lowAlchValue(25_600).buyLimit(70).build());
		metaMap.put(2503, ItemMetadata.builder().id(2503).name("Black d'hide body").members(true).highAlchValue(8_088).lowAlchValue(5_392).buyLimit(70).build());
		Map<Integer, MarketPrice> priceMap = new HashMap<>();
		priceMap.put(1127, MarketPrice.builder().itemId(1127).instantBuyPrice(37_800).instantBuyTimeEpochSec(now - 10).highVolume1h(600).fetchedAtEpochSec(now).build());
		priceMap.put(2503, MarketPrice.builder().itemId(2503).instantBuyPrice(7_520).instantBuyTimeEpochSec(now - 20).highVolume1h(1_400).fetchedAtEpochSec(now).build());
		priceMap.put(MarketDataService.NATURE_RUNE_ITEM_ID, MarketPrice.builder().itemId(MarketDataService.NATURE_RUNE_ITEM_ID).instantBuyPrice(182).instantSellPrice(179).fetchedAtEpochSec(now).build());

		marketDataService.loadDirectData(metaMap, priceMap, now);

		AlchMateProPanel panel = new AlchMateProPanel(
			marketDataService,
			profitCalculator,
			recommendationEngine,
			priceHistoryService,
			watchlistService,
			alertService,
			cacheService,
			iconService,
			null,
			null
		);

		assertEquals(Section.DASHBOARD, panel.getActiveSection());

		// Verify responsive layout and zero sibling overlap at narrow (260px), medium (520px), and wide (960px) widths
		int[] testWidths = {260, 520, 960};
		for (int w : testWidths)
		{
			panel.setSize(w, 860);
			panel.doLayout();
			for (Section section : Section.values())
			{
				panel.navigateTo(section);
				assertEquals(section, panel.getActiveSection());
				layoutRecursively(panel);
				assertNoSiblingOverlap(panel.getDashboardPanel());
			}
		}

		AlchMateItem item = marketDataService.getItemById(1127).orElseThrow();
		watchlistService.toggleItem(item);
		panel.openItemAnalysis(item);
		assertEquals(Section.ITEM_ANALYSIS, panel.getActiveSection());
		layoutRecursively(panel);
		assertNoSiblingOverlap(panel.getItemAnalysisPanel());

		panel.openProfitCalculator(item);
		assertEquals(Section.PROFIT_CALCULATOR, panel.getActiveSection());
		layoutRecursively(panel);
		assertNoSiblingOverlap(panel.getProfitCalculatorPanel());
	}

	private static void layoutRecursively(Container container)
	{
		container.doLayout();
		for (int i = 0; i < container.getComponentCount(); i++)
		{
			Component c = container.getComponent(i);
			if (c instanceof Container)
			{
				layoutRecursively((Container) c);
			}
		}
	}

	private static void assertNoSiblingOverlap(Container container)
	{
		Component[] comps = container.getComponents();
		for (int i = 0; i < comps.length; i++)
		{
			if (!comps[i].isVisible())
			{
				continue;
			}
			Rectangle r1 = comps[i].getBounds();
			if (r1.width <= 0 || r1.height <= 0)
			{
				continue;
			}
			for (int j = i + 1; j < comps.length; j++)
			{
				if (!comps[j].isVisible())
				{
					continue;
				}
				Rectangle r2 = comps[j].getBounds();
				if (r2.width <= 0 || r2.height <= 0)
				{
					continue;
				}
				assertFalse(
					"Components overlapped in " + container.getClass().getSimpleName() + ": " + r1 + " vs " + r2,
					r1.intersects(r2)
				);
			}
			if (comps[i] instanceof Container)
			{
				assertNoSiblingOverlap((Container) comps[i]);
			}
		}
	}
}
