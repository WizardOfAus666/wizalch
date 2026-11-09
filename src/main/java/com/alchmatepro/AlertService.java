package com.alchmatepro;

import com.alchmatepro.AlertEvent.AlertType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.Notifier;
import net.runelite.client.util.QuantityFormatter;

/**
 * Evaluates profit, ROI, profitability transition, and price-movement alerts (Section 13).
 * Integrates with RuneLite's {@link Notifier} while enforcing per-item/per-type cooldowns
 * to prevent notification spam.
 */
@Slf4j
@Singleton
public class AlertService
{
	private static final int MAX_RECENT_ALERTS = 50;

	private final Notifier notifier;
	private final AlchMateProConfig config;
	private final WatchlistService watchlistService;

	/** Maps "itemId:AlertType" -> last triggered epoch seconds. */
	private final Map<String, Long> lastTriggeredEpochMap = new ConcurrentHashMap<>();

	/** Maps itemId -> previous netProfitPerAlch to detect unprofitable -> profitable transitions. */
	private final Map<Integer, Long> previousProfitMap = new ConcurrentHashMap<>();

	private final List<AlertEvent> recentAlerts = new CopyOnWriteArrayList<>();

	@Inject
	public AlertService(Notifier notifier, AlchMateProConfig config, WatchlistService watchlistService)
	{
		this.notifier = notifier;
		this.config = config;
		this.watchlistService = watchlistService;
	}

	/**
	 * Evaluates all watched items against user-configured and global alert rules.
	 */
	public List<AlertEvent> evaluateAlerts(Map<Integer, AlchMateItem> itemsById)
	{
		return evaluateAlerts(itemsById, Instant.now().getEpochSecond());
	}

	public List<AlertEvent> evaluateAlerts(Map<Integer, AlchMateItem> itemsById, long nowEpochSec)
	{
		if (itemsById == null || itemsById.isEmpty() || watchlistService == null)
		{
			return Collections.emptyList();
		}

		boolean notificationsEnabled = config == null || config.enableNotifications();
		long cooldownSeconds = config != null
			? Math.max(60L, (long) config.alertCooldownMinutes() * 60L)
			: 900L;

		int globalProfitTarget = config != null ? config.alertProfitThresholdGp() : 1000;
		double globalRoiTarget = config != null ? config.alertRoiThresholdPercent() : 5.0;
		boolean alertOnBecomeProfitable = config == null || config.alertOnBecomeProfitable();
		int profitFloorGp = config != null ? config.alertProfitDropBelowGp() : 0;
		int significantPriceChangePct = config != null ? config.alertPriceChangePercent() : 10;

		List<AlertEvent> fired = new ArrayList<>();

		for (WatchlistEntry entry : watchlistService.getAllEntries())
		{
			if (!entry.isAlertEnabled())
			{
				continue;
			}
			AlchMateItem item = itemsById.get(entry.getItemId());
			if (item == null || !item.hasValidPrice() || item.isStale())
			{
				continue;
			}

			int itemId = item.getId();
			long profit = item.getNetProfitPerAlch();
			double roi = item.getRoiPercent();
			long buyPrice = item.getEstimatedPurchasePrice();
			Long prevProfit = previousProfitMap.put(itemId, profit);

			// 1. Profit exceeds target (use entry target if > 0, else global threshold)
			int effectiveProfitTarget = entry.getTargetProfitGp() > 0 ? entry.getTargetProfitGp() : globalProfitTarget;
			if (effectiveProfitTarget > 0 && profit >= effectiveProfitTarget)
			{
				tryFire(
					fired,
					item,
					AlertType.PROFIT_THRESHOLD_EXCEEDED,
					String.format(
						"[WIZALCH] %s profit reached +%s GP/alch (target: %s GP)",
						item.getName(),
						QuantityFormatter.formatNumber(profit),
						QuantityFormatter.formatNumber(effectiveProfitTarget)
					),
					nowEpochSec,
					cooldownSeconds,
					notificationsEnabled
				);
			}

			// 2. ROI exceeds target
			double effectiveRoiTarget = entry.getMinimumRoiPercent() > 0 ? entry.getMinimumRoiPercent() : globalRoiTarget;
			if (effectiveRoiTarget > 0 && roi >= effectiveRoiTarget && profit > 0)
			{
				tryFire(
					fired,
					item,
					AlertType.ROI_THRESHOLD_EXCEEDED,
					String.format(
						"[WIZALCH] %s ROI reached %.1f%% (+%s GP/alch)",
						item.getName(),
						roi,
						QuantityFormatter.formatNumber(profit)
					),
					nowEpochSec,
					cooldownSeconds,
					notificationsEnabled
				);
			}

			// 3. Item became profitable (was <= 0, now > 0)
			if (alertOnBecomeProfitable && prevProfit != null && prevProfit <= 0 && profit > 0)
			{
				tryFire(
					fired,
					item,
					AlertType.BECAME_PROFITABLE,
					String.format(
						"[WIZALCH] %s is now profitable (+%s GP/alch, %.1f%% ROI)!",
						item.getName(),
						QuantityFormatter.formatNumber(profit),
						roi
					),
					nowEpochSec,
					cooldownSeconds,
					notificationsEnabled
				);
			}

			// 4. Profit dropped below threshold (when previously above it)
			if (prevProfit != null && prevProfit > profitFloorGp && profit < profitFloorGp)
			{
				tryFire(
					fired,
					item,
					AlertType.PROFIT_DROPPED_BELOW,
					String.format(
						"[WIZALCH] %s profit dropped to %s GP/alch (below %s GP)",
						item.getName(),
						QuantityFormatter.formatNumber(profit),
						QuantityFormatter.formatNumber(profitFloorGp)
					),
					nowEpochSec,
					cooldownSeconds,
					notificationsEnabled
				);
			}

			// 5. Significant price change relative to baseline
			long baselinePrice = entry.getBaselinePurchasePrice();
			if (baselinePrice > 0 && significantPriceChangePct > 0)
			{
				double pctDelta = Math.abs((double) (buyPrice - baselinePrice) / (double) baselinePrice) * 100.0;
				if (pctDelta >= significantPriceChangePct)
				{
					String direction = buyPrice < baselinePrice ? "dropped" : "rose";
					tryFire(
						fired,
						item,
						AlertType.SIGNIFICANT_PRICE_CHANGE,
						String.format(
							"[WIZALCH] %s buy price %s %.1f%% to %s GP",
							item.getName(),
							direction,
							pctDelta,
							QuantityFormatter.formatNumber(buyPrice)
						),
						nowEpochSec,
						cooldownSeconds,
						notificationsEnabled
					);
				}
			}
		}

		return fired;
	}

