package com.alchmatepro;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;
import net.runelite.client.util.QuantityFormatter;

/**
 * Reusable Layout Managers, Responsive Grids, Financial Tables, Metric Cards,
 * and Compact RuneLite-Sized Expandable Item Cards for AlchMate Pro.
 */
public final class AlchUiComponents
{
	private AlchUiComponents()
	{
	}

	// =========================================================================
	// 1. VERTICAL STACK LAYOUT (100% WIDTH STRETCH, ZERO OVERLAP)
	// =========================================================================

	/**
	 * Lays out child components vertically from top to bottom, stretching every visible
	 * child to 100% of the container's inner width and using each child's preferred height.
	 */
	public static class VerticalStackLayout implements LayoutManager
	{
		private final int vgap;

		public VerticalStackLayout(int vgap)
		{
			this.vgap = Math.max(0, vgap);
		}

		@Override
		public void addLayoutComponent(String name, Component comp)
		{
		}

		@Override
		public void removeLayoutComponent(Component comp)
		{
		}

		@Override
		public Dimension preferredLayoutSize(Container parent)
		{
			synchronized (parent.getTreeLock())
			{
				Insets insets = parent.getInsets();
				int totalHeight = insets.top + insets.bottom;
				int maxWidth = 0;
				int visibleCount = 0;

				for (int i = 0; i < parent.getComponentCount(); i++)
				{
					Component c = parent.getComponent(i);
					if (!c.isVisible())
					{
						continue;
					}
					Dimension d = c.getPreferredSize();
					if (d != null)
					{
						totalHeight += d.height;
						maxWidth = Math.max(maxWidth, d.width);
					}
					visibleCount++;
				}
				if (visibleCount > 1)
				{
					totalHeight += (visibleCount - 1) * vgap;
				}
				return new Dimension(maxWidth + insets.left + insets.right, totalHeight);
			}
		}

		@Override
		public Dimension minimumLayoutSize(Container parent)
		{
			synchronized (parent.getTreeLock())
			{
				Insets insets = parent.getInsets();
				int totalHeight = insets.top + insets.bottom;
				int visibleCount = 0;

				for (int i = 0; i < parent.getComponentCount(); i++)
				{
					Component c = parent.getComponent(i);
					if (!c.isVisible())
					{
						continue;
					}
					Dimension d = c.getPreferredSize();
					if (d != null)
					{
						totalHeight += d.height;
					}
					visibleCount++;
				}
				if (visibleCount > 1)
				{
					totalHeight += (visibleCount - 1) * vgap;
				}
				return new Dimension(10, totalHeight);
			}
		}

		@Override
		public void layoutContainer(Container parent)
		{
			synchronized (parent.getTreeLock())
			{
				Insets insets = parent.getInsets();
				int x = insets.left;
				int y = insets.top;
				int availWidth = Math.max(40, parent.getWidth() - insets.left - insets.right);

				for (int i = 0; i < parent.getComponentCount(); i++)
				{
					Component c = parent.getComponent(i);
					if (!c.isVisible())
					{
						continue;
					}
					if (c.getWidth() != availWidth)
					{
						c.setSize(availWidth, c.getHeight());
					}
					Dimension pref = c.getPreferredSize();
					int h = pref != null ? pref.height : 24;
					c.setBounds(x, y, availWidth, h);
					y += h + vgap;
				}
			}
		}
	}

	/**
	 * A vertical stack panel that tracks the enclosing JScrollPane viewport's width.
	 */
	public static class ScrollableTrackWidthPanel extends JPanel implements Scrollable
	{
		public ScrollableTrackWidthPanel(int vgap, int padding)
		{
			super(new VerticalStackLayout(vgap));
			setBackground(AlchTheme.BG_MAIN);
			if (padding > 0)
			{
				setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));
			}
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

	// =========================================================================
	// 2. RESPONSIVE GRID LAYOUT (ADAPTS COLUMNS TO AVAILABLE WIDTH)
	// =========================================================================

	/**
	 * Responsive multi-column layout that automatically stacks into fewer columns on narrow widths
	 * and expands up to {@code maxColumns} when sufficient horizontal width is available.
	 */
	public static class ResponsiveGridLayout implements LayoutManager
	{
		private final int minColumnWidth;
		private final int maxColumns;
		private final int hgap;
		private final int vgap;

