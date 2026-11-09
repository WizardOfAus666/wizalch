package com.alchmatepro;

import com.alchmatepro.FilterCriteria.MembershipFilter;
import com.alchmatepro.PriceValidator.ResolvedPurchasePrice;
import com.alchmatepro.ProviderStatusSnapshot.ConnectionState;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Central market intelligence orchestrator.
 * Manages background polling, provider failover (AlchMate -> OSRS Wiki API -> Cache),
 * item enrichment, profit calculation, smart recommendations, filtering, sorting, and alerts.
 */
@Slf4j
@Singleton
public class MarketDataService
{
	public static final int NATURE_RUNE_ITEM_ID = 561;
	public static final int FIRE_RUNE_ITEM_ID = 554;

	private final AlchMateDataProvider alchMateDataProvider;
	private final WikiPriceDataProvider wikiPriceDataProvider;
	private final ItemMetadataService itemMetadataService;
	private final CacheService cacheService;
	private final ProfitCalculator profitCalculator;
	private final PriceValidator priceValidator;
	private final RecommendationEngine recommendationEngine;
	private final AlertService alertService;
	private final AlchMateProConfig config;

	private final Map<Integer, ItemMetadata> currentMetadataMap = new ConcurrentHashMap<>();
	private final Map<Integer, MarketPrice> currentPricesMap = new ConcurrentHashMap<>();
	private final Map<Integer, AlchMateItem> itemsById = new ConcurrentHashMap<>();
	private volatile List<AlchMateItem> allAnalysedItems = Collections.emptyList();

	private final List<Runnable> dataUpdateListeners = new CopyOnWriteArrayList<>();
	private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

	private ScheduledExecutorService executorService;
	private ScheduledFuture<?> scheduledRefreshTask;

	@Getter
	private volatile ProviderStatusSnapshot statusSnapshot;

	@Getter
	private volatile long effectiveNatureRuneCost = 150L;

	@Getter
	private volatile boolean usingLiveNatureRunePrice = true;

	@Getter
	private volatile long effectiveFireRuneCostPerCast = 0L;

	// Runtime settings (initialised from AlchMateProConfig and updatable via SettingsPanel)
	@Getter
	private volatile boolean runtimeUseLiveNatureRune = true;
	@Getter
	private volatile int runtimeManualNatureRuneCost = 150;
	@Getter
	private volatile boolean runtimeUseFireStaff = true;
	@Getter
	private volatile int runtimeManualFireRuneCost = 20;
	@Getter
	private volatile PriceCalculationMethod runtimePriceMethod = PriceCalculationMethod.INSTANT_BUY_HIGH;
	@Getter
	private volatile int runtimeRefreshIntervalSec = 60;
	@Getter
	private volatile int runtimeMaxDataAgeMin = 120;
	@Getter
	private volatile int runtimeMinProfit = 1;
	@Getter
	private volatile int runtimeMinRoi = 0;
	@Getter
	private volatile int runtimeMinVolume = 0;
	@Getter
	private volatile int runtimeMaxPurchaseCost = 0;
	@Getter
	private volatile int runtimeDefaultQuantity = 100;
	@Getter
	private volatile SortOption runtimeDefaultSorting = SortOption.HIGHEST_PROFIT_PER_ALCH;
	@Getter
	private volatile boolean runtimeEnableNotifications = true;
	@Getter
	private volatile boolean runtimeEnableAutoRefresh = true;
	@Getter
	private volatile boolean runtimeShowMembers = true;
	@Getter
	private volatile boolean runtimeShowUnprofitable = true;

	@Inject
	public MarketDataService(
		AlchMateDataProvider alchMateDataProvider,
		WikiPriceDataProvider wikiPriceDataProvider,
		ItemMetadataService itemMetadataService,
		CacheService cacheService,
		ProfitCalculator profitCalculator,
		PriceValidator priceValidator,
		RecommendationEngine recommendationEngine,
		AlertService alertService,
		AlchMateProConfig config)
	{
		this.alchMateDataProvider = alchMateDataProvider;
		this.wikiPriceDataProvider = wikiPriceDataProvider;
		this.itemMetadataService = itemMetadataService;
		this.cacheService = cacheService;
		this.profitCalculator = profitCalculator;
		this.priceValidator = priceValidator;
		this.recommendationEngine = recommendationEngine;
		this.alertService = alertService;
		this.config = config;
		syncSettingsFromConfig();

		this.statusSnapshot = ProviderStatusSnapshot.builder()
			.activeProviderName(wikiPriceDataProvider.getProviderName())
			.primaryProviderName(alchMateDataProvider.getProviderName())
			.primaryProviderStatusNote(alchMateDataProvider.getStatusSummary())
			.secondaryProviderName(wikiPriceDataProvider.getProviderName())
			.connectionState(ConnectionState.REFRESHING)
			.cacheStatusDescription(cacheService.getCacheStatusSummary())
			.natureRuneLivePrice(150L)
			.fireRuneLivePrice(4L)
			.build();
	}