	private void tryFire(
		List<AlertEvent> firedList,
		AlchMateItem item,
		AlertType type,
		String message,
		long nowEpochSec,
		long cooldownSeconds,
		boolean notificationsEnabled)
	{
		String cooldownKey = item.getId() + ":" + type.name();
		Long lastEpoch = lastTriggeredEpochMap.get(cooldownKey);
		if (lastEpoch != null && (nowEpochSec - lastEpoch) < cooldownSeconds)
		{
			return;
		}

		lastTriggeredEpochMap.put(cooldownKey, nowEpochSec);

		AlertEvent event = AlertEvent.builder()
			.itemId(item.getId())
			.itemName(item.getName())
			.alertType(type)
			.message(message)
			.triggeredAtEpochSec(nowEpochSec)
			.netProfitPerAlch(item.getNetProfitPerAlch())
			.roiPercent(item.getRoiPercent())
			.purchasePrice(item.getEstimatedPurchasePrice())
			.build();

		firedList.add(event);
		recentAlerts.add(0, event);
		while (recentAlerts.size() > MAX_RECENT_ALERTS)
		{
			recentAlerts.remove(recentAlerts.size() - 1);
		}

		if (notificationsEnabled && notifier != null)
		{
			try
			{
				notifier.notify(message);
			}
			catch (Exception ex)
			{
				log.debug("Failed to dispatch RuneLite notification: {}", ex.getMessage());
			}
		}
	}

	public List<AlertEvent> getRecentAlerts()
	{
		return Collections.unmodifiableList(new ArrayList<>(recentAlerts));
	}

	public void clearAlertHistory()
	{
		recentAlerts.clear();
		lastTriggeredEpochMap.clear();
	}
}
