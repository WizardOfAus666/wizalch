package com.alchmatepro;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Provides asynchronous OSRS item icons via RuneLite's {@link ItemManager} with a clean
 * custom-painted fallback badge when running outside a live client cache.
 */
@Singleton
public class IconService
{
	private final ItemManager itemManager;

	@Inject
	public IconService(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	/**
	 * Attaches an item's 32x32 icon to the target {@link JLabel}.
	 */
	public void attachItemIcon(JLabel label, int itemId, String itemName)
	{
		if (label == null)
		{
			return;
		}
		label.setPreferredSize(new Dimension(32, 32));
		label.setMinimumSize(new Dimension(32, 32));
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setVerticalAlignment(SwingConstants.CENTER);

		if (itemManager != null && itemId > 0)
		{
			try
			{
				AsyncBufferedImage asyncImg = itemManager.getImage(itemId);
				if (asyncImg != null)
				{
					asyncImg.addTo(label);
					return;
				}
			}
			catch (Throwable ignored)
			{
				// Fallback to vector monogram icon if ItemManager requires active game client state
			}
		}

		label.setIcon(new ImageIcon(createFallbackItemIcon(itemName)));
	}

	private BufferedImage createFallbackItemIcon(String itemName)
	{
		BufferedImage img = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		g.setColor(AlchTheme.SURFACE_ELEVATED);
		g.fillRoundRect(1, 1, 28, 28, 8, 8);

		g.setColor(AlchTheme.BORDER_GOLD_MUTED);
		g.drawRoundRect(1, 1, 27, 27, 8, 8);

		String initials = "GP";
		if (itemName != null && !itemName.trim().isEmpty())
		{
			String trimmed = itemName.trim();
			initials = trimmed.substring(0, Math.min(2, trimmed.length())).toUpperCase();
		}

		g.setFont(new Font("SansSerif", Font.BOLD, 11));
		g.setColor(AlchTheme.ACCENT_GOLD_BRIGHT);
		FontMetrics fm = g.getFontMetrics();
		int x = (30 - fm.stringWidth(initials)) / 2;
		int y = ((30 - fm.getHeight()) / 2) + fm.getAscent();
		g.drawString(initials, x, y);

		g.dispose();
		return img;
	}
}