	/**
	 * Synchronises runtime settings from RuneLite's {@link AlchMateProConfig} interface.
	 */
	public final synchronized void syncSettingsFromConfig()
	{
		if (config == null)
		{
			return;
		}
		this.runtimeUseLiveNatureRune = config.useLiveNatureRunePrice();
		this.runtimeManualNatureRuneCost = Math.max(0, config.natureRuneCost());
		this.runtimeUseFireStaff = config.useFireStaff();
		this.runtimeManualFireRuneCost = Math.max(0, config.fireRuneCostPerCast());
		this.runtimePriceMethod = config.priceCalculationMethod() != null
			? config.priceCalculationMethod()
			: PriceCalculationMethod.INSTANT_BUY_HIGH;
		this.runtimeRefreshIntervalSec = Math.max(30, config.refreshIntervalSeconds());
		this.runtimeMaxDataAgeMin = Math.max(5, config.maxDataAgeMinutes());
		this.runtimeMinProfit = config.minimumProfit();
		this.runtimeMinRoi = config.minimumRoiPercent();
		this.runtimeMinVolume = Math.max(0, config.minimumTradeVolume());
		this.runtimeMaxPurchaseCost = Math.max(0, config.maximumPurchaseCost());
		this.runtimeDefaultQuantity = Math.max(1, config.defaultQuantity());
		this.runtimeDefaultSorting = config.defaultSorting() != null
			? config.defaultSorting()
			: SortOption.HIGHEST_PROFIT_PER_ALCH;
		this.runtimeEnableNotifications = config.enableNotifications();
		this.runtimeEnableAutoRefresh = config.enableAutoRefresh();
		this.runtimeShowMembers = config.showMembersItems();
		this.runtimeShowUnprofitable = config.showUnprofitableItems();
	}

	/**
	 * Applies user settings directly from {@link SettingsPanel} and immediately recalculates all items.
	 */
	public synchronized void applyRuntimeSettings(
		boolean useLiveNatureRune,
		int manualNatureRuneCost,
		boolean useFireStaff,
		int manualFireRuneCost,
		PriceCalculationMethod priceMethod,
		int refreshIntervalSec,
		int maxDataAgeMin,
		int minProfit,
		int minRoi,
		int minVolume,
		int maxPurchaseCost,
		int defaultQuantity,
		SortOption defaultSorting,
		boolean enableNotifications,
		boolean enableAutoRefresh,
		boolean showMembers,
		boolean showUnprofitable)
	{
		this.runtimeUseLiveNatureRune = useLiveNatureRune;
		this.runtimeManualNatureRuneCost = Math.max(0, manualNatureRuneCost);
		this.runtimeUseFireStaff = useFireStaff;
		this.runtimeManualFireRuneCost = Math.max(0, manualFireRuneCost);
		this.runtimePriceMethod = priceMethod != null ? priceMethod : PriceCalculationMethod.INSTANT_BUY_HIGH;
		this.runtimeRefreshIntervalSec = Math.max(30, refreshIntervalSec);
		this.runtimeMaxDataAgeMin = Math.max(5, maxDataAgeMin);
		this.runtimeMinProfit = minProfit;
		this.runtimeMinRoi = minRoi;
		this.runtimeMinVolume = Math.max(0, minVolume);
		this.runtimeMaxPurchaseCost = Math.max(0, maxPurchaseCost);
		this.runtimeDefaultQuantity = Math.max(1, defaultQuantity);
		this.runtimeDefaultSorting = defaultSorting != null ? defaultSorting : SortOption.HIGHEST_PROFIT_PER_ALCH;
		this.runtimeEnableNotifications = enableNotifications;
		this.runtimeEnableAutoRefresh = enableAutoRefresh;
		this.runtimeShowMembers = showMembers;
		this.runtimeShowUnprofitable = showUnprofitable;

		rescheduleAutoRefresh();
		recalculateAllItems();
	}