		public ResponsiveGridLayout(int minColumnWidth, int maxColumns, int hgap, int vgap)
		{
			this.minColumnWidth = Math.max(48, minColumnWidth);
			this.maxColumns = Math.max(1, maxColumns);
			this.hgap = Math.max(0, hgap);
			this.vgap = Math.max(0, vgap);
		}

		private int computeColumnCount(Container parent, int visibleCount)
		{
			if (visibleCount <= 1)
			{
				return 1;
			}
			Insets insets = parent.getInsets();
			int parentW = parent.getWidth();
			if (parentW <= 0 && parent.getParent() != null)
			{
				parentW = parent.getParent().getWidth();
			}
			if (parentW <= 0)
			{
				parentW = 310;
			}
			int availW = Math.max(1, parentW - insets.left - insets.right);
			int possibleCols = (availW + hgap) / (minColumnWidth + hgap);
			int cols = Math.max(1, Math.min(maxColumns, possibleCols));
			return Math.min(cols, visibleCount);
		}

		@Override
		public void addLayoutComponent(String name, Component comp)
		{
		}

		@Override
		public void removeLayoutComponent(Component comp)
		{
		}

		@Override
		public Dimension preferredLayoutSize(Container parent)
		{
			synchronized (parent.getTreeLock())
			{
				Insets insets = parent.getInsets();
				int visibleCount = 0;
				for (int i = 0; i < parent.getComponentCount(); i++)
				{
					if (parent.getComponent(i).isVisible())
					{
						visibleCount++;
					}
				}
				if (visibleCount == 0)
				{
					return new Dimension(insets.left + insets.right, insets.top + insets.bottom);
				}

				int cols = computeColumnCount(parent, visibleCount);
				int rows = (int) Math.ceil((double) visibleCount / cols);
				int totalHeight = insets.top + insets.bottom + (rows - 1) * vgap;

				int visIdx = 0;
				for (int r = 0; r < rows; r++)
				{
					int rowMaxH = 0;
					for (int c = 0; c < cols && visIdx < parent.getComponentCount(); )
					{
						Component comp = parent.getComponent(visIdx++);
						if (!comp.isVisible())
						{
							continue;
						}
						Dimension d = comp.getPreferredSize();
						if (d != null)
						{
							rowMaxH = Math.max(rowMaxH, d.height);
						}
						c++;
					}
					totalHeight += rowMaxH;
				}
				int prefW = insets.left + insets.right + cols * minColumnWidth + (cols - 1) * hgap;
				return new Dimension(prefW, totalHeight);
			}
		}

		@Override
		public Dimension minimumLayoutSize(Container parent)
		{
			Dimension pref = preferredLayoutSize(parent);
			return new Dimension(10, pref.height);
		}

		@Override
		public void layoutContainer(Container parent)
		{
			synchronized (parent.getTreeLock())
			{
				Insets insets = parent.getInsets();
				int visibleCount = 0;
				for (int i = 0; i < parent.getComponentCount(); i++)
				{
					if (parent.getComponent(i).isVisible())
					{
						visibleCount++;
					}
				}
				if (visibleCount == 0)
				{
					return;
				}

				int cols = computeColumnCount(parent, visibleCount);
				int availW = Math.max(40, parent.getWidth() - insets.left - insets.right);
				int colW = Math.max(30, (availW - (cols - 1) * hgap) / cols);
				int rows = (int) Math.ceil((double) visibleCount / cols);

				int y = insets.top;
				int compIdx = 0;

				for (int r = 0; r < rows; r++)
				{
					int rowMaxH = 0;
					Component[] rowComps = new Component[cols];
					int actualInRow = 0;

					while (actualInRow < cols && compIdx < parent.getComponentCount())
					{
						Component comp = parent.getComponent(compIdx++);
						if (!comp.isVisible())
						{
							continue;
						}
						rowComps[actualInRow++] = comp;
						Dimension d = comp.getPreferredSize();
						if (d != null)
						{
							rowMaxH = Math.max(rowMaxH, d.height);
						}
					}

					int x = insets.left;
					for (int c = 0; c < actualInRow; c++)
					{
						int w = (c == cols - 1)
							? Math.max(colW, (parent.getWidth() - insets.right) - x)
							: colW;
						rowComps[c].setBounds(x, y, w, rowMaxH);
						x += colW + hgap;
					}
					y += rowMaxH + vgap;
				}
			}
		}
	}

