package com.alchmatepro;

import com.alchmatepro.FilterCriteria.MembershipFilter;
import com.alchmatepro.PriceValidator.ResolvedPurchasePrice;
import com.google.gson.Gson;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link PriceValidator}, {@link RecommendationEngine}, {@link MarketDataService},
 * {@link WatchlistService}, {@link AlertService}, and {@link CacheService}.
 */
public class RecommendationAndServiceTest
{
	private ProfitCalculator profitCalculator;
	private PriceValidator priceValidator;
	private RecommendationEngine recommendationEngine;

	@Before
	public void setUp()
	{
		profitCalculator = new ProfitCalculator();
		priceValidator = new PriceValidator();
		recommendationEngine = new RecommendationEngine(priceValidator);
	}

	@Test
	public void testStaleDataAndManipulationWarningsPreventFalseRecommendation()
	{
		long now = 1_700_000_000L;
		ItemMetadata meta = ItemMetadata.builder()
			.id(2500)
			.name("Obscure Third-Age Piece")
			.members(true)
			.highAlchValue(50_000)
			.lowAlchValue(33_000)
			.buyLimit(8)
			.build();

		// Trade is 10 hours old (stale beyond 120m threshold) and has tiny volume + big spike
		MarketPrice stalePrice = MarketPrice.builder()
			.itemId(2500)
			.instantBuyPrice(30_000)
			.instantBuyTimeEpochSec(now - 36_000L)
			.instantSellPrice(15_000)
			.instantSellTimeEpochSec(now - 36_000L)
			.avgHighPrice1h(45_000)
			.highVolume1h(1)
			.lowVolume1h(0)
			.fetchedAtEpochSec(now)
			.build();

		ResolvedPurchasePrice resolved = priceValidator.resolvePurchasePrice(
			stalePrice,
			PriceCalculationMethod.INSTANT_BUY_HIGH,
			120,
			now
		);
		assertTrue(resolved.isStale());

		AlchCalculation calc = profitCalculator.calculate(2500, 50_000L, resolved.getPriceGp(), 150L, 10);
		AlchRecommendation rec = recommendationEngine.evaluate(meta, stalePrice, resolved, calc);

		assertFalse("Stale/manipulated item must never be recommended", rec.isRecommended());
		assertTrue(rec.hasWarning(MarketWarning.STALE_DATA));
		assertTrue(rec.hasWarning(MarketWarning.LOW_VOLUME));
		assertTrue(rec.hasWarning(MarketWarning.POSSIBLE_PRICE_MANIPULATION));
	}

	@Test
	public void testHealthyItemReceivesStrongRecommendationAndCategories()
	{
		long now = 1_700_000_000L;
		ItemMetadata dhideBody = ItemMetadata.builder()
			.id(2503)
			.name("Black d'hide body")
			.members(true)
			.highAlchValue(8_088)
			.lowAlchValue(5_392)
			.buyLimit(125)
			.build();

		MarketPrice livePrice = MarketPrice.builder()
			.itemId(2503)
			.instantBuyPrice(7_450)
			.instantBuyTimeEpochSec(now - 45L)
			.instantSellPrice(7_410)
			.instantSellTimeEpochSec(now - 30L)
			.avgHighPrice5m(7_455)
			.highVolume5m(60)
			.avgHighPrice1h(7_460)
			.highVolume1h(450)
			.lowVolume1h(420)
			.fetchedAtEpochSec(now)
			.build();

		ResolvedPurchasePrice resolved = priceValidator.resolvePurchasePrice(
			livePrice,
			PriceCalculationMethod.INSTANT_BUY_HIGH,
			120,
			now
		);
		assertFalse(resolved.isStale());
		assertEquals(7_450L, resolved.getPriceGp());

		AlchCalculation calc = profitCalculator.calculate(2503, 8_088L, 7_450L, 150L, 0L, 100, 125);
		assertEquals(488L, calc.getNetProfitPerAlch());

		AlchRecommendation rec = recommendationEngine.evaluate(dhideBody, livePrice, resolved, calc);
		assertTrue(rec.isRecommended());
		assertTrue(rec.getOverallScore() >= 55.0);
		assertTrue(rec.matchesCategory(RecommendationCategory.BEST_OVERALL));
		assertTrue(rec.matchesCategory(RecommendationCategory.BEST_PROFIT_PER_ALCH));
		assertTrue(rec.matchesCategory(RecommendationCategory.BEST_BULK_ALCH));
		assertTrue(rec.getExplanation().contains("Recommended because"));
	}