	/**
	 * Starts the background scheduler and triggers an immediate asynchronous data refresh.
	 */
	public synchronized void startUp()
	{
		if (executorService == null || executorService.isShutdown())
		{
			executorService = Executors.newSingleThreadScheduledExecutor(r -> {
				Thread t = new Thread(r, "AlchMatePro-MarketWorker");
				t.setDaemon(true);
				return t;
			});
		}
		rescheduleAutoRefresh();
		refreshAsync();
	}

	/**
	 * Cleanly cancels scheduled background tasks and shuts down the executor on plugin stop.
	 */
	public synchronized void shutDown()
	{
		if (scheduledRefreshTask != null)
		{
			scheduledRefreshTask.cancel(true);
			scheduledRefreshTask = null;
		}
		if (executorService != null)
		{
			executorService.shutdownNow();
			executorService = null;
		}
		refreshInProgress.set(false);
	}

	/**
	 * Updates the periodic background refresh schedule when configuration changes.
	 */
	public synchronized void rescheduleAutoRefresh()
	{
		if (scheduledRefreshTask != null)
		{
			scheduledRefreshTask.cancel(false);
			scheduledRefreshTask = null;
		}
		if (executorService == null || executorService.isShutdown())
		{
			return;
		}
		if (runtimeEnableAutoRefresh)
		{
			int intervalSec = Math.max(30, runtimeRefreshIntervalSec);
			scheduledRefreshTask = executorService.scheduleWithFixedDelay(
				this::refreshSynchronouslySafe,
				intervalSec,
				intervalSec,
				TimeUnit.SECONDS
			);
		}
	}

	/**
	 * Triggers a non-blocking asynchronous market refresh on the background worker thread.
	 */
	public void refreshAsync()
	{
		if (executorService == null || executorService.isShutdown())
		{
			startUp();
			return;
		}
		executorService.submit(this::refreshSynchronouslySafe);
	}

	private void refreshSynchronouslySafe()
	{
		try
		{
			refreshMarketDataNow();
		}
		catch (Throwable t)
		{
			log.warn("Unexpected error during background market refresh: {}", t.getMessage());
		}
	}

	/**
	 * Performs a full market data refresh (callable directly in unit tests or on the background thread).
	 */
	public void refreshMarketDataNow()
	{
		if (!refreshInProgress.compareAndSet(false, true))
		{
			return;
		}

		long attemptEpochSec = Instant.now().getEpochSecond();
		updateConnectionState(ConnectionState.REFRESHING, null);
		notifyListeners();

		String activeProvider = wikiPriceDataProvider.getProviderName();
		ConnectionState finalState = ConnectionState.LIVE;
		String errorMsg = null;
		long successEpochSec = statusSnapshot.getLastSuccessfulUpdateEpochSec();

		try
		{
			// 1. Fetch item metadata (/mapping)
			Map<Integer, ItemMetadata> metadata = itemMetadataService.fetchItemMetadata();
			if (metadata != null && !metadata.isEmpty())
			{
				currentMetadataMap.clear();
				currentMetadataMap.putAll(metadata);
			}

			// 2. Try primary provider (AlchMateDataProvider), then fallback to WikiPriceDataProvider
			Map<Integer, MarketPrice> fetchedPrices = null;
			try
			{
				fetchedPrices = alchMateDataProvider.fetchMarketPrices();
				if (fetchedPrices != null && !fetchedPrices.isEmpty())
				{
					activeProvider = alchMateDataProvider.getProviderName();
					finalState = ConnectionState.LIVE;
				}
			}
			catch (IOException primaryEx)
			{
				log.debug("Primary AlchMate provider unavailable ({}), using OSRS Wiki Real-Time API", primaryEx.getMessage());
			}

			if (fetchedPrices == null || fetchedPrices.isEmpty())
			{
				fetchedPrices = wikiPriceDataProvider.fetchMarketPrices();
				activeProvider = wikiPriceDataProvider.getProviderName();
				finalState = ConnectionState.LIVE;
			}

			if (fetchedPrices != null && !fetchedPrices.isEmpty())
			{
				currentPricesMap.clear();
				currentPricesMap.putAll(fetchedPrices);
				cacheService.savePricesCache(fetchedPrices);
				successEpochSec = Instant.now().getEpochSecond();
			}
		}
		catch (Exception ex)
		{
			errorMsg = ex.getMessage();
			log.warn("Live market data refresh failed: {}", errorMsg);

			// Attempt fallback to disk/memory cache without ever fabricating prices
			if (currentMetadataMap.isEmpty())
			{
				Map<Integer, ItemMetadata> cachedMeta = cacheService.loadMetadataCache();
				if (cachedMeta != null && !cachedMeta.isEmpty())
				{
					currentMetadataMap.putAll(cachedMeta);
				}
			}
			if (currentPricesMap.isEmpty())
			{
				Map<Integer, MarketPrice> cachedPrices = cacheService.loadPricesCache();
				if (cachedPrices != null && !cachedPrices.isEmpty())
				{
					currentPricesMap.putAll(cachedPrices);
				}
			}

			if (!currentMetadataMap.isEmpty() && !currentPricesMap.isEmpty())
			{
				finalState = ConnectionState.CACHED;
				activeProvider = "Local Cache (" + wikiPriceDataProvider.getProviderName() + ")";
			}
			else
			{
				finalState = ConnectionState.OFFLINE_ERROR;
			}
		}
		finally
		{
			try
			{
				rebuildAnalysedItems(
					activeProvider,
					finalState,
					successEpochSec,
					attemptEpochSec,
					errorMsg,
					Instant.now().getEpochSecond()
				);
			}
			finally
			{
				refreshInProgress.set(false);
				notifyListeners();
			}
		}
	}