	// =========================================================================
	// 3. REUSABLE METRIC / PERFORMANCE CARD
	// =========================================================================

	/**
	 * Compact performance metric card with a muted uppercase title,
	 * a prominent primary value, and a supporting subtitle.
	 */
	public static class MetricCard extends JPanel
	{
		private final JLabel headingLabel = new JLabel();
		private final JLabel mainValueLabel = new JLabel();
		private final JLabel subtitleLabel = new JLabel();

		public MetricCard(String heading, String defaultMainValue, String defaultSubtitle, Color mainValueColor)
		{
			super(new VerticalStackLayout(2));
			setBackground(AlchTheme.BG_CARD);
			setBorder(AlchTheme.createCardBorder());

			headingLabel.setText(heading);
			headingLabel.setFont(AlchTheme.getCardHeadingFont());
			headingLabel.setForeground(AlchTheme.TEXT_MUTED);

			mainValueLabel.setText(defaultMainValue);
			mainValueLabel.setFont(AlchTheme.getMainValueFont());
			mainValueLabel.setForeground(mainValueColor);

			subtitleLabel.setText(defaultSubtitle);
			subtitleLabel.setFont(AlchTheme.getSupportFont(false));
			subtitleLabel.setForeground(AlchTheme.TEXT_SECONDARY);

			add(headingLabel);
			add(mainValueLabel);
			add(subtitleLabel);
		}

		public void updateCard(String mainValue, String subtitle, Color valueColor)
		{
			mainValueLabel.setText(mainValue);
			if (valueColor != null)
			{
				mainValueLabel.setForeground(valueColor);
			}
			subtitleLabel.setText(subtitle);
			subtitleLabel.setToolTipText(subtitle);
		}

		public JLabel getMainValueLabel()
		{
			return mainValueLabel;
		}

		public JLabel getSubtitleLabel()
		{
			return subtitleLabel;
		}
	}

	// =========================================================================
	// 4. COMPACT RUNELITE ALCH ITEM CARD (FULL ITEM TITLE + EXPANDABLE DRAWER)
	// =========================================================================

