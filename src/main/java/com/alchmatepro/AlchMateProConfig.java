package com.alchmatepro;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

/**
 * Persistent RuneLite configuration interface for AlchMate Pro.
 */
@ConfigGroup(AlchMateProConfig.CONFIG_GROUP)
public interface AlchMateProConfig extends Config
{
	String CONFIG_GROUP = "alchmatepro";
	String WATCHLIST_DATA_KEY = "watchlistJson";
	String CACHED_MAPPING_KEY = "cachedMappingJson";
	String CACHED_PRICES_KEY = "cachedPricesJson";

	@ConfigSection(
		name = "Rune & Cast Costs",
		description = "Configure nature rune, fire rune, and alchemy casting expenditure",
		position = 0
	)
	String costSection = "costSection";

	@ConfigSection(
		name = "Pricing & Market Safety",
		description = "Configure price calculation methodology, freshness thresholds, and auto-refresh",
		position = 1
	)
	String pricingSection = "pricingSection";

	@ConfigSection(
		name = "Scanner Defaults & Filters",
		description = "Configure default scanner filters, quantities, and sorting preferences",
		position = 2
	)
	String scannerSection = "scannerSection";

	@ConfigSection(
		name = "Alerts & Notifications",
		description = "Configure profit alerts, ROI triggers, and notification cooldowns",
		position = 3
	)
	String alertSection = "alertSection";

	// ==================== RUNE & CAST COSTS ====================

	@ConfigItem(
		keyName = "useLiveNatureRunePrice",
		name = "Use Live Nature Rune Price",
		description = "Automatically use the live Grand Exchange price for Nature runes (Item ID 561), falling back to the manual cost below if unavailable",
		position = 1,
		section = costSection
	)
	default boolean useLiveNatureRunePrice()
	{
		return true;
	}

	@Range(min = 0, max = 10000)
	@ConfigItem(
		keyName = "natureRuneCost",
		name = "Nature Rune Cost (GP)",
		description = "Cost of 1 Nature rune per High Level Alchemy cast (used when Live Nature Rune Price is off, or as fallback)",
		position = 2,
		section = costSection
	)
	default int natureRuneCost()
	{
		return 150;
	}

	@ConfigItem(
		keyName = "useFireStaff",
		name = "Equipped Fire Staff (0 Fire Rune Cost)",
		description = "When enabled, assumes a Staff of Fire / Lava battlestaff / Tome of Fire is equipped so Fire rune cost is 0 GP",
		position = 3,
		section = costSection
	)
	default boolean useFireStaff()
	{
		return true;
	}

	@Range(min = 0, max = 5000)
	@ConfigItem(
		keyName = "fireRuneCostPerCast",
		name = "Fire Rune Cost Per Cast (GP)",
		description = "Total cost of the 5 Fire runes consumed per High Level Alchemy cast when not using a fire staff (e.g. 5 x 4 GP = 20 GP)",
		position = 4,
		section = costSection
	)
	default int fireRuneCostPerCast()
	{
		return 20;
	}

	// ==================== PRICING & MARKET SAFETY ====================

	@ConfigItem(
		keyName = "priceCalculationMethod",
		name = "Price Calculation Method",
		description = "Select which Grand Exchange market price metric is used as the estimated item purchase price",
		position = 10,
		section = pricingSection
	)
	default PriceCalculationMethod priceCalculationMethod()
	{
		return PriceCalculationMethod.INSTANT_BUY_HIGH;
	}

	@ConfigItem(
		keyName = "enableAutoRefresh",
		name = "Enable Automatic Refresh",
		description = "Automatically poll market data in the background at the configured refresh interval",
		position = 11,
		section = pricingSection
	)
	default boolean enableAutoRefresh()
	{
		return true;
	}