	/**
	 * Recomputes all item calculations and recommendations immediately using already-loaded
	 * market data (e.g. when the user changes Nature Rune cost, Fire Staff toggle, or Price Method).
	 */
	public void recalculateAllItems()
	{
		rebuildAnalysedItems(
			statusSnapshot.getActiveProviderName(),
			statusSnapshot.getConnectionState(),
			statusSnapshot.getLastSuccessfulUpdateEpochSec(),
			statusSnapshot.getLastAttemptedUpdateEpochSec(),
			statusSnapshot.getLastErrorMessage(),
			Instant.now().getEpochSecond()
		);
		notifyListeners();
	}

	/**
	 * Directly loads metadata and market prices (used for deterministic unit testing).
	 */
	public void loadDirectData(
		Map<Integer, ItemMetadata> metadataMap,
		Map<Integer, MarketPrice> pricesMap,
		long nowEpochSec)
	{
		currentMetadataMap.clear();
		if (metadataMap != null)
		{
			currentMetadataMap.putAll(metadataMap);
		}
		currentPricesMap.clear();
		if (pricesMap != null)
		{
			currentPricesMap.putAll(pricesMap);
		}
		rebuildAnalysedItems(
			wikiPriceDataProvider.getProviderName(),
			ConnectionState.LIVE,
			nowEpochSec,
			nowEpochSec,
			null,
			nowEpochSec
		);
	}

