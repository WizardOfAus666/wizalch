package com.alchmatepro;

import com.alchmatepro.PriceTrendSummary.MarginDirection;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for API response parsing, metadata mapping, price history trend analysis,
 * invalid JSON handling, and provider fallback logic.
 */
public class MarketDataAndParsingTest
{
	private Gson gson;
	private OkHttpClient okHttpClient;
	private WikiPriceDataProvider wikiProvider;
	private AlchMateDataProvider alchMateProvider;
	private ItemMetadataService metadataService;
	private PriceHistoryService priceHistoryService;

	@Before
	public void setUp()
	{
		gson = new Gson();
		okHttpClient = new OkHttpClient();
		wikiProvider = new WikiPriceDataProvider(okHttpClient, gson);
		alchMateProvider = new AlchMateDataProvider(okHttpClient, gson);
		metadataService = new ItemMetadataService(okHttpClient, gson, null);
		priceHistoryService = new PriceHistoryService(okHttpClient, gson);
	}

	@Test
	public void testParseMappingJsonFiltersNonAlchableItemsExceptRunes() throws IOException
	{
		String sampleMappingJson = "[\n"
			+ "  {\"id\": 1127, \"name\": \"Rune platebody\", \"examine\": \"Platebody\", \"members\": false, \"lowalch\": 25600, \"highalch\": 38400, \"limit\": 70, \"value\": 64000},\n"
			+ "  {\"id\": 561, \"name\": \"Nature rune\", \"examine\": \"Used for alchemy\", \"members\": false, \"lowalch\": 72, \"highalch\": 108, \"limit\": 18000, \"value\": 180},\n"
			+ "  {\"id\": 99999, \"name\": \"Untradeable dummy\", \"members\": true, \"lowalch\": 0, \"highalch\": 0, \"limit\": 0, \"value\": 0}\n"
			+ "]";

		Map<Integer, ItemMetadata> map = metadataService.parseMappingJson(sampleMappingJson);
		assertEquals(2, map.size());

		ItemMetadata runePlatebody = map.get(1127);
		assertNotNull(runePlatebody);
		assertEquals("Rune platebody", runePlatebody.getName());
		assertEquals(38400, runePlatebody.getHighAlchValue());
		assertEquals(25600, runePlatebody.getLowAlchValue());
		assertEquals(70, runePlatebody.getBuyLimit());
		assertFalse(runePlatebody.isMembers());

		assertNull(map.get(99999));
	}

	@Test
	public void testParseLatest5mAnd1hWikiJson() throws IOException
	{
		long now = 1_700_000_000L;
		String latestJson = "{\n"
			+ "  \"data\": {\n"
			+ "    \"1127\": {\"high\": 37950, \"highTime\": 1699999950, \"low\": 37700, \"lowTime\": 1699999900},\n"
			+ "    \"4151\": {\"high\": null, \"highTime\": null, \"low\": null, \"lowTime\": null}\n"
			+ "  }\n"
			+ "}";

		Map<Integer, MarketPrice> prices = wikiProvider.parseLatestJson(latestJson, now);
		assertEquals(1, prices.size());
		assertFalse(prices.containsKey(4151)); // Missing prices are never fabricated as 0 GP

		String fiveMinJson = "{\n"
			+ "  \"data\": {\n"
			+ "    \"1127\": {\"avgHighPrice\": 37920, \"highPriceVolume\": 45, \"avgLowPrice\": 37720, \"lowPriceVolume\": 30}\n"
			+ "  }\n"
			+ "}";
		wikiProvider.merge5mJson(prices, fiveMinJson, now);

		String oneHourJson = "{\n"
			+ "  \"data\": {\n"
			+ "    \"1127\": {\"avgHighPrice\": 37900, \"highPriceVolume\": 320, \"avgLowPrice\": 37710, \"lowPriceVolume\": 280}\n"
			+ "  }\n"
			+ "}";
		wikiProvider.merge1hJson(prices, oneHourJson, now);

		MarketPrice mp = prices.get(1127);
		assertNotNull(mp);
		assertEquals(Integer.valueOf(37950), mp.getInstantBuyPrice());
		assertEquals(Integer.valueOf(37700), mp.getInstantSellPrice());
		assertEquals(Integer.valueOf(37920), mp.getAvgHighPrice5m());
		assertEquals(75, mp.getTotalVolume5m());
		assertEquals(Integer.valueOf(37900), mp.getAvgHighPrice1h());
		assertEquals(600, mp.getTotalVolume1h());
		assertEquals(50L, mp.getBuyTradeAgeSeconds(now));
	}

	@Test
	public void testInvalidJsonThrowsIOExceptionCleanly()
	{
		try
		{
			wikiProvider.parseLatestJson("{not-valid-json", 1700000000L);
			fail("Expected IOException on malformed JSON");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("Invalid JSON"));
		}

		try
		{
			metadataService.parseMappingJson("{\"not\":\"an_array\"}");
			fail("Expected IOException when /mapping is not a JSON array");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("Expected JSON array"));
		}
	}

	@Test
	public void testAlchMateProviderJsonParsing()
	{
		String json = "[{\"id\": 1127, \"buyPrice\": 37900, \"sellPrice\": 37600, \"volume1h\": 400}]";
		Map<Integer, MarketPrice> parsed = alchMateProvider.parseJsonPayload(json, 1700000000L);
		assertEquals(1, parsed.size());
		assertEquals(Integer.valueOf(37900), parsed.get(1127).getInstantBuyPrice());
	}

	@Test
	public void testPriceHistoryTimeseriesAndMarginDirection() throws IOException
	{
		// Falling buy price (from 10,000 down to 9,500) means High Alch margin is IMPROVING
		String tsJson = "{\n"
			+ "  \"data\": [\n"
			+ "    {\"timestamp\": 1700000000, \"avgHighPrice\": 10000, \"highPriceVolume\": 50, \"avgLowPrice\": 9900, \"lowPriceVolume\": 40},\n"
			+ "    {\"timestamp\": 1700003600, \"avgHighPrice\": 9750, \"highPriceVolume\": 60, \"avgLowPrice\": 9650, \"lowPriceVolume\": 45},\n"
			+ "    {\"timestamp\": 1700007200, \"avgHighPrice\": 9500, \"highPriceVolume\": 70, \"avgLowPrice\": 9400, \"lowPriceVolume\": 55}\n"
			+ "  ]\n"
			+ "}";

		List<PricePoint> points = priceHistoryService.parseTimeseriesJson(tsJson);
		assertEquals(3, points.size());

		PriceTrendSummary summary = priceHistoryService.buildTrendSummary(1127, points);
		assertEquals(MarginDirection.IMPROVING, summary.getMarginDirection());
		assertEquals(9500, summary.getMinBuyPrice());
		assertEquals(10000, summary.getMaxBuyPrice());
		assertEquals(-500L, summary.getPriceChangeGp());
	}
}