	/**
	 * Creates a compact, RuneLite-proportioned Item Card where the item title has a dedicated
	 * full-width header row so it is never cut off by the profit figure, followed by a
	 * profit/ROI highlight bar, a compact 2x2 stat grid, a single-row action bar, and an
	 * expandable details drawer.
	 */
	public static JPanel createAlchItemCard(
		int rank,
		AlchMateItem item,
		IconService iconService,
		WatchlistService watchlistService,
		NavigationController navigationController,
		Runnable onWatchlistChanged)
	{
		JPanel card = new JPanel(new VerticalStackLayout(6));
		card.setBackground(AlchTheme.BG_CARD);
		card.setBorder(AlchTheme.createCardBorder());

		AlchCalculation calc = item.getCalculation();
		AlchRecommendation rec = item.getRecommendation();
		long profit = item.getNetProfitPerAlch();
		Color profitColor = AlchTheme.getProfitColor(profit);

		// --- ROW 1: Dedicated Full-Width Title Row (Rank + Icon on WEST, Full Item Name in CENTER) ---
		JPanel titleRow = new JPanel(new BorderLayout(6, 0));
		titleRow.setOpaque(false);

		JPanel rankAndIcon = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		rankAndIcon.setOpaque(false);

		JLabel rankBadge = AlchTheme.createBadge(
			"#" + rank,
			rank <= 3 ? AlchTheme.ACCENT_GOLD : AlchTheme.TEXT_SECONDARY,
			rank <= 3 ? AlchTheme.ACCENT_GOLD_DIM : AlchTheme.BG_SECONDARY
		);
		rankAndIcon.add(rankBadge);

		JLabel iconLabel = new JLabel();
		if (iconService != null)
		{
			iconService.attachItemIcon(iconLabel, item.getId(), item.getName());
		}
		rankAndIcon.add(iconLabel);

		JPanel nameStack = new JPanel(new VerticalStackLayout(1));
		nameStack.setOpaque(false);

		JLabel nameLabel = new JLabel(item.getName());
		nameLabel.setFont(AlchTheme.getUiFont(java.awt.Font.BOLD, 13));
		nameLabel.setForeground(AlchTheme.TEXT_PRIMARY);
		nameLabel.setToolTipText(item.getName() + " (ID: " + item.getId() + ")");

		JLabel metaSubLabel = new JLabel(String.format(
			"%s • %s • %s",
			item.isMembers() ? "P2P" : "F2P",
			item.getPriceSourceType() != null ? item.getPriceSourceType().getLabel() : "GE Est.",
			item.getFreshnessLabel()
		));
		metaSubLabel.setFont(AlchTheme.getSupportFont(false));
		metaSubLabel.setForeground(item.isStale() ? AlchTheme.ACCENT_GOLD : AlchTheme.TEXT_MUTED);

		nameStack.add(nameLabel);
		nameStack.add(metaSubLabel);

		titleRow.add(rankAndIcon, BorderLayout.WEST);
		titleRow.add(nameStack, BorderLayout.CENTER);

		// --- ROW 2: Profit & ROI Highlight Strip ---
		JPanel profitStrip = new JPanel(new BorderLayout(6, 0));
		profitStrip.setBackground(AlchTheme.BG_PANEL);
		profitStrip.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(AlchTheme.BORDER_SUBTLE_LINE, 1),
			BorderFactory.createEmptyBorder(4, 8, 4, 8)
		));

		JLabel profitValLabel = new JLabel(AlchTheme.formatSignedGp(profit) + " / alch");
		profitValLabel.setFont(AlchTheme.getItemProfitFont());
		profitValLabel.setForeground(profitColor);

		JLabel roiAndHourlyLabel = new JLabel(String.format(
			"ROI %s • %s",
			AlchTheme.formatRoi(item.getRoiPercent()),
			AlchTheme.formatHourlyProfit(profit)
		));
		roiAndHourlyLabel.setFont(AlchTheme.getSupportFont(true));
		roiAndHourlyLabel.setForeground(profit >= 0 ? AlchTheme.ACCENT_GOLD : AlchTheme.DANGER_RED);
		roiAndHourlyLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		profitStrip.add(profitValLabel, BorderLayout.CENTER);
		profitStrip.add(roiAndHourlyLabel, BorderLayout.EAST);

		// --- ROW 3: Compact 2x2 Key Metrics Grid ---
		JPanel metricsGrid = new JPanel(new GridLayout(2, 2, 4, 4));
		metricsGrid.setOpaque(false);

		metricsGrid.add(createMiniStatBox("Buy", AlchTheme.formatGp(item.getEstimatedPurchasePrice()), AlchTheme.TEXT_PRIMARY));
		metricsGrid.add(createMiniStatBox("Alch", AlchTheme.formatGp(item.getHighAlchValue()), AlchTheme.ACCENT_GOLD));
		metricsGrid.add(createMiniStatBox("Limit", item.getBuyLimit() > 0 ? QuantityFormatter.formatNumber(item.getBuyLimit()) + "/4h" : "Unknown", AlchTheme.TEXT_SECONDARY));
		metricsGrid.add(createMiniStatBox("1h Vol", QuantityFormatter.formatNumber(item.getVolume1h()) + "/hr", AlchTheme.INFO_BLUE));

		// --- ROW 4: Expandable Details Drawer (Hidden by Default) ---
		JPanel detailsDrawer = new JPanel(new VerticalStackLayout(3));
		detailsDrawer.setBackground(AlchTheme.BG_PANEL);
		detailsDrawer.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(AlchTheme.BORDER_SUBTLE_LINE, 1),
			BorderFactory.createEmptyBorder(6, 6, 6, 6)
		));
		detailsDrawer.setVisible(false);

		JLabel natVal = new JLabel(AlchTheme.formatGp(calc.getNatureRuneCostPerCast()));
		natVal.setForeground(AlchTheme.ACCENT_GOLD);
		detailsDrawer.add(AlchTheme.createKeyValueRow("Nature Rune Cost:", natVal, true));

		JLabel breakEvenVal = new JLabel(AlchTheme.formatGp(calc.getBreakEvenPurchasePrice()));
		breakEvenVal.setForeground(AlchTheme.ACCENT_GOLD);
		detailsDrawer.add(AlchTheme.createKeyValueRow("Break-Even Buy Price:", breakEvenVal, false));

		JLabel p100Val = new JLabel(AlchTheme.formatSignedGp(calc.getProfitPer100Alchs()));
		p100Val.setForeground(profitColor);
		detailsDrawer.add(AlchTheme.createKeyValueRow("Profit (100 Casts):", p100Val, true));

		JLabel p1000Val = new JLabel(AlchTheme.formatSignedGp(calc.getProfitPer1000Alchs()));
		p1000Val.setForeground(profitColor);
		detailsDrawer.add(AlchTheme.createKeyValueRow("Profit (1,000 Casts):", p1000Val, false));

		if (item.getBuyLimit() > 0)
		{
			JLabel cycleVal = new JLabel(AlchTheme.formatSignedGp(calc.getMaxBuyLimitProfit4h()));
			cycleVal.setForeground(profitColor);
			detailsDrawer.add(AlchTheme.createKeyValueRow("Max 4h Cycle Profit:", cycleVal, true));
		}

		if (rec != null)
		{
			JPanel badgesFlow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
			badgesFlow.setOpaque(false);
			badgesFlow.add(AlchTheme.createBadge(
				String.format("Score %.0f/100", rec.getOverallScore()),
				AlchTheme.ACCENT_GOLD,
				AlchTheme.ACCENT_GOLD_DIM
			));
			for (MarketWarning warning : item.getWarnings())
			{
				JLabel wb = AlchTheme.createBadge(warning.getBadgeText(), AlchTheme.DANGER_RED, AlchTheme.DANGER_BG);
				wb.setToolTipText(warning.getExplanation());
				badgesFlow.add(wb);
			}
			detailsDrawer.add(badgesFlow);

			if (rec.getExplanation() != null && !rec.getExplanation().isEmpty())
			{
				JTextArea expArea = createReadOnlyTextArea(rec.getExplanation(), AlchTheme.TEXT_SECONDARY);
				detailsDrawer.add(expArea);
			}
		}

		// --- ROW 5: Single-Row Compact Action Bar (Never Orphans a Button) ---
		int btnCols = watchlistService != null ? 4 : 3;
		JPanel actionBar = new JPanel(new GridLayout(1, btnCols, 4, 0));
		actionBar.setOpaque(false);

		JButton analyseBtn = AlchTheme.createPrimaryButton("Analyse");
		analyseBtn.setToolTipText("Inspect full pricing, history chart, and 4h limit for " + item.getName());
		analyseBtn.addActionListener(e -> navigationController.openItemAnalysis(item));
		actionBar.add(analyseBtn);

		JButton calcBtn = AlchTheme.createSecondaryButton("Calc");
		calcBtn.setToolTipText("Load " + item.getName() + " into the Profit Calculator");
		calcBtn.addActionListener(e -> navigationController.openProfitCalculator(item));
		actionBar.add(calcBtn);

		if (watchlistService != null)
		{
			boolean watched = watchlistService.isWatched(item.getId());
			JButton watchBtn = AlchTheme.createSecondaryButton(watched ? "★ Watch" : "☆ Watch");
			if (watched)
			{
				watchBtn.setForeground(AlchTheme.ACCENT_GOLD);
			}
			watchBtn.addActionListener(e -> {
				watchlistService.toggleItem(item);
				if (onWatchlistChanged != null)
				{
					onWatchlistChanged.run();
				}
			});
			actionBar.add(watchBtn);
		}

		JButton detailsBtn = AlchTheme.createSecondaryButton("Details ▾");
		detailsBtn.setToolTipText("Expand or collapse detailed batch profits and warnings");
		detailsBtn.addActionListener(e -> {
			boolean show = !detailsDrawer.isVisible();
			detailsDrawer.setVisible(show);
			detailsBtn.setText(show ? "Details ▴" : "Details ▾");
			card.revalidate();
			card.repaint();
		});
		actionBar.add(detailsBtn);

		card.add(titleRow);
		card.add(profitStrip);
		card.add(metricsGrid);
		card.add(detailsDrawer);
		card.add(actionBar);

		MouseAdapter openAnalysisClick = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				navigationController.openItemAnalysis(item);
			}
		};
		titleRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		titleRow.addMouseListener(openAnalysisClick);
		profitStrip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		profitStrip.addMouseListener(openAnalysisClick);

		return card;
	}

	private static JPanel createMiniStatBox(String labelText, String valueText, Color valueColor)
	{
		JPanel box = new JPanel(new BorderLayout(4, 0));
		box.setBackground(AlchTheme.BG_PANEL);
		box.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(AlchTheme.BORDER_SUBTLE_LINE, 1),
			BorderFactory.createEmptyBorder(3, 6, 3, 6)
		));

		JLabel k = new JLabel(labelText);
		k.setFont(AlchTheme.getSupportFont(false));
		k.setForeground(AlchTheme.TEXT_MUTED);

		JLabel v = new JLabel(valueText);
		v.setFont(AlchTheme.getBodyFont(true));
		v.setForeground(valueColor);
		v.setHorizontalAlignment(SwingConstants.RIGHT);

		box.add(k, BorderLayout.WEST);
		box.add(v, BorderLayout.CENTER);
		return box;
	}

	// =========================================================================
	// 5. REUSABLE FINANCIAL TABLE STYLING
	// =========================================================================

	public static void styleFinancialTable(JTable table, int[] rightAlignedColumns, int profitColumnIndex)
	{
		table.setBackground(AlchTheme.BG_PANEL);
		table.setForeground(AlchTheme.TEXT_PRIMARY);
		table.setFont(AlchTheme.getBodyFont(false));
		table.setRowHeight(28);
		table.setShowHorizontalLines(true);
		table.setShowVerticalLines(false);
		table.setGridColor(AlchTheme.BORDER_SUBTLE_LINE);
		table.setIntercellSpacing(new Dimension(0, 1));
		table.setSelectionBackground(AlchTheme.BG_CARD_HOVER);
		table.setSelectionForeground(AlchTheme.TEXT_PRIMARY);
		table.setFillsViewportHeight(true);

		JTableHeader header = table.getTableHeader();
		header.setBackground(AlchTheme.BG_SECONDARY);
		header.setForeground(AlchTheme.TEXT_SECONDARY);
		header.setFont(AlchTheme.getCardHeadingFont());
		header.setPreferredSize(new Dimension(header.getPreferredSize().width, 28));
		header.setReorderingAllowed(false);
		header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AlchTheme.BORDER_DEFAULT));

		boolean[] isRightCol = new boolean[table.getColumnCount()];
		if (rightAlignedColumns != null)
		{
			for (int col : rightAlignedColumns)
			{
				if (col >= 0 && col < isRightCol.length)
				{
					isRightCol[col] = true;
				}
			}
		}

		DefaultTableCellRenderer cellRenderer = new DefaultTableCellRenderer()
		{
			@Override
			public Component getTableCellRendererComponent(
				JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column)
			{
				Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
				setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));

				if (!isSelected)
				{
					c.setBackground(row % 2 == 0 ? AlchTheme.BG_PANEL : AlchTheme.BG_CARD);
				}

				boolean rightAlign = column < isRightCol.length && isRightCol[column];
				setHorizontalAlignment(rightAlign ? SwingConstants.RIGHT : SwingConstants.LEFT);

				String text = value != null ? value.toString() : "";
				if (column == profitColumnIndex || text.startsWith("+") || (text.startsWith("-") && text.endsWith("GP")))
				{
					setFont(AlchTheme.getBodyFont(true));
					if (text.startsWith("+"))
					{
						c.setForeground(AlchTheme.SUCCESS_GREEN);
					}
					else if (text.startsWith("-"))
					{
						c.setForeground(AlchTheme.DANGER_RED);
					}
					else
					{
						c.setForeground(AlchTheme.TEXT_PRIMARY);
					}
				}
				else
				{
					setFont(AlchTheme.getBodyFont(column == 0 || column == 1));
					c.setForeground(column == 1 ? AlchTheme.TEXT_PRIMARY : AlchTheme.TEXT_SECONDARY);
				}
				return c;
			}
		};

		TableColumnModel colModel = table.getColumnCount() > 0 ? table.getColumnModel() : null;
		if (colModel != null)
		{
			for (int i = 0; i < colModel.getColumnCount(); i++)
			{
				colModel.getColumn(i).setCellRenderer(cellRenderer);
			}
		}
	}

	/**
	 * Creates a wrapping read-only text area that never overflows horizontally.
	 */
	public static JTextArea createReadOnlyTextArea(String text, Color textColor)
	{
		JTextArea area = new JTextArea(text);
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setOpaque(false);
		area.setFont(AlchTheme.getBodyFont(false));
		area.setForeground(textColor);
		area.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		return area;
	}
}
