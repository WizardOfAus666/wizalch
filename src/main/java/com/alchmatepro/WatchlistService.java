package com.alchmatepro;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Manages the user's persistent personal watchlist (Section 12).
 * Persists watchlist entries across game restarts using RuneLite's {@link ConfigManager}.
 */
@Slf4j
@Singleton
public class WatchlistService
{
	private final ConfigManager configManager;
	private final Gson gson;

	private final Map<Integer, WatchlistEntry> entries = new LinkedHashMap<>();
	private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

	@Inject
	public WatchlistService(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
		loadFromConfig();
	}

	public synchronized void loadFromConfig()
	{
		entries.clear();
		if (configManager == null)
		{
			return;
		}
		try
		{
			String rawJson = configManager.getConfiguration(
				AlchMateProConfig.CONFIG_GROUP,
				AlchMateProConfig.WATCHLIST_DATA_KEY
			);
			if (rawJson == null || rawJson.trim().isEmpty())
			{
				return;
			}

			Type listType = new TypeToken<List<WatchlistEntry>>()
			{
			}.getType();
			List<WatchlistEntry> loaded = gson.fromJson(rawJson, listType);
			if (loaded != null)
			{
				for (WatchlistEntry entry : loaded)
				{
					if (entry != null && entry.getItemId() > 0)
					{
						entries.put(entry.getItemId(), entry);
					}
				}
			}
		}
		catch (Exception ex)
		{
			log.warn("Failed to load watchlist configuration, starting with empty watchlist: {}", ex.getMessage());
		}
	}

	public synchronized void saveToConfig()
	{
		if (configManager == null)
		{
			return;
		}
		try
		{
			List<WatchlistEntry> list = new ArrayList<>(entries.values());
			String json = gson.toJson(list);
			configManager.setConfiguration(
				AlchMateProConfig.CONFIG_GROUP,
				AlchMateProConfig.WATCHLIST_DATA_KEY,
				json
			);
		}
		catch (Exception ex)
		{
			log.warn("Failed to persist watchlist to ConfigManager: {}", ex.getMessage());
		}
	}

	public synchronized WatchlistEntry addOrUpdateItem(
		AlchMateItem item,
		int targetProfitGp,
		double minimumRoiPercent)
	{
		if (item == null || item.getId() <= 0)
		{
			return null;
		}
		int itemId = item.getId();
		WatchlistEntry existing = entries.get(itemId);
		long now = Instant.now().getEpochSecond();

		WatchlistEntry updated = WatchlistEntry.builder()
			.itemId(itemId)
			.itemName(item.getName())
			.addedEpochSec(existing != null ? existing.getAddedEpochSec() : now)
			.favourite(existing != null && existing.isFavourite())
			.targetProfitGp(targetProfitGp)
			.minimumRoiPercent(minimumRoiPercent)
			.baselinePurchasePrice(existing != null && existing.getBaselinePurchasePrice() > 0
				? existing.getBaselinePurchasePrice()
				: item.getEstimatedPurchasePrice())
			.baselineProfitPerAlch(existing != null
				? existing.getBaselineProfitPerAlch()
				: item.getNetProfitPerAlch())
			.alertEnabled(existing == null || existing.isAlertEnabled())
			.build();

		entries.put(itemId, updated);
		saveToConfig();
		notifyListeners();
		return updated;
	}

	public synchronized void addItem(AlchMateItem item)
	{
		if (item == null || item.getId() <= 0)
		{
			return;
		}
		int defaultTargetProfit = Math.max(250, (int) Math.max(0L, item.getNetProfitPerAlch()));
		double defaultMinRoi = Math.max(1.0, Math.round(Math.max(0.0, item.getRoiPercent()) * 10.0) / 10.0);
		addOrUpdateItem(item, defaultTargetProfit, defaultMinRoi);
	}

	public synchronized boolean removeItem(int itemId)
	{
		WatchlistEntry removed = entries.remove(itemId);
		if (removed != null)
		{
			saveToConfig();
			notifyListeners();
			return true;
		}
		return false;
	}

	public synchronized void toggleItem(AlchMateItem item)
	{
		if (item == null)
		{
			return;
		}
		if (isWatched(item.getId()))
		{
			removeItem(item.getId());
		}
		else
		{
			addItem(item);
		}
	}

	public synchronized void toggleFavourite(int itemId)
	{
		WatchlistEntry entry = entries.get(itemId);
		if (entry != null)
		{
			entry.setFavourite(!entry.isFavourite());
			saveToConfig();
			notifyListeners();
		}
	}

	public synchronized void toggleAlertEnabled(int itemId)
	{
		WatchlistEntry entry = entries.get(itemId);
		if (entry != null)
		{
			entry.setAlertEnabled(!entry.isAlertEnabled());
			saveToConfig();
			notifyListeners();
		}
	}

	public synchronized void updateTargets(int itemId, int targetProfitGp, double minimumRoiPercent)
	{
		WatchlistEntry entry = entries.get(itemId);
		if (entry != null)
		{
			entry.setTargetProfitGp(targetProfitGp);
			entry.setMinimumRoiPercent(minimumRoiPercent);
			saveToConfig();
			notifyListeners();
		}
	}

	public synchronized void resetBaseline(AlchMateItem currentItem)
	{
		if (currentItem == null)
		{
			return;
		}
		WatchlistEntry entry = entries.get(currentItem.getId());
		if (entry != null)
		{
			entry.setBaselinePurchasePrice(currentItem.getEstimatedPurchasePrice());
			entry.setBaselineProfitPerAlch(currentItem.getNetProfitPerAlch());
			saveToConfig();
			notifyListeners();
		}
	}

	public synchronized void clearAll()
	{
		entries.clear();
		saveToConfig();
		notifyListeners();
	}

	public synchronized boolean isWatched(int itemId)
	{
		return entries.containsKey(itemId);
	}

	public synchronized WatchlistEntry getEntry(int itemId)
	{
		return entries.get(itemId);
	}

	public synchronized int size()
	{
		return entries.size();
	}

	/**
	 * Returns all watchlist entries sorted with favourites first, then by added time descending.
	 */
	public synchronized List<WatchlistEntry> getAllEntries()
	{
		List<WatchlistEntry> list = new ArrayList<>(entries.values());
		list.sort(
			Comparator.comparing(WatchlistEntry::isFavourite).reversed()
				.thenComparing(Comparator.comparingLong(WatchlistEntry::getAddedEpochSec).reversed())
		);
		return list;
	}

	public void addChangeListener(Runnable listener)
	{
		if (listener != null)
		{
			changeListeners.add(listener);
		}
	}

	private void notifyListeners()
	{
		for (Runnable listener : changeListeners)
		{
			try
			{
				listener.run();
			}
			catch (Exception ex)
			{
				log.debug("Watchlist listener error: {}", ex.getMessage());
			}
		}
	}
}
