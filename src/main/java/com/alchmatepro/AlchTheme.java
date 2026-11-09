package com.alchmatepro;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicScrollBarUI;
import net.runelite.client.util.QuantityFormatter;

/**
 * Unified Design System, Colour Tokens, Compact RuneLite Spacing Scale, Typography Hierarchy,
 * and Reusable Component Factory for AlchMate Pro.
 */
public final class AlchTheme
{
	// ==================================================
	// 1. COLOUR PALETTE TOKENS
	// ==================================================
	public static final Color BG_MAIN          = new Color(0x10, 0x11, 0x14); // #101114 Main background
	public static final Color BG_SECONDARY     = new Color(0x17, 0x19, 0x1E); // #17191E Secondary background
	public static final Color BG_PANEL         = new Color(0x1D, 0x20, 0x27); // #1D2027 Panel background
	public static final Color BG_CARD          = new Color(0x22, 0x26, 0x30); // #222630 Card background
	public static final Color BG_CARD_HOVER    = new Color(0x29, 0x2E, 0x3A); // Subtle hover state
	public static final Color BG_ROW_ALT       = new Color(0x1E, 0x22, 0x2B); // Alternating table row

	public static final Color BORDER_DEFAULT   = new Color(0x34, 0x39, 0x44); // #343944 Borders
	public static final Color BORDER_SUBTLE_LINE = new Color(0x2A, 0x2E, 0x38);
	public static final Color BORDER_GOLD_ACCENT = new Color(0x8C, 0x70, 0x28);

	public static final Color ACCENT_GOLD      = new Color(0xE7, 0xB8, 0x3F); // #E7B83F Primary gold
	public static final Color ACCENT_GOLD_HOVER = new Color(0xF2, 0xC6, 0x52);
	public static final Color ACCENT_GOLD_DIM  = new Color(0x33, 0x2B, 0x17);

	public static final Color SUCCESS_GREEN    = new Color(0x35, 0xD9, 0x87); // #35D987 Success green
	public static final Color SUCCESS_BG       = new Color(0x16, 0x33, 0x26);

	public static final Color DANGER_RED       = new Color(0xF0, 0x5D, 0x66); // #F05D66 Danger red
	public static final Color DANGER_BG        = new Color(0x38, 0x1A, 0x1F);

	public static final Color INFO_BLUE        = new Color(0x55, 0xA6, 0xE8); // #55A6E8 Information blue
	public static final Color INFO_BG          = new Color(0x18, 0x2A, 0x3D);

	public static final Color TEXT_PRIMARY     = new Color(0xF1, 0xF2, 0xF4); // #F1F2F4 Primary text
	public static final Color TEXT_SECONDARY   = new Color(0xA0, 0xA6, 0xB2); // #A0A6B2 Secondary text
	public static final Color TEXT_MUTED       = new Color(0x73, 0x7B, 0x89); // #737B89 Muted text

	// Legacy aliases
	public static final Color BG_MATTE_BLACK     = BG_MAIN;
	public static final Color SURFACE_CHARCOAL   = BG_CARD;
	public static final Color SURFACE_ELEVATED   = BG_PANEL;
	public static final Color SURFACE_HOVER      = BG_CARD_HOVER;
	public static final Color SURFACE_INPUT      = BG_SECONDARY;
	public static final Color BORDER_SUBTLE      = BORDER_DEFAULT;
	public static final Color BORDER_GOLD_MUTED  = BORDER_GOLD_ACCENT;
	public static final Color ACCENT_GOLD_BRIGHT = ACCENT_GOLD;
	public static final Color ACCENT_GOLD_DARK   = ACCENT_GOLD_DIM;
	public static final Color PROFIT_GREEN       = SUCCESS_GREEN;
	public static final Color PROFIT_BG          = SUCCESS_BG;
	public static final Color LOSS_RED           = DANGER_RED;
	public static final Color LOSS_BG            = DANGER_BG;
	public static final Color WARNING_AMBER      = ACCENT_GOLD;
	public static final Color WARNING_BG         = ACCENT_GOLD_DIM;

	// ==================================================
	// 2. SPACING SCALE TOKENS
	// ==================================================
	public static final int SPACE_XS  = 4;  // 4px  = very small spacing
	public static final int SPACE_SM  = 8;  // 8px  = compact spacing
	public static final int SPACE_MD  = 12; // 12px = standard spacing
	public static final int SPACE_LG  = 16; // 16px = component spacing
	public static final int SPACE_XL  = 20; // 20px = section spacing
	public static final int SPACE_XXL = 24; // 24px = large section spacing

	// Compact RuneLite control heights
	public static final int CONTROL_HEIGHT = 26;
	public static final int BUTTON_HEIGHT  = 26;