	@Test
	public void testWatchlistPersistenceAndAlertCooldowns()
	{
		Gson gson = new Gson();
		WatchlistService watchlistService = new WatchlistService(null, gson);
		AlertService alertService = new AlertService(null, null, watchlistService);

		ItemMetadata meta = ItemMetadata.builder()
			.id(1391)
			.name("Battlestaff")
			.members(true)
			.highAlchValue(4_200)
			.buyLimit(11_000)
			.build();

		AlchCalculation calc = profitCalculator.calculate(1391, 4_200L, 2_900L, 150L, 100);
		AlchMateItem item = AlchMateItem.builder()
			.metadata(meta)
			.calculation(calc)
			.estimatedPurchasePrice(2_900L)
			.priceSourceType(PriceSourceType.INSTANT_BUY_PRICE)
			.stale(false)
			.priceAgeSeconds(15L)
			.build();

		watchlistService.addOrUpdateItem(item, 800, 5.0);
		assertTrue(watchlistService.isWatched(1391));
		assertEquals(1, watchlistService.size());

		Map<Integer, AlchMateItem> itemsById = Map.of(1391, item);
		long t0 = 1_700_000_000L;

		// First evaluation triggers profit & ROI alerts
		List<AlertEvent> firstAlerts = alertService.evaluateAlerts(itemsById, t0);
		assertFalse(firstAlerts.isEmpty());

		// Second evaluation 60 seconds later is suppressed by the 15-minute cooldown
		List<AlertEvent> secondAlerts = alertService.evaluateAlerts(itemsById, t0 + 60L);
		assertTrue("Alerts inside cooldown window must be suppressed", secondAlerts.isEmpty());

		// Evaluation after cooldown (16 minutes later) triggers again
		List<AlertEvent> thirdAlerts = alertService.evaluateAlerts(itemsById, t0 + 960L);
		assertFalse(thirdAlerts.isEmpty());

		watchlistService.clearAll();
		assertEquals(0, watchlistService.size());
	}

	@Test
	public void testMarketDataServiceFilteringAndSortingAndCache() throws Exception
	{
		Gson gson = new Gson();
		OkHttpClient http = new OkHttpClient();

		CacheService cacheService = new CacheService(gson);
		WikiPriceDataProvider wikiProvider = new WikiPriceDataProvider(http, gson);
		ItemMetadataService metadataService = new ItemMetadataService(http, gson, cacheService);
		WatchlistService watchlistService = new WatchlistService(null, gson);
		AlertService alertService = new AlertService(null, null, watchlistService);

		MarketDataService service = new MarketDataService(
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
		metaMap.put(859, ItemMetadata.builder().id(859).name("Magic longbow").members(true).highAlchValue(1_536).lowAlchValue(1_024).buyLimit(18_000).build());

		Map<Integer, MarketPrice> priceMap = new HashMap<>();
		priceMap.put(1127, MarketPrice.builder().itemId(1127).instantBuyPrice(37_800).instantBuyTimeEpochSec(now - 20).highVolume1h(500).fetchedAtEpochSec(now).build());
		priceMap.put(859, MarketPrice.builder().itemId(859).instantBuyPrice(1_300).instantBuyTimeEpochSec(now - 10).highVolume1h(5_000).fetchedAtEpochSec(now).build());

		// Verify disk cache round-trip
		cacheService.saveMetadataCache(metaMap);
		cacheService.savePricesCache(priceMap);
		assertEquals(2, cacheService.loadMetadataCache().size());
		assertEquals(2, cacheService.loadPricesCache().size());

		service.loadDirectData(metaMap, priceMap, now);
		assertEquals(2, service.getAllAnalysedItems().size());

		// Default sort: Highest profit per alch -> Rune platebody (+450 GP) before Magic longbow (+86 GP)
		List<AlchMateItem> byProfit = service.filterAndSort(FilterCriteria.builder()
			.sortOption(SortOption.HIGHEST_PROFIT_PER_ALCH)
			.build());
		assertEquals(1127, byProfit.get(0).getId());

		// Sort by Highest ROI -> Magic longbow (+5.93% ROI) before Rune platebody (+1.19% ROI)
		List<AlchMateItem> byRoi = service.filterAndSort(FilterCriteria.builder()
			.sortOption(SortOption.HIGHEST_ROI)
			.build());
		assertEquals(859, byRoi.get(0).getId());

		// Filter F2P only -> only Rune platebody (1127)
		List<AlchMateItem> f2pOnly = service.filterAndSort(FilterCriteria.builder()
			.membershipFilter(MembershipFilter.FREE_TO_PLAY_ONLY)
			.build());
		assertEquals(1, f2pOnly.size());
		assertEquals(1127, f2pOnly.get(0).getId());

		// Search by name
		List<AlchMateItem> searched = service.filterAndSort(FilterCriteria.builder()
			.searchQuery("magic")
			.build());
		assertEquals(1, searched.size());
		assertEquals(859, searched.get(0).getId());
		assertNotNull(service.getStatusSnapshot());

		// Verify live Nature Rune (ID 561) price updates automatically when present in market feed
		priceMap.put(561, MarketPrice.builder()
			.itemId(561)
			.instantBuyPrice(94)
			.instantBuyTimeEpochSec(now - 5)
			.instantSellPrice(91)
			.avgHighPrice1h(93)
			.highVolume1h(120_000)
			.fetchedAtEpochSec(now)
			.build());

		service.loadDirectData(metaMap, priceMap, now);
		assertTrue(service.isUsingLiveNatureRunePrice());
		assertEquals(94L, service.getEffectiveNatureRuneCost());
		// Rune platebody: 38,400 alch - 37,800 buy - 94 live nat = +506 GP/alch
		assertEquals(506L, service.getItemById(1127).orElseThrow().getNetProfitPerAlch());

		// Verify manual override via SettingsPanel / applyRuntimeSettings
		service.applyRuntimeSettings(
			false,
			110,
			true,
			20,
			PriceCalculationMethod.INSTANT_BUY_HIGH,
			60,
			120,
			1,
			0,
			0,
			0,
			100,
			SortOption.HIGHEST_PROFIT_PER_ALCH,
			true,
			false,
			true,
			true
		);
		assertFalse(service.isUsingLiveNatureRunePrice());
		assertEquals(110L, service.getEffectiveNatureRuneCost());
		assertEquals(490L, service.getItemById(1127).orElseThrow().getNetProfitPerAlch());
	}
}