	@Range(min = 30, max = 900)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "refreshIntervalSeconds",
		name = "Refresh Interval",
		description = "Background market data refresh interval in seconds (minimum 30s to respect API rate limits)",
		position = 12,
		section = pricingSection
	)
	default int refreshIntervalSeconds()
	{
		return 60;
	}

	@Range(min = 5, max = 1440)
	@Units(Units.MINUTES)
	@ConfigItem(
		keyName = "maxDataAgeMinutes",
		name = "Maximum Data Age (Stale Threshold)",
		description = "Mark prices older than this threshold (in minutes) as STALE DATA and exclude them from confirmed recommendations",
		position = 13,
		section = pricingSection
	)
	default int maxDataAgeMinutes()
	{
		return 120;
	}

	// ==================== SCANNER DEFAULTS & FILTERS ====================

	@Range(min = -1000000, max = 10000000)
	@ConfigItem(
		keyName = "minimumProfit",
		name = "Minimum Profit (GP)",
		description = "Default minimum profit per alch in GP for scanner filtering",
		position = 20,
		section = scannerSection
	)
	default int minimumProfit()
	{
		return 1;
	}

	@Range(min = -100, max = 1000)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "minimumRoiPercent",
		name = "Minimum ROI (%)",
		description = "Default minimum Return on Investment percentage",
		position = 21,
		section = scannerSection
	)
	default int minimumRoiPercent()
	{
		return 0;
	}

	@Range(min = 0, max = 1000000)
	@ConfigItem(
		keyName = "minimumTradeVolume",
		name = "Minimum 1h Trade Volume",
		description = "Minimum 1-hour Grand Exchange trading volume required",
		position = 22,
		section = scannerSection
	)
	default int minimumTradeVolume()
	{
		return 0;
	}

	@Range(min = 0, max = 2147483647)
	@ConfigItem(
		keyName = "maximumPurchaseCost",
		name = "Maximum Item Purchase Cost (0 = No Max)",
		description = "Maximum unit purchase price in GP (set to 0 for no upper limit)",
		position = 23,
		section = scannerSection
	)
	default int maximumPurchaseCost()
	{
		return 0;
	}

	@Range(min = 1, max = 1000000)
	@ConfigItem(
		keyName = "defaultQuantity",
		name = "Default Quantity",
		description = "Default item quantity used for bulk profit calculations",
		position = 24,
		section = scannerSection
	)
	default int defaultQuantity()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "defaultSorting",
		name = "Default Sorting",
		description = "Default sorting criterion in the Live Alch Scanner",
		position = 25,
		section = scannerSection
	)
	default SortOption defaultSorting()
	{
		return SortOption.HIGHEST_PROFIT_PER_ALCH;
	}

	@ConfigItem(
		keyName = "showMembersItems",
		name = "Show Members Items",
		description = "Include Members-only (P2P) items in the scanner and dashboard",
		position = 26,
		section = scannerSection
	)
	default boolean showMembersItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showUnprofitableItems",
		name = "Show Unprofitable Items",
		description = "Include unprofitable (loss-making) items in the scanner when filters allow",
		position = 27,
		section = scannerSection
	)
	default boolean showUnprofitableItems()
	{
		return true;
	}

	// ==================== ALERTS & NOTIFICATIONS ====================

	@ConfigItem(
		keyName = "enableNotifications",
		name = "Enable Notifications",
		description = "Send RuneLite notifications when watchlist or scanner alert conditions are met",
		position = 30,
		section = alertSection
	)
	default boolean enableNotifications()
	{
		return true;
	}

	@Range(min = 0, max = 10000000)
	@ConfigItem(
		keyName = "alertProfitThresholdGp",
		name = "Alert When Profit Exceeds (GP)",
		description = "Trigger an alert when a watched item's net profit per alch exceeds this GP amount",
		position = 31,
		section = alertSection
	)
	default int alertProfitThresholdGp()
	{
		return 1000;
	}

	@Range(min = 0, max = 500)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "alertRoiThresholdPercent",
		name = "Alert When ROI Exceeds (%)",
		description = "Trigger an alert when a watched item's ROI exceeds this percentage",
		position = 32,
		section = alertSection
	)
	default int alertRoiThresholdPercent()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "alertOnBecomeProfitable",
		name = "Alert When Item Becomes Profitable",
		description = "Notify when a previously unprofitable watched item turns profitable",
		position = 33,
		section = alertSection
	)
	default boolean alertOnBecomeProfitable()
	{
		return true;
	}

	@Range(min = -1000000, max = 1000000)
	@ConfigItem(
		keyName = "alertProfitDropBelowGp",
		name = "Alert When Profit Drops Below (GP)",
		description = "Notify when a watched item's profit per alch drops below this GP floor",
		position = 34,
		section = alertSection
	)
	default int alertProfitDropBelowGp()
	{
		return 0;
	}

	@Range(min = 1, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "alertPriceChangePercent",
		name = "Alert on Significant Price Change (%)",
		description = "Notify when a watched item's purchase price moves by at least this percentage from baseline",
		position = 35,
		section = alertSection
	)
	default int alertPriceChangePercent()
	{
		return 10;
	}

	@Range(min = 1, max = 240)
	@Units(Units.MINUTES)
	@ConfigItem(
		keyName = "alertCooldownMinutes",
		name = "Alert Cooldown",
		description = "Minimum cooldown in minutes before repeating an identical alert for the same item",
		position = 36,
		section = alertSection
	)
	default int alertCooldownMinutes()
	{
		return 15;
	}
}
