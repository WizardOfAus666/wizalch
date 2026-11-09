package com.alchmatepro;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import javax.imageio.ImageIO;
import net.runelite.client.plugins.Plugin;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Verifies RuneLite Plugin Hub compliance requirements:
 * - Valid runelite-plugin.properties metadata and main plugin class
 * - Root icon.png asset (PNG format, <= 48x72 pixels)
 * - LICENSE (BSD 2-Clause) and README.md presence
 */
public class PluginHubComplianceTest
{
	@Test
	public void testGenerateAndVerifyPluginHubIcon() throws Exception
	{
		File rootIcon = new File("icon.png");
		if (!rootIcon.exists())
		{
			BufferedImage img = renderPluginHubBannerIcon(48, 72);
			ImageIO.write(img, "PNG", rootIcon);
		}

		assertTrue("Root icon.png must exist for Plugin Hub", rootIcon.exists());
		BufferedImage readBack = ImageIO.read(rootIcon);
		assertNotNull("icon.png must be a valid PNG image", readBack);
		assertTrue("icon.png width must be <= 48px", readBack.getWidth() <= 48);
		assertTrue("icon.png height must be <= 72px", readBack.getHeight() <= 72);
		assertEquals(48, readBack.getWidth());
		assertEquals(72, readBack.getHeight());
	}

	@Test
	public void testRuneLitePluginPropertiesAndMainClass() throws Exception
	{
		File propsFile = new File("runelite-plugin.properties");
		assertTrue("runelite-plugin.properties must exist", propsFile.exists());

		Properties props = new Properties();
		try (InputStreamReader reader = new InputStreamReader(new FileInputStream(propsFile), StandardCharsets.UTF_8))
		{
			props.load(reader);
		}

		assertEquals("WIZALCH", props.getProperty("displayName"));
		assertNotNull(props.getProperty("author"));
		assertFalse(props.getProperty("author").trim().isEmpty());
		assertNotNull(props.getProperty("description"));
		assertFalse(props.getProperty("description").trim().isEmpty());
		assertNotNull(props.getProperty("tags"));
		assertNotNull(props.getProperty("version"));
		assertEquals("standard", props.getProperty("build"));
		assertNotNull("warning property must disclose external API usage", props.getProperty("warning"));

		String pluginClassName = props.getProperty("plugins");
		assertEquals("com.alchmatepro.AlchMateProPlugin", pluginClassName);

		Class<?> clazz = Class.forName(pluginClassName);
		assertTrue("Main plugin class must extend RuneLite Plugin", Plugin.class.isAssignableFrom(clazz));
	}

	@Test
	public void testLicenseAndReadmeExist()
	{
		File license = new File("LICENSE");
		assertTrue("LICENSE file must exist in repository root", license.exists() && license.length() > 100);

		File readme = new File("README.md");
		assertTrue("README.md file must exist in repository root", readme.exists() && readme.length() > 500);
	}

	private static BufferedImage renderPluginHubBannerIcon(int width, int height)
	{
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = img.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		// Dark OSRS card background with subtle gold border
		GradientPaint bgPaint = new GradientPaint(
			0, 0, new Color(0x1D, 0x20, 0x27),
			0, height, new Color(0x10, 0x11, 0x14)
		);
		g2.setPaint(bgPaint);
		g2.fillRoundRect(1, 1, width - 2, height - 2, 10, 10);

		g2.setColor(new Color(0x34, 0x39, 0x44));
		g2.setStroke(new BasicStroke(1.2f));
		g2.drawRoundRect(1, 1, width - 3, height - 3, 10, 10);

		// Outer gold alchemy diamond emblem
		int cx = width / 2;
		int cy = 28;
		Polygon outerDiamond = new Polygon(
			new int[]{cx, cx + 17, cx, cx - 17},
			new int[]{cy - 17, cy, cy + 17, cy},
			4
		);
		GradientPaint goldGrad = new GradientPaint(
			cx, cy - 17, new Color(0xF5, 0xCF, 0x65),
			cx, cy + 17, new Color(0xC9, 0x97, 0x22)
		);
		g2.setPaint(goldGrad);
		g2.fillPolygon(outerDiamond);

		g2.setColor(new Color(0x10, 0x11, 0x14));
		g2.setStroke(new BasicStroke(1.5f));
		g2.drawPolygon(outerDiamond);

		// Inner dark core coin circle
		g2.setColor(new Color(0x17, 0x19, 0x1E));
		g2.fillOval(cx - 9, cy - 9, 18, 18);

		g2.setColor(new Color(0x35, 0xD9, 0x87));
		g2.setStroke(new BasicStroke(1.2f));
		g2.drawOval(cx - 9, cy - 9, 18, 18);

		// "W" monogram inside emblem
		g2.setColor(new Color(0xE7, 0xB8, 0x3F));
		g2.setFont(new Font("SansSerif", Font.BOLD, 11));
		FontMetrics fmW = g2.getFontMetrics();
		String wText = "W";
		g2.drawString(wText, cx - fmW.stringWidth(wText) / 2, cy + (fmW.getAscent() - fmW.getDescent()) / 2);

		// Bottom "ALCH" label Pill
		g2.setColor(new Color(0x22, 0x26, 0x30));
		g2.fillRoundRect(5, 51, width - 10, 15, 6, 6);
		g2.setColor(new Color(0xE7, 0xB8, 0x3F));
		g2.setStroke(new BasicStroke(1f));
		g2.drawRoundRect(5, 51, width - 10, 15, 6, 6);

		g2.setFont(new Font("SansSerif", Font.BOLD, 9));
		FontMetrics fm = g2.getFontMetrics();
		String label = "ALCH";
		g2.drawString(label, cx - fm.stringWidth(label) / 2, 51 + 11);

		g2.dispose();
		return img;
	}
}
