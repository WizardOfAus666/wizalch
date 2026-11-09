package com.alchmatepro;

import com.alchmatepro.AlchUiComponents.ResponsiveGridLayout;
import com.alchmatepro.AlchUiComponents.VerticalStackLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;

/**
 * Persistent Personal Watchlist and Profit Alert Monitor panel (Sections 12 & 13).
 * Allows users to track favourite items, configure target profit & minimum ROI,
 * compare current prices against baseline, toggle alerts, and view recent alert history.
 */
public class WatchlistPanel extends JPanel
{
	private final WatchlistService watchlistService;
	private final MarketDataService marketDataService;
	private final AlertService alertService;
	private final IconService iconService;
	private final NavigationController navigationController;

	private final JLabel countSummaryLabel = new JLabel("0 watched items");
	private final JPanel watchlistItemsContainer = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
	private final JPanel alertsHistoryContainer = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_XS));

	public WatchlistPanel(
		WatchlistService watchlistService,
		MarketDataService marketDataService,
		AlertService alertService,
		IconService iconService,
		NavigationController navigationController)
	{
		super(new VerticalStackLayout(AlchTheme.SPACE_SM));
		this.watchlistService = watchlistService;
		this.marketDataService = marketDataService;
		this.alertService = alertService;
		this.iconService = iconService;
		this.navigationController = navigationController;

		setBackground(AlchTheme.BG_MAIN);
		setBorder(BorderFactory.createEmptyBorder(
			AlchTheme.SPACE_SM, AlchTheme.SPACE_SM, AlchTheme.SPACE_MD, AlchTheme.SPACE_SM
		));

		add(buildHeaderCard());
		add(buildWatchlistSection());
		add(buildAlertHistorySection());
	}

	private JPanel buildHeaderCard()
	{
		JPanel card = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_XS));
		card.setBackground(AlchTheme.BG_CARD);
		card.setBorder(AlchTheme.createGoldCardBorder());

		JLabel title = AlchTheme.createSectionHeading("PERSONAL WATCHLIST & ALERTS");

		JButton clearAllBtn = AlchTheme.createDangerButton("Clear All");
		clearAllBtn.setToolTipText("Remove all items from your watchlist (requires confirmation)");
		clearAllBtn.addActionListener(e -> confirmAndClearWatchlist());

		countSummaryLabel.setFont(AlchTheme.getBodyFont(false));
		countSummaryLabel.setForeground(AlchTheme.TEXT_SECONDARY);

		card.add(AlchTheme.createSplitRow(title, clearAllBtn));
		card.add(countSummaryLabel);
		return card;
	}

	private JPanel buildWatchlistSection()
	{
		watchlistItemsContainer.setOpaque(false);
		return watchlistItemsContainer;
	}

	private JPanel buildAlertHistorySection()
	{
		JPanel wrapper = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
		wrapper.setBackground(AlchTheme.BG_CARD);
		wrapper.setBorder(AlchTheme.createCardBorder());

		JLabel title = AlchTheme.createSectionHeading("RECENT PROFIT ALERTS");

		JButton clearAlertsBtn = AlchTheme.createSecondaryButton("Clear Log");
		clearAlertsBtn.addActionListener(e -> {
			alertService.clearAlertHistory();
			refreshView();
		});

		alertsHistoryContainer.setOpaque(false);

		wrapper.add(AlchTheme.createSplitRow(title, clearAlertsBtn));
		wrapper.add(alertsHistoryContainer);
		return wrapper;
	}

	private void confirmAndClearWatchlist()
	{
		if (watchlistService.size() == 0)
		{
			return;
		}
		int result = JOptionPane.showConfirmDialog(
			this,
			"Are you sure you want to remove all " + watchlistService.size() + " items from your Watchlist?",
			"Confirm Clear Watchlist",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE
		);
		if (result == JOptionPane.YES_OPTION)
		{
			watchlistService.clearAll();
			refreshView();
		}
	}

	public void refreshView()
	{
		List<WatchlistEntry> entries = watchlistService.getAllEntries();
		countSummaryLabel.setText(entries.size() + " watched item" + (entries.size() == 1 ? "" : "s")
			+ " (persisted across sessions)");

		watchlistItemsContainer.removeAll();
		if (entries.isEmpty())
		{
			JPanel emptyCard = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
			emptyCard.setBackground(AlchTheme.BG_CARD);
			emptyCard.setBorder(AlchTheme.createSpaciousCardBorder());

			JLabel emptyTitle = new JLabel("Your Watchlist is Empty");
			emptyTitle.setFont(AlchTheme.getSectionHeadingFont());
			emptyTitle.setForeground(AlchTheme.TEXT_PRIMARY);

			JTextArea emptySub = AlchUiComponents.createReadOnlyTextArea(
				"Click '☆ Watch' on any item in the Live Alch Scanner or Item Analysis page to track price movements and receive automatic profit alerts.",
				AlchTheme.TEXT_SECONDARY
			);

			emptyCard.add(emptyTitle);
			emptyCard.add(emptySub);
			watchlistItemsContainer.add(emptyCard);
		}
		else
		{
			for (WatchlistEntry entry : entries)
			{
				watchlistItemsContainer.add(buildWatchlistEntryCard(entry));
			}
		}

		// Recent alerts log
		alertsHistoryContainer.removeAll();
		List<AlertEvent> alerts = alertService.getRecentAlerts();
		if (alerts.isEmpty())
		{
			JLabel noAlertsLbl = new JLabel("No alerts triggered yet in this session.");
			noAlertsLbl.setFont(AlchTheme.getBodyFont(false));
			noAlertsLbl.setForeground(AlchTheme.TEXT_MUTED);
			alertsHistoryContainer.add(noAlertsLbl);
		}
		else
		{
			int shown = Math.min(8, alerts.size());
			for (int i = 0; i < shown; i++)
			{
				AlertEvent ev = alerts.get(i);
				String text = String.format(
					"[%s] %s",
					AlchTheme.formatEpochTimestamp(ev.getTriggeredAtEpochSec()),
					ev.getMessage().replace("[WIZALCH] ", "").replace("[AlchMate Pro] ", "")
				);
				JTextArea alertArea = AlchUiComponents.createReadOnlyTextArea(text, AlchTheme.ACCENT_GOLD);
				alertArea.setBackground(AlchTheme.BG_PANEL);
				alertArea.setOpaque(true);
				alertArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
				alertsHistoryContainer.add(alertArea);
			}
		}

		revalidate();
		repaint();
	}

	private JPanel buildWatchlistEntryCard(WatchlistEntry entry)
	{
		JPanel card = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_SM));
		card.setBackground(AlchTheme.BG_CARD);
		card.setBorder(entry.isFavourite() ? AlchTheme.createGoldCardBorder() : AlchTheme.createCardBorder());

		Optional<AlchMateItem> liveItemOpt = marketDataService.getItemById(entry.getItemId());
		AlchMateItem liveItem = liveItemOpt.orElse(null);

		// Header row: Icon + Name (Left) | Favourite + Remove (Right)
		JPanel leftHeader = new JPanel(new GridBagLayout());
		leftHeader.setOpaque(false);

		JLabel iconLbl = new JLabel();
		iconService.attachItemIcon(iconLbl, entry.getItemId(), entry.getItemName());

		JLabel nameLbl = new JLabel((entry.isFavourite() ? "★ " : "") + entry.getItemName());
		nameLbl.setFont(AlchTheme.getSectionHeadingFont());
		nameLbl.setForeground(entry.isFavourite() ? AlchTheme.ACCENT_GOLD : AlchTheme.TEXT_PRIMARY);

		GridBagConstraints gbcIcon = new GridBagConstraints();
		gbcIcon.gridx = 0;
		gbcIcon.gridy = 0;
		gbcIcon.insets = new Insets(0, 0, 0, AlchTheme.SPACE_SM);
		gbcIcon.anchor = GridBagConstraints.WEST;
		leftHeader.add(iconLbl, gbcIcon);

		GridBagConstraints gbcName = new GridBagConstraints();
		gbcName.gridx = 1;
		gbcName.gridy = 0;
		gbcName.weightx = 1.0;
		gbcName.fill = GridBagConstraints.HORIZONTAL;
		gbcName.anchor = GridBagConstraints.WEST;
		leftHeader.add(nameLbl, gbcName);

		JPanel topBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, AlchTheme.SPACE_XS, 0));
		topBtns.setOpaque(false);

		JButton favBtn = AlchTheme.createSecondaryButton(entry.isFavourite() ? "★ Fav" : "☆ Fav");
		if (entry.isFavourite())
		{
			favBtn.setForeground(AlchTheme.ACCENT_GOLD);
		}
		favBtn.setToolTipText("Toggle favourite status");
		favBtn.addActionListener(e -> watchlistService.toggleFavourite(entry.getItemId()));

		JButton removeBtn = AlchTheme.createDangerButton("Remove");
		removeBtn.setToolTipText("Remove from watchlist");
		removeBtn.addActionListener(e -> watchlistService.removeItem(entry.getItemId()));

		topBtns.add(favBtn);
		topBtns.add(removeBtn);

		card.add(AlchTheme.createSplitRow(leftHeader, topBtns));

		// Live Price & Delta Comparison Rows
		JPanel metricsRows = new JPanel(new VerticalStackLayout(2));
		metricsRows.setOpaque(false);

		if (liveItem != null && liveItem.hasValidPrice())
		{
			long currentBuy = liveItem.getEstimatedPurchasePrice();
			long baseBuy = entry.getBaselinePurchasePrice();
			long priceDiff = baseBuy > 0 ? (currentBuy - baseBuy) : 0L;

			long currentProfit = liveItem.getNetProfitPerAlch();
			long baseProfit = entry.getBaselineProfitPerAlch();
			long profitDiff = currentProfit - baseProfit;

			JLabel curBuyLbl = new JLabel(AlchTheme.formatGp(currentBuy));
			curBuyLbl.setForeground(AlchTheme.TEXT_PRIMARY);

			JLabel curProfitLbl = new JLabel(AlchTheme.formatSignedGp(currentProfit)
				+ " (" + AlchTheme.formatRoi(liveItem.getRoiPercent()) + ")");
			curProfitLbl.setForeground(AlchTheme.getProfitColor(currentProfit));

			JLabel priceDiffLbl = new JLabel(AlchTheme.formatSignedGp(priceDiff));
			priceDiffLbl.setForeground(priceDiff <= 0 ? AlchTheme.SUCCESS_GREEN : AlchTheme.DANGER_RED);

			JLabel profitDiffLbl = new JLabel(AlchTheme.formatSignedGp(profitDiff));
			profitDiffLbl.setForeground(AlchTheme.getProfitColor(profitDiff));

			metricsRows.add(AlchTheme.createKeyValueRow("Current Buy Price", curBuyLbl, true));
			metricsRows.add(AlchTheme.createKeyValueRow("Current Net Profit", curProfitLbl, false));
			metricsRows.add(AlchTheme.createKeyValueRow("Buy Price vs Baseline", priceDiffLbl, true));
			metricsRows.add(AlchTheme.createKeyValueRow("Profit Change vs Baseline", profitDiffLbl, false));
		}
		else
		{
			JLabel baseLbl = new JLabel(AlchTheme.formatGp(entry.getBaselinePurchasePrice()));
			baseLbl.setForeground(AlchTheme.TEXT_MUTED);
			JLabel statusLbl = new JLabel("Awaiting market sync...");
			statusLbl.setForeground(AlchTheme.ACCENT_GOLD);

			metricsRows.add(AlchTheme.createKeyValueRow("Baseline Buy Price", baseLbl, true));
			metricsRows.add(AlchTheme.createKeyValueRow("Live Status", statusLbl, false));
		}
		card.add(metricsRows);

		// Target Profit & Min ROI Editors
		JSpinner targetProfitSpinner = new JSpinner(new SpinnerNumberModel(entry.getTargetProfitGp(), -100000, 10000000, 50));
		AlchTheme.styleSpinner(targetProfitSpinner);

		JSpinner minRoiSpinner = new JSpinner(new SpinnerNumberModel(entry.getMinimumRoiPercent(), 0.0, 500.0, 0.5));
		AlchTheme.styleSpinner(minRoiSpinner);

		card.add(AlchTheme.createFormControlRow("Target Alert Profit (GP)", targetProfitSpinner));
		card.add(AlchTheme.createFormControlRow("Minimum Alert ROI (%)", minRoiSpinner));

		// Bottom Action Bar
		JCheckBox alertCheck = new JCheckBox("Alerts Enabled", entry.isAlertEnabled());
		AlchTheme.styleCheckBox(alertCheck);
		alertCheck.addActionListener(e -> watchlistService.toggleAlertEnabled(entry.getItemId()));

		JPanel actionBtns = new JPanel(new ResponsiveGridLayout(90, 2, AlchTheme.SPACE_XS, AlchTheme.SPACE_XS));
		actionBtns.setOpaque(false);

		JButton saveTargetBtn = AlchTheme.createSecondaryButton("Save Target");
		saveTargetBtn.addActionListener(e -> {
			int tp = ((Number) targetProfitSpinner.getValue()).intValue();
			double mr = ((Number) minRoiSpinner.getValue()).doubleValue();
			watchlistService.updateTargets(entry.getItemId(), tp, mr);
		});

		JButton inspectBtn = AlchTheme.createPrimaryButton("Inspect Item");
		inspectBtn.setEnabled(liveItem != null);
		inspectBtn.addActionListener(e -> {
			if (liveItem != null)
			{
				navigationController.openItemAnalysis(liveItem);
			}
		});

		actionBtns.add(saveTargetBtn);
		actionBtns.add(inspectBtn);

		card.add(alertCheck);
		card.add(actionBtns);
		return card;
	}
}
