package com.alchmatepro;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

/**
 * Custom-painted historical price sparkline and break-even margin chart for the Item Analysis view.
 */
public class PriceTrendChartPanel extends JPanel
{
	private List<PricePoint> points = new ArrayList<>();
	private long breakEvenPrice = 0L;

	public PriceTrendChartPanel()
	{
		setBackground(AlchTheme.BG_SECONDARY);
		setBorder(BorderFactory.createLineBorder(AlchTheme.BORDER_DEFAULT, 1));
		setPreferredSize(new Dimension(280, 165));
		setMinimumSize(new Dimension(200, 150));
	}

	public void setTrendData(List<PricePoint> pricePoints, long breakEvenPriceGp)
	{
		this.points = pricePoints != null ? new ArrayList<>(pricePoints) : new ArrayList<>();
		this.breakEvenPrice = breakEvenPriceGp;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		int w = getWidth();
		int h = getHeight();
		int padLeft = 14;
		int padRight = 14;
		int padTop = 28;
		int padBottom = 26;
		int plotW = Math.max(20, w - padLeft - padRight);
		int plotH = Math.max(20, h - padTop - padBottom);

		List<Integer> values = new ArrayList<>();
		for (PricePoint pt : points)
		{
			Integer val = pt.getEffectiveBuyPrice();
			if (val != null && val > 0)
			{
				values.add(val);
			}
		}

		if (values.size() < 2)
		{
			g2.setColor(AlchTheme.TEXT_MUTED);
			g2.setFont(AlchTheme.getBodyFont(false));
			String msg = "Historical trend loading / unavailable";
			int strW = g2.getFontMetrics().stringWidth(msg);
			g2.drawString(msg, Math.max(8, (w - strW) / 2), h / 2 + 4);
			g2.dispose();
			return;
		}

		long minVal = Long.MAX_VALUE;
		long maxVal = Long.MIN_VALUE;
		for (int v : values)
		{
			minVal = Math.min(minVal, v);
			maxVal = Math.max(maxVal, v);
		}
		if (breakEvenPrice > 0)
		{
			minVal = Math.min(minVal, breakEvenPrice);
			maxVal = Math.max(maxVal, breakEvenPrice);
		}
		long range = Math.max(1L, maxVal - minVal);

		// Subtle horizontal grid lines
		g2.setColor(AlchTheme.BORDER_SUBTLE_LINE);
		g2.drawLine(padLeft, padTop, w - padRight, padTop);
		g2.drawLine(padLeft, padTop + plotH / 2, w - padRight, padTop + plotH / 2);
		g2.drawLine(padLeft, padTop + plotH, w - padRight, padTop + plotH);

		// Draw break-even alchemy threshold line (dashed gold)
		if (breakEvenPrice > 0 && breakEvenPrice >= minVal && breakEvenPrice <= maxVal)
		{
			int beY = padTop + (int) (((double) (maxVal - breakEvenPrice) / range) * plotH);
			Stroke oldStroke = g2.getStroke();
			g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 4.0f, new float[]{5.0f, 4.0f}, 0.0f));
			g2.setColor(AlchTheme.ACCENT_GOLD);
			g2.drawLine(padLeft, beY, w - padRight, beY);
			g2.setStroke(oldStroke);
		}

		int n = values.size();
		int[] xs = new int[n];
		int[] ys = new int[n];
		for (int i = 0; i < n; i++)
		{
			xs[i] = padLeft + (int) (((double) i / (n - 1)) * plotW);
			ys[i] = padTop + (int) (((double) (maxVal - values.get(i)) / range) * plotH);
		}

		// Determine line colour: green if latest buy price is below break-even (profitable), else red/gold
		int latestPrice = values.get(n - 1);
		Color lineColor = (breakEvenPrice > 0 && latestPrice < breakEvenPrice)
			? AlchTheme.SUCCESS_GREEN
			: AlchTheme.DANGER_RED;

		// Gradient area fill under curve
		Polygon fillPoly = new Polygon();
		fillPoly.addPoint(xs[0], padTop + plotH);
		for (int i = 0; i < n; i++)
		{
			fillPoly.addPoint(xs[i], ys[i]);
		}
		fillPoly.addPoint(xs[n - 1], padTop + plotH);
		g2.setPaint(new GradientPaint(
			0, padTop, new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 70),
			0, padTop + plotH, new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 6)
		));
		g2.fillPolygon(fillPoly);

		// Price polyline
		g2.setColor(lineColor);
		g2.setStroke(new BasicStroke(2.0f));
		g2.drawPolyline(xs, ys, n);

		// End point dot
		g2.fillOval(xs[n - 1] - 4, ys[n - 1] - 4, 8, 8);

		// Header min/max labels
		g2.setFont(AlchTheme.getSupportFont(true));
		g2.setColor(AlchTheme.TEXT_SECONDARY);
		g2.drawString("Low: " + AlchTheme.formatUnsignedCompactGp(minVal), padLeft, 18);

		String highLabel = "High: " + AlchTheme.formatUnsignedCompactGp(maxVal);
		int hw = g2.getFontMetrics().stringWidth(highLabel);
		g2.drawString(highLabel, Math.max(padLeft + 90, w - padRight - hw), 18);

		// Footer legend
		g2.setColor(AlchTheme.ACCENT_GOLD);
		g2.drawString(
			"-- Break-even Ceiling: " + AlchTheme.formatGp(breakEvenPrice),
			padLeft,
			h - 8
		);

		g2.dispose();
	}
}
