package com.alchmatepro;

import com.alchmatepro.AlchUiComponents.VerticalStackLayout;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import lombok.Getter;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Root RuneLite panel for AlchMate Pro.
 * Hosts Section A (Brand Header), Section B (Primary & Secondary Navigation),
 * and the responsive scrollable viewport for all 7 application views.
 */
public class AlchMateProPanel extends PluginPanel implements NavigationController
{
	private final MarketDataService marketDataService;
	private final WatchlistService watchlistService;

	private final ActiveCardLayout cardLayout = new ActiveCardLayout();
	private final ScrollableCardContainer cardContainer = new ScrollableCardContainer(cardLayout);
	private final JScrollPane bodyScrollPane;
	private final Map<Section, JButton> navButtons = new EnumMap<>(Section.class);

	@Getter
	private final DashboardPanel dashboardPanel;
	@Getter
	private final AlchScannerPanel alchScannerPanel;
	@Getter
	private final ItemAnalysisPanel itemAnalysisPanel;
	@Getter
	private final ProfitCalculatorPanel profitCalculatorPanel;
	@Getter
	private final WatchlistPanel watchlistPanel;
	@Getter
	private final MarketDataPanel marketDataPanel;
	@Getter
	private final SettingsPanel settingsPanel;

	private Section activeSection = Section.DASHBOARD;

