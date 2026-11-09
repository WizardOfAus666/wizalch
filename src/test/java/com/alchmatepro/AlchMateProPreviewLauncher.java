package com.alchmatepro;

import com.google.gson.Gson;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import okhttp3.OkHttpClient;

/**
 * Instant desktop preview harness that opens the real AlchMateProPanel connected to the
 * live OSRS Wiki Real-Time Prices API.
 * Supports resizing from narrow RuneLite sidebar widths (260px) to wide desktop layouts (1200px+).
 */
public class AlchMateProPreviewLauncher
{
	public static void main(String[] args)
	{
		SwingUtilities.invokeLater(() -> {
			try
			{
				UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
			}
			catch (Exception ignored)
			{
			}

			Gson gson = new Gson();
			OkHttpClient okHttpClient = new OkHttpClient();

			AlchMateProConfig defaultConfig = new AlchMateProConfig()
			{
			};

			CacheService cacheService = new CacheService(gson);
			WikiPriceDataProvider wikiProvider = new WikiPriceDataProvider(okHttpClient, gson);
			ItemMetadataService metadataService = new ItemMetadataService(okHttpClient, gson, cacheService);
			ProfitCalculator profitCalculator = new ProfitCalculator();
			PriceValidator priceValidator = new PriceValidator();
			RecommendationEngine recommendationEngine = new RecommendationEngine(priceValidator);
			WatchlistService watchlistService = new WatchlistService(null, gson);
			AlertService alertService = new AlertService(null, defaultConfig, watchlistService);
			PriceHistoryService priceHistoryService = new PriceHistoryService(okHttpClient, gson);
			IconService iconService = new IconService(null);

			MarketDataService marketDataService = new MarketDataService(
				wikiProvider,
				metadataService,
				cacheService,
				profitCalculator,
				priceValidator,
				recommendationEngine,
				alertService,
				defaultConfig
			);

			AlchMateProPanel panel = new AlchMateProPanel(
				marketDataService,
				profitCalculator,
				recommendationEngine,
				priceHistoryService,
				watchlistService,
				alertService,
				cacheService,
				iconService,
				defaultConfig,
				null
			);

			JFrame frame = new JFrame("WIZALCH — OSRS High Alchemy Intelligence");
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.getContentPane().setBackground(AlchTheme.BG_MAIN);
			frame.setLayout(new BorderLayout());
			frame.add(panel, BorderLayout.CENTER);
			frame.setSize(new Dimension(350, 840));
			frame.setMinimumSize(new Dimension(245, 500));
			frame.setLocationRelativeTo(null);

			frame.addComponentListener(new ComponentAdapter()
			{
				@Override
				public void componentResized(ComponentEvent e)
				{
					panel.revalidate();
					panel.repaint();
				}
			});

			frame.setVisible(true);
			marketDataService.startUp();
		});
	}
}