	/** Standard high-alchemy casting rate in OSRS (5 ticks = 3.0s per cast = 1,200 casts/hour). */
	public static final long ALCH_CASTS_PER_HOUR = 1_200L;

	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
		.withZone(ZoneId.systemDefault());

	private AlchTheme()
	{
	}

	// ==================================================
	// 3. COMPACT RUNELITE TYPOGRAPHY HIERARCHY
	// ==================================================
	public static Font getPageHeadingFont()
	{
		return new Font("SansSerif", Font.BOLD, 15);
	}

	public static Font getSectionHeadingFont()
	{
		return new Font("SansSerif", Font.BOLD, 12);
	}

	public static Font getCardHeadingFont()
	{
		return new Font("SansSerif", Font.BOLD, 10);
	}

	public static Font getMainValueFont()
	{
		return new Font("SansSerif", Font.BOLD, 15);
	}

	public static Font getItemProfitFont()
	{
		return new Font("SansSerif", Font.BOLD, 14);
	}

	public static Font getBodyFont(boolean bold)
	{
		return new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, 11);
	}

	public static Font getSupportFont(boolean bold)
	{
		return new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, 10);
	}

	public static Font getBoldFont(float size)
	{
		return new Font("SansSerif", Font.BOLD, Math.max(10, Math.round(size)));
	}

	public static Font getSmallFont()
	{
		return getSupportFont(false);
	}

	public static Font getUiFont(int style, int size)
	{
		return new Font("SansSerif", style, Math.max(10, size));
	}

	// ==================================================
	// 4. REUSABLE BORDERS & CARDS (COMPACT RUNELITE SIZING)
	// ==================================================
	public static Border createCardBorder()
	{
		return BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORDER_DEFAULT, 1),
			BorderFactory.createEmptyBorder(SPACE_SM, SPACE_SM, SPACE_SM, SPACE_SM)
		);
	}

	public static Border createSpaciousCardBorder()
	{
		return BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORDER_DEFAULT, 1),
			BorderFactory.createEmptyBorder(SPACE_MD, SPACE_MD, SPACE_MD, SPACE_MD)
		);
	}

	public static Border createGoldCardBorder()
	{
		return BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORDER_GOLD_ACCENT, 1),
			BorderFactory.createEmptyBorder(SPACE_SM, SPACE_SM, SPACE_SM, SPACE_SM)
		);
	}

	public static JPanel createCardPanel()
	{
		JPanel panel = new JPanel(new BorderLayout(SPACE_XS, SPACE_XS));
		panel.setBackground(BG_CARD);
		panel.setBorder(createCardBorder());
		return panel;
	}

	public static JPanel createSectionPanel()
	{
		JPanel panel = new JPanel(new BorderLayout(SPACE_XS, SPACE_XS));
		panel.setBackground(BG_PANEL);
		panel.setBorder(createCardBorder());
		return panel;
	}

	// ==================================================
	// 5. REUSABLE CONTROLS (BUTTONS, INPUTS, BADGES, ROWS)
	// ==================================================
	public static JButton createPrimaryButton(String text)
	{
		JButton btn = new JButton(text);
		btn.setFont(getBodyFont(true));
		btn.setForeground(new Color(0x10, 0x11, 0x14));
		btn.setBackground(ACCENT_GOLD);
		btn.setFocusPainted(false);
		btn.setMargin(new Insets(2, 6, 2, 6));
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ACCENT_GOLD_HOVER, 1),
			BorderFactory.createEmptyBorder(4, 8, 4, 8)
		));
		btn.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (btn.isEnabled())
				{
					btn.setBackground(ACCENT_GOLD_HOVER);
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				btn.setBackground(ACCENT_GOLD);
			}
		});
		return btn;
	}

	public static JButton createSecondaryButton(String text)
	{
		JButton btn = new JButton(text);
		btn.setFont(getBodyFont(false));
		btn.setForeground(TEXT_PRIMARY);
		btn.setBackground(BG_PANEL);
		btn.setFocusPainted(false);
		btn.setMargin(new Insets(2, 6, 2, 6));
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORDER_DEFAULT, 1),
			BorderFactory.createEmptyBorder(4, 8, 4, 8)
		));
		btn.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (btn.isEnabled())
				{
					btn.setBackground(BG_CARD_HOVER);
					btn.setBorder(BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(ACCENT_GOLD, 1),
						BorderFactory.createEmptyBorder(4, 8, 4, 8)
					));
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				btn.setBackground(BG_PANEL);
				btn.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(BORDER_DEFAULT, 1),
					BorderFactory.createEmptyBorder(4, 8, 4, 8)
				));
			}
		});
		return btn;
	}

	public static JButton createDangerButton(String text)
	{
		JButton btn = createSecondaryButton(text);
		btn.setForeground(DANGER_RED);
		return btn;
	}

	public static JLabel createBadge(String text, Color fg, Color bg)
	{
		JLabel badge = new JLabel(text);
		badge.setOpaque(true);
		badge.setFont(getSupportFont(true));
		badge.setForeground(fg);
		badge.setBackground(bg);
		badge.setHorizontalAlignment(SwingConstants.CENTER);
		badge.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 95), 1),
			BorderFactory.createEmptyBorder(2, 6, 2, 6)
		));
		return badge;
	}

	public static JLabel createSectionHeading(String text)
	{
		JLabel lbl = new JLabel(text);
		lbl.setFont(getSectionHeadingFont());
		lbl.setForeground(TEXT_PRIMARY);
		return lbl;
	}

	public static JLabel createCardHeading(String text)
	{
		JLabel lbl = new JLabel(text);
		lbl.setFont(getCardHeadingFont());
		lbl.setForeground(TEXT_SECONDARY);
		return lbl;
	}

	/**
	 * Creates a collision-proof two-column row using BorderLayout(CENTER, EAST) where
	 * the right component gets its exact preferred width and the left component gets
	 * 100% of the remaining width without ever collapsing to minimumSize or overlapping.
	 */
	public static JPanel createSplitRow(Component left, Component right)
	{
		JPanel row = new JPanel(new BorderLayout(SPACE_SM, 0));
		row.setOpaque(false);
		row.add(left, BorderLayout.CENTER);
		if (right != null)
		{
			row.add(right, BorderLayout.EAST);
		}
		return row;
	}

	/**
	 * Creates a compact form field row with a descriptive label on the left and an input
	 * control on the right.
	 */
	public static JPanel createFormControlRow(String labelText, JComponent control)
	{
		JPanel row = new JPanel(new BorderLayout(SPACE_SM, 0));
		row.setOpaque(false);
		row.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));

		JLabel label = new JLabel(labelText);
		label.setFont(getBodyFont(false));
		label.setForeground(TEXT_SECONDARY);
		label.setPreferredSize(new Dimension(112, CONTROL_HEIGHT));

		row.add(label, BorderLayout.WEST);
		row.add(control, BorderLayout.CENTER);
		return row;
	}

	/**
	 * Creates a compact aligned key-value stat row with optional subtle background shading.
	 */
	public static JPanel createKeyValueRow(String keyText, JLabel valueLabel, boolean alternateBg)
	{
		JPanel row = new JPanel(new BorderLayout(SPACE_SM, 0));
		if (alternateBg)
		{
			row.setOpaque(true);
			row.setBackground(BG_ROW_ALT);
		}
		else
		{
			row.setOpaque(false);
		}
		row.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));

		JLabel keyLbl = new JLabel(keyText);
		keyLbl.setFont(getBodyFont(false));
		keyLbl.setForeground(TEXT_SECONDARY);

		valueLabel.setFont(getBodyFont(true));
		valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		row.add(keyLbl, BorderLayout.CENTER);
		row.add(valueLabel, BorderLayout.EAST);
		return row;
	}

	public static void styleTextField(JTextField field)
	{
		field.setBackground(BG_SECONDARY);
		field.setForeground(TEXT_PRIMARY);
		field.setCaretColor(ACCENT_GOLD);
		field.setFont(getBodyFont(false));
		field.setPreferredSize(new Dimension(100, CONTROL_HEIGHT));
		field.setMinimumSize(new Dimension(60, CONTROL_HEIGHT));
		field.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORDER_DEFAULT, 1),
			BorderFactory.createEmptyBorder(3, 6, 3, 6)
		));
	}

	public static void styleComboBox(JComboBox<?> combo)
	{
		combo.setBackground(BG_SECONDARY);
		combo.setForeground(TEXT_PRIMARY);
		combo.setFont(getBodyFont(false));
		combo.setPreferredSize(new Dimension(110, CONTROL_HEIGHT));
		combo.setMinimumSize(new Dimension(70, CONTROL_HEIGHT));
		combo.setBorder(BorderFactory.createLineBorder(BORDER_DEFAULT, 1));
	}

	public static void styleSpinner(JSpinner spinner)
	{
		spinner.setBackground(BG_SECONDARY);
		spinner.setForeground(TEXT_PRIMARY);
		spinner.setFont(getBodyFont(true));
		spinner.setPreferredSize(new Dimension(95, CONTROL_HEIGHT));
		spinner.setMinimumSize(new Dimension(70, CONTROL_HEIGHT));
		spinner.setBorder(BorderFactory.createLineBorder(BORDER_DEFAULT, 1));
		Component editor = spinner.getEditor();
		if (editor instanceof JSpinner.DefaultEditor)
		{
			JTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
			tf.setBackground(BG_SECONDARY);
			tf.setForeground(TEXT_PRIMARY);
			tf.setCaretColor(ACCENT_GOLD);
			tf.setFont(getBodyFont(true));
			tf.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
		}
	}

	public static void styleCheckBox(JCheckBox cb)
	{
		cb.setOpaque(false);
		cb.setFont(getBodyFont(false));
		cb.setForeground(TEXT_PRIMARY);
		cb.setFocusPainted(false);
		cb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		cb.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
	}

	public static void styleScrollPane(JScrollPane scrollPane)
	{
		scrollPane.setBackground(BG_MAIN);
		scrollPane.getViewport().setBackground(BG_MAIN);
		scrollPane.setBorder(null);
		scrollPane.getVerticalScrollBar().setUnitIncrement(18);
		scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(7, 0));
		scrollPane.getVerticalScrollBar().setUI(new BasicScrollBarUI()
		{
			@Override
			protected void configureScrollBarColors()
			{
				this.thumbColor = BORDER_DEFAULT;
				this.trackColor = BG_MAIN;
			}

			@Override
			protected JButton createDecreaseButton(int orientation)
			{
				return createZeroButton();
			}

			@Override
			protected JButton createIncreaseButton(int orientation)
			{
				return createZeroButton();
			}

			private JButton createZeroButton()
			{
				JButton b = new JButton();
				b.setPreferredSize(new Dimension(0, 0));
				return b;
			}
		});
	}

	// ==================================================
	// 6. NUMBER & CURRENCY FORMATTING
	// ==================================================
	public static String formatGp(long gp)
	{
		return QuantityFormatter.formatNumber(gp) + " GP";
	}

	public static String formatSignedGp(long gp)
	{
		if (gp > 0)
		{
			return "+" + QuantityFormatter.formatNumber(gp) + " GP";
		}
		return QuantityFormatter.formatNumber(gp) + " GP";
	}

	public static String formatCompactGp(long gp)
	{
		long abs = Math.abs(gp);
		String sign = gp > 0 ? "+" : (gp < 0 ? "-" : "");
		if (abs >= 1_000_000L)
		{
			return String.format("%s%.2fM", sign, abs / 1_000_000.0);
		}
		if (abs >= 10_000L)
		{
			return String.format("%s%.1fK", sign, abs / 1_000.0);
		}
		return (gp > 0 ? "+" : "") + QuantityFormatter.formatNumber(gp);
	}

	public static String formatUnsignedCompactGp(long gp)
	{
		long abs = Math.abs(gp);
		if (abs >= 1_000_000L)
		{
			return String.format("%.2fM GP", abs / 1_000_000.0);
		}
		if (abs >= 10_000L)
		{
			return String.format("%.1fK GP", abs / 1_000.0);
		}
		return QuantityFormatter.formatNumber(gp) + " GP";
	}

	public static long calculateHourlyProfit(long profitPerAlch)
	{
		return profitPerAlch * ALCH_CASTS_PER_HOUR;
	}

	public static String formatHourlyProfit(long profitPerAlch)
	{
		long hourly = calculateHourlyProfit(profitPerAlch);
		return formatCompactGp(hourly) + " GP/hr";
	}

	public static String formatRoi(double roi)
	{
		if (Double.isNaN(roi) || Double.isInfinite(roi))
		{
			return "0.0%";
		}
		return String.format("%s%.1f%%", roi > 0 ? "+" : "", roi);
	}

	public static Color getProfitColor(long profit)
	{
		if (profit > 0)
		{
			return SUCCESS_GREEN;
		}
		if (profit < 0)
		{
			return DANGER_RED;
		}
		return TEXT_SECONDARY;
	}

	public static String formatEpochTimestamp(long epochSec)
	{
		if (epochSec <= 0)
		{
			return "Syncing...";
		}
		return TIME_FORMATTER.format(Instant.ofEpochSecond(epochSec));
	}

	/**
	 * Renders the custom 24x24 OSRS Gold High-Alchemy icon used on the RuneLite sidebar toolbar.
	 */
	public static BufferedImage createPluginToolbarIcon()
	{
		BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		g.setColor(BG_CARD);
		g.fillOval(1, 1, 22, 22);

		g.setStroke(new BasicStroke(1.8f));
		g.setPaint(new GradientPaint(0, 0, ACCENT_GOLD_HOVER, 24, 24, BORDER_GOLD_ACCENT));
		g.drawOval(2, 2, 20, 20);

		int[] xs = {12, 18, 12, 6};
		int[] ys = {5, 12, 19, 12};
		g.setPaint(new GradientPaint(6, 5, ACCENT_GOLD_HOVER, 18, 19, ACCENT_GOLD));
		g.fillPolygon(xs, ys, 4);

		g.setColor(SUCCESS_GREEN);
		g.fillOval(10, 10, 4, 4);

		g.dispose();
		return image;
	}
}