	@Inject
	public AlchMateProPanel(
		MarketDataService marketDataService,
		ProfitCalculator profitCalculator,
		RecommendationEngine recommendationEngine,
		PriceHistoryService priceHistoryService,
		WatchlistService watchlistService,
		AlertService alertService,
		CacheService cacheService,
		IconService iconService,
		AlchMateProConfig config,
		ConfigManager configManager)
	{
		super(false);
		this.marketDataService = marketDataService;
		this.watchlistService = watchlistService;

		setLayout(new BorderLayout(0, 0));
		setBackground(AlchTheme.BG_MAIN);
		setBorder(null);

		this.dashboardPanel = new DashboardPanel(marketDataService, recommendationEngine, iconService, this);
		this.alchScannerPanel = new AlchScannerPanel(marketDataService, watchlistService, iconService, config, this);
		this.itemAnalysisPanel = new ItemAnalysisPanel(
			marketDataService,
			profitCalculator,
			priceHistoryService,
			watchlistService,
			iconService,
			this
		);
		this.profitCalculatorPanel = new ProfitCalculatorPanel(marketDataService, profitCalculator, config);
		this.watchlistPanel = new WatchlistPanel(
			watchlistService,
			marketDataService,
			alertService,
			iconService,
			this
		);
		this.marketDataPanel = new MarketDataPanel(marketDataService, cacheService);
		this.settingsPanel = new SettingsPanel(config, configManager, marketDataService);

		cardContainer.setBackground(AlchTheme.BG_MAIN);
		cardContainer.add(dashboardPanel, Section.DASHBOARD.name());
		cardContainer.add(alchScannerPanel, Section.ALCH_SCANNER.name());
		cardContainer.add(itemAnalysisPanel, Section.ITEM_ANALYSIS.name());
		cardContainer.add(profitCalculatorPanel, Section.PROFIT_CALCULATOR.name());
		cardContainer.add(watchlistPanel, Section.WATCHLIST.name());
		cardContainer.add(marketDataPanel, Section.MARKET_DATA.name());
		cardContainer.add(settingsPanel, Section.SETTINGS.name());

		JPanel topChrome = new JPanel(new VerticalStackLayout(6));
		topChrome.setBackground(AlchTheme.BG_SECONDARY);
		topChrome.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, AlchTheme.BORDER_DEFAULT),
			BorderFactory.createEmptyBorder(AlchTheme.SPACE_SM, AlchTheme.SPACE_SM, AlchTheme.SPACE_SM, AlchTheme.SPACE_SM)
		));
		topChrome.add(buildBrandHeader());
		topChrome.add(buildNavigationGrid());

		this.bodyScrollPane = new JScrollPane(cardContainer);
		this.bodyScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		this.bodyScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		AlchTheme.styleScrollPane(this.bodyScrollPane);

		add(topChrome, BorderLayout.NORTH);
		add(this.bodyScrollPane, BorderLayout.CENTER);

		this.marketDataService.addDataUpdateListener(this::refreshAllPanels);
		this.watchlistService.addChangeListener(watchlistPanel::refreshView);

		navigateTo(Section.DASHBOARD);
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(320, 760);
	}

	@Override
	public Dimension getMinimumSize()
	{
		return new Dimension(235, 400);
	}

	private JPanel buildBrandHeader()
	{
		JPanel titles = new JPanel(new VerticalStackLayout(1));
		titles.setOpaque(false);

		JLabel mainTitle = new JLabel("WIZALCH");
		mainTitle.setFont(AlchTheme.getPageHeadingFont());
		mainTitle.setForeground(AlchTheme.ACCENT_GOLD);

		JLabel subTitle = new JLabel("HIGH ALCHEMY INTELLIGENCE");
		subTitle.setFont(AlchTheme.getSupportFont(true));
		subTitle.setForeground(AlchTheme.TEXT_SECONDARY);

		titles.add(mainTitle);
		titles.add(subTitle);

		JLabel versionBadge = AlchTheme.createBadge("v1.1", AlchTheme.ACCENT_GOLD, AlchTheme.ACCENT_GOLD_DIM);
		return AlchTheme.createSplitRow(titles, versionBadge);
	}

	private JPanel buildNavigationGrid()
	{
		JPanel navWrapper = new JPanel(new VerticalStackLayout(AlchTheme.SPACE_XS));
		navWrapper.setOpaque(false);

		JPanel primaryRow = new JPanel(new GridLayout(1, 4, AlchTheme.SPACE_XS, 0));
		primaryRow.setOpaque(false);

		JPanel secondaryRow = new JPanel(new GridLayout(1, 3, AlchTheme.SPACE_XS, 0));
		secondaryRow.setOpaque(false);

		Section[] sections = Section.values();
		for (int i = 0; i < sections.length; i++)
		{
			final Section sec = sections[i];
			JButton btn = createNavTabButton(sec);
			btn.addActionListener(e -> navigateTo(sec));
			navButtons.put(sec, btn);
			if (i < 4)
			{
				primaryRow.add(btn);
			}
			else
			{
				secondaryRow.add(btn);
			}
		}

		navWrapper.add(primaryRow);
		navWrapper.add(secondaryRow);
		return navWrapper;
	}

	private JButton createNavTabButton(Section section)
	{
		JButton btn = new JButton(section.getLabel());
		btn.setFont(AlchTheme.getBodyFont(true));
		btn.setFocusPainted(false);
		btn.setMargin(new Insets(2, 4, 2, 4));
		btn.setPreferredSize(new Dimension(56, 28));
		btn.setMinimumSize(new Dimension(48, 28));
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (activeSection != section)
				{
					btn.setBackground(AlchTheme.BG_CARD_HOVER);
					btn.setForeground(AlchTheme.TEXT_PRIMARY);
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				if (activeSection != section)
				{
					applyNavTabStyle(btn, false);
				}
			}
		});
		applyNavTabStyle(btn, false);
		return btn;
	}

	private void applyNavTabStyle(JButton btn, boolean active)
	{
		if (active)
		{
			btn.setBackground(AlchTheme.BG_CARD);
			btn.setForeground(AlchTheme.ACCENT_GOLD);
			btn.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 1, 2, 1, AlchTheme.ACCENT_GOLD),
				BorderFactory.createEmptyBorder(3, 4, 2, 4)
			));
		}
		else
		{
			btn.setBackground(AlchTheme.BG_PANEL);
			btn.setForeground(AlchTheme.TEXT_SECONDARY);
			btn.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(AlchTheme.BORDER_DEFAULT, 1),
				BorderFactory.createEmptyBorder(3, 4, 3, 4)
			));
		}
	}

	@Override
	public void navigateTo(Section section)
	{
		if (section == null)
		{
			return;
		}
		this.activeSection = section;
		cardLayout.show(cardContainer, section.name());

		for (Map.Entry<Section, JButton> entry : navButtons.entrySet())
		{
			applyNavTabStyle(entry.getValue(), entry.getKey() == section);
		}

		refreshSection(section);
		cardContainer.revalidate();
		cardContainer.repaint();

		if (bodyScrollPane != null)
		{
			SwingUtilities.invokeLater(() -> bodyScrollPane.getVerticalScrollBar().setValue(0));
		}
	}

	@Override
	public void openItemAnalysis(AlchMateItem item)
	{
		itemAnalysisPanel.displayItem(item);
		navigateTo(Section.ITEM_ANALYSIS);
	}

	@Override
	public void openProfitCalculator(AlchMateItem item)
	{
		profitCalculatorPanel.loadItem(item);
		navigateTo(Section.PROFIT_CALCULATOR);
	}

	public void refreshAllPanels()
	{
		dashboardPanel.refreshView();
		alchScannerPanel.refreshView();
		itemAnalysisPanel.refreshCurrentItem();
		profitCalculatorPanel.syncDefaultsFromConfig();
		profitCalculatorPanel.recalculate();
		watchlistPanel.refreshView();
		marketDataPanel.refreshView();
		settingsPanel.loadFromConfig();
		cardContainer.revalidate();
		cardContainer.repaint();
	}

	private void refreshSection(Section section)
	{
		switch (section)
		{
			case DASHBOARD:
				dashboardPanel.refreshView();
				break;
			case ALCH_SCANNER:
				alchScannerPanel.refreshView();
				break;
			case ITEM_ANALYSIS:
				itemAnalysisPanel.refreshCurrentItem();
				break;
			case PROFIT_CALCULATOR:
				profitCalculatorPanel.recalculate();
				break;
			case WATCHLIST:
				watchlistPanel.refreshView();
				break;
			case MARKET_DATA:
				marketDataPanel.refreshView();
				break;
			case SETTINGS:
				settingsPanel.loadFromConfig();
				break;
		}
	}

	public Section getActiveSection()
	{
		return activeSection;
	}

	public void shutDown()
	{
		if (itemAnalysisPanel != null)
		{
			itemAnalysisPanel.shutDown();
		}
	}

	private static class ActiveCardLayout extends CardLayout
	{
		@Override
		public Dimension preferredLayoutSize(Container parent)
		{
			synchronized (parent.getTreeLock())
			{
				Insets insets = parent.getInsets();
				for (int i = 0; i < parent.getComponentCount(); i++)
				{
					Component c = parent.getComponent(i);
					if (c.isVisible())
					{
						if (parent.getWidth() > 0 && c.getWidth() != parent.getWidth() - insets.left - insets.right)
						{
							c.setSize(parent.getWidth() - insets.left - insets.right, c.getHeight());
						}
						Dimension d = c.getPreferredSize();
						return new Dimension(
							d.width + insets.left + insets.right,
							d.height + insets.top + insets.bottom
						);
					}
				}
				return super.preferredLayoutSize(parent);
			}
		}

		@Override
		public Dimension minimumLayoutSize(Container parent)
		{
			synchronized (parent.getTreeLock())
			{
				Insets insets = parent.getInsets();
				for (int i = 0; i < parent.getComponentCount(); i++)
				{
					Component c = parent.getComponent(i);
					if (c.isVisible())
					{
						Dimension d = c.getMinimumSize();
						return new Dimension(
							d.width + insets.left + insets.right,
							d.height + insets.top + insets.bottom
						);
					}
				}
				return super.minimumLayoutSize(parent);
			}
		}
	}

	private static class ScrollableCardContainer extends JPanel implements Scrollable
	{
		ScrollableCardContainer(CardLayout layout)
		{
			super(layout);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 20;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return Math.max(60, visibleRect.height - 40);
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}
}