	private synchronized void rebuildAnalysedItems(
		String activeProvider,
		ConnectionState connectionState,
		long lastSuccessEpochSec,
		long lastAttemptEpochSec,
		String errorMsg,
		long nowEpochSec)
	{
		PriceCalculationMethod priceMethod = runtimePriceMethod != null
			? runtimePriceMethod
			: PriceCalculationMethod.INSTANT_BUY_HIGH;

		long liveNatPrice = resolveLiveRunePrice(NATURE_RUNE_ITEM_ID, priceMethod, runtimeManualNatureRuneCost, nowEpochSec);
		long liveFirePrice = resolveLiveRunePrice(FIRE_RUNE_ITEM_ID, priceMethod, 4L, nowEpochSec);

		boolean hasLiveNatInMap = currentPricesMap.containsKey(NATURE_RUNE_ITEM_ID) && liveNatPrice > 0;
		this.usingLiveNatureRunePrice = runtimeUseLiveNatureRune && hasLiveNatInMap;
		this.effectiveNatureRuneCost = (runtimeUseLiveNatureRune && liveNatPrice > 0)
			? liveNatPrice
			: runtimeManualNatureRuneCost;

		this.effectiveFireRuneCostPerCast = runtimeUseFireStaff ? 0L : runtimeManualFireRuneCost;

		int maxAgeMinutes = Math.max(5, runtimeMaxDataAgeMin);
		int defaultQty = Math.max(1, runtimeDefaultQuantity);
		int minTargetProfit = Math.max(0, runtimeMinProfit);

		List<AlchMateItem> builtItems = new ArrayList<>(currentMetadataMap.size());
		Map<Integer, AlchMateItem> newItemsById = new HashMap<>(currentMetadataMap.size());

		int validPricesCount = 0;
		int profitableCount = 0;
		int staleCount = 0;
		int failedOrMissingCount = 0;

		for (ItemMetadata metadata : currentMetadataMap.values())
		{
			if (metadata == null || !metadata.isAlchable())
			{
				continue;
			}

			int itemId = metadata.getId();
			MarketPrice marketPrice = currentPricesMap.get(itemId);
			ResolvedPurchasePrice resolvedPrice = priceValidator.resolvePurchasePrice(
				marketPrice,
				priceMethod,
				maxAgeMinutes,
				nowEpochSec
			);

			if (!resolvedPrice.isValid())
			{
				failedOrMissingCount++;
				continue;
			}

			validPricesCount++;
			if (resolvedPrice.isStale())
			{
				staleCount++;
			}

			AlchCalculation calculation = profitCalculator.calculate(
				itemId,
				metadata.getHighAlchValue(),
				resolvedPrice.getPriceGp(),
				effectiveNatureRuneCost,
				effectiveFireRuneCostPerCast,
				0L,
				defaultQty,
				metadata.getBuyLimit(),
				minTargetProfit
			);

			if (!calculation.isValid())
			{
				failedOrMissingCount++;
				continue;
			}

			if (calculation.isProfitable())
			{
				profitableCount++;
			}

			AlchRecommendation recommendation = recommendationEngine.evaluate(
				metadata,
				marketPrice,
				resolvedPrice,
				calculation
			);

			AlchMateItem alchMateItem = AlchMateItem.builder()
				.metadata(metadata)
				.marketPrice(marketPrice)
				.calculation(calculation)
				.recommendation(recommendation)
				.estimatedPurchasePrice(resolvedPrice.getPriceGp())
				.priceSourceType(resolvedPrice.getSourceType())
				.stale(resolvedPrice.isStale())
				.priceAgeSeconds(resolvedPrice.getAgeSeconds())
				.build();

			builtItems.add(alchMateItem);
			newItemsById.put(itemId, alchMateItem);
		}

		// Default ranking: highest profit per alch descending
		builtItems.sort(Comparator.comparingLong(AlchMateItem::getNetProfitPerAlch).reversed());

		this.allAnalysedItems = Collections.unmodifiableList(builtItems);
		this.itemsById.clear();
		this.itemsById.putAll(newItemsById);

		this.statusSnapshot = ProviderStatusSnapshot.builder()
			.activeProviderName(activeProvider)
			.primaryProviderName(alchMateDataProvider.getProviderName())
			.primaryProviderStatusNote(alchMateDataProvider.getStatusSummary())
			.secondaryProviderName(wikiPriceDataProvider.getProviderName())
			.connectionState(connectionState)
			.lastSuccessfulUpdateEpochSec(lastSuccessEpochSec)
			.lastAttemptedUpdateEpochSec(lastAttemptEpochSec)
			.totalItemsLoaded(currentMetadataMap.size())
			.validPricesCount(validPricesCount)
			.profitableItemsCount(profitableCount)
			.stalePricesCount(staleCount)
			.failedOrMissingItemsCount(failedOrMissingCount)
			.cacheStatusDescription(cacheService.getCacheStatusSummary())
			.lastErrorMessage(errorMsg)
			.natureRuneLivePrice(liveNatPrice)
			.fireRuneLivePrice(liveFirePrice)
			.build();

		// Evaluate watchlist profit alerts
		if (alertService != null && !newItemsById.isEmpty())
		{
			alertService.evaluateAlerts(newItemsById, nowEpochSec);
		}
	}

	/**
	 * Filters and sorts all currently analysed items according to the supplied {@link FilterCriteria}.
	 */
	public List<AlchMateItem> filterAndSort(FilterCriteria criteria)
	{
		List<AlchMateItem> snapshot = this.allAnalysedItems;
		if (snapshot.isEmpty())
		{
			return Collections.emptyList();
		}

		FilterCriteria effective = criteria != null ? criteria : FilterCriteria.builder().build();
		String query = effective.getSearchQuery() != null
			? effective.getSearchQuery().trim().toLowerCase(Locale.ROOT)
			: "";

		boolean configShowMembers = runtimeShowMembers;
		boolean configShowUnprofitable = runtimeShowUnprofitable;

		List<AlchMateItem> filtered = snapshot.stream()
			.filter(AlchMateItem::hasValidPrice)
			.filter(item -> {
				if (!query.isEmpty())
				{
					String nameLower = item.getName().toLowerCase(Locale.ROOT);
					String idStr = String.valueOf(item.getId());
					if (!nameLower.contains(query) && !idStr.equals(query))
					{
						return false;
					}
				}
				if (!configShowMembers && item.isMembers())
				{
					return false;
				}
				if (effective.getMembershipFilter() == MembershipFilter.MEMBERS_ONLY && !item.isMembers())
				{
					return false;
				}
				if (effective.getMembershipFilter() == MembershipFilter.FREE_TO_PLAY_ONLY && item.isMembers())
				{
					return false;
				}
				if ((!configShowUnprofitable || effective.isProfitableOnly() || effective.isHideLosses())
					&& item.getNetProfitPerAlch() <= 0)
				{
					return false;
				}
				if (effective.getMinProfitGp() != null && item.getNetProfitPerAlch() < effective.getMinProfitGp())
				{
					return false;
				}
				if (effective.getMinRoiPercent() != null && item.getRoiPercent() < effective.getMinRoiPercent())
				{
					return false;
				}
				if (effective.getMaxPurchasePriceGp() != null
					&& effective.getMaxPurchasePriceGp() > 0
					&& item.getEstimatedPurchasePrice() > effective.getMaxPurchasePriceGp())
				{
					return false;
				}
				if (effective.getMinTradeVolume1h() > 0 && item.getVolume1h() < effective.getMinTradeVolume1h())
				{
					return false;
				}
				if (effective.getMinBuyLimit() > 0 && item.getBuyLimit() < effective.getMinBuyLimit())
				{
					return false;
				}
				if (effective.isHideStaleData() && item.isStale())
				{
					return false;
				}
				if (effective.isHideInactiveItems() && item.getVolume1h() <= 0 && item.getVolume5m() <= 0)
				{
					return false;
				}
				if (effective.getCategoryFilter() != null)
				{
					if (item.getRecommendation() == null
						|| !item.getRecommendation().matchesCategory(effective.getCategoryFilter()))
					{
						return false;
					}
				}
				return true;
			})
			.collect(Collectors.toCollection(ArrayList::new));

		SortOption sortOption = effective.getSortOption() != null
			? effective.getSortOption()
			: SortOption.HIGHEST_PROFIT_PER_ALCH;

		Comparator<AlchMateItem> comparator;
		switch (sortOption)
		{
			case HIGHEST_ROI:
				comparator = Comparator.comparingDouble(AlchMateItem::getRoiPercent).reversed()
					.thenComparing(Comparator.comparingLong(AlchMateItem::getNetProfitPerAlch).reversed());
				break;
			case HIGHEST_PROFIT_100:
				comparator = Comparator.comparingLong((AlchMateItem i) -> i.getCalculation().getProfitPer100Alchs()).reversed();
				break;
			case HIGHEST_PROFIT_1000:
				comparator = Comparator.comparingLong((AlchMateItem i) -> i.getCalculation().getProfitPer1000Alchs()).reversed();
				break;
			case HIGHEST_VOLUME:
				comparator = Comparator.comparingInt(AlchMateItem::getVolume1h).reversed()
					.thenComparing(Comparator.comparingLong(AlchMateItem::getNetProfitPerAlch).reversed());
				break;
			case LOWEST_INVESTMENT:
				comparator = Comparator.comparingLong(AlchMateItem::getEstimatedPurchasePrice)
					.thenComparing(Comparator.comparingLong(AlchMateItem::getNetProfitPerAlch).reversed());
				break;
			case HIGHEST_ALCH_VALUE:
				comparator = Comparator.comparingInt(AlchMateItem::getHighAlchValue).reversed()
					.thenComparing(Comparator.comparingLong(AlchMateItem::getNetProfitPerAlch).reversed());
				break;
			case HIGHEST_BUY_LIMIT:
				comparator = Comparator.comparingInt(AlchMateItem::getBuyLimit).reversed()
					.thenComparing(Comparator.comparingLong(AlchMateItem::getNetProfitPerAlch).reversed());
				break;
			case HIGHEST_PROFIT_PER_ALCH:
			default:
				comparator = Comparator.comparingLong(AlchMateItem::getNetProfitPerAlch).reversed()
					.thenComparing(Comparator.comparingDouble(AlchMateItem::getRoiPercent).reversed());
				break;
		}

		filtered.sort(comparator);
		return filtered;
	}

	public List<AlchMateItem> getAllAnalysedItems()
	{
		return allAnalysedItems;
	}

	public Optional<AlchMateItem> getItemById(int itemId)
	{
		return Optional.ofNullable(itemsById.get(itemId));
	}

	public boolean isRefreshInProgress()
	{
		return refreshInProgress.get();
	}

	public void addDataUpdateListener(Runnable listener)
	{
		if (listener != null)
		{
			dataUpdateListeners.add(listener);
		}
	}

	private long resolveLiveRunePrice(
		int runeItemId,
		PriceCalculationMethod method,
		long fallbackPrice,
		long nowEpochSec)
	{
		MarketPrice mp = currentPricesMap.get(runeItemId);
		if (mp == null)
		{
			return fallbackPrice;
		}
		ResolvedPurchasePrice resolved = priceValidator.resolvePurchasePrice(mp, method, 1440, nowEpochSec);
		if (resolved != null && resolved.isValid() && resolved.getPriceGp() > 0)
		{
			return resolved.getPriceGp();
		}
		if (mp.getInstantBuyPrice() != null && mp.getInstantBuyPrice() > 0)
		{
			return mp.getInstantBuyPrice();
		}
		if (mp.getAvgHighPrice5m() != null && mp.getAvgHighPrice5m() > 0)
		{
			return mp.getAvgHighPrice5m();
		}
		if (mp.getAvgHighPrice1h() != null && mp.getAvgHighPrice1h() > 0)
		{
			return mp.getAvgHighPrice1h();
		}
		if (mp.getInstantSellPrice() != null && mp.getInstantSellPrice() > 0)
		{
			return mp.getInstantSellPrice();
		}
		return fallbackPrice;
	}

	public MarketPrice getNatureRuneMarketPrice()
	{
		return currentPricesMap.get(NATURE_RUNE_ITEM_ID);
	}

	private void updateConnectionState(ConnectionState state, String error)
	{
		this.statusSnapshot = ProviderStatusSnapshot.builder()
			.activeProviderName(statusSnapshot.getActiveProviderName())
			.primaryProviderName(alchMateDataProvider.getProviderName())
			.primaryProviderStatusNote(alchMateDataProvider.getStatusSummary())
			.secondaryProviderName(wikiPriceDataProvider.getProviderName())
			.connectionState(state)
			.lastSuccessfulUpdateEpochSec(statusSnapshot.getLastSuccessfulUpdateEpochSec())
			.lastAttemptedUpdateEpochSec(Instant.now().getEpochSecond())
			.totalItemsLoaded(statusSnapshot.getTotalItemsLoaded())
			.validPricesCount(statusSnapshot.getValidPricesCount())
			.profitableItemsCount(statusSnapshot.getProfitableItemsCount())
			.stalePricesCount(statusSnapshot.getStalePricesCount())
			.failedOrMissingItemsCount(statusSnapshot.getFailedOrMissingItemsCount())
			.cacheStatusDescription(cacheService.getCacheStatusSummary())
			.lastErrorMessage(error)
			.natureRuneLivePrice(statusSnapshot.getNatureRuneLivePrice())
			.fireRuneLivePrice(statusSnapshot.getFireRuneLivePrice())
			.build();
	}

	private void notifyListeners()
	{
		for (Runnable listener : dataUpdateListeners)
		{
			if (SwingUtilities.isEventDispatchThread())
			{
				try
				{
					listener.run();
				}
				catch (Exception ex)
				{
					log.debug("UI listener error: {}", ex.getMessage());
				}
			}
			else
			{
				SwingUtilities.invokeLater(() -> {
					try
					{
						listener.run();
					}
					catch (Exception ex)
					{
						log.debug("UI listener error on EDT: {}", ex.getMessage());
					}
				});
			}
		}
	}
}
