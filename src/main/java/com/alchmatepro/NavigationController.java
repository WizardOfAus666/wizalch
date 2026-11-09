package com.alchmatepro;

/**
 * Navigation callback contract allowing child panels to switch active views
 * or inspect a specific {@link AlchMateItem}.
 */
public interface NavigationController
{
	enum Section
	{
		DASHBOARD("Dashboard"),
		ALCH_SCANNER("Scanner"),
		ITEM_ANALYSIS("Analysis"),
		PROFIT_CALCULATOR("Calculator"),
		WATCHLIST("Watchlist"),
		MARKET_DATA("Market"),
		SETTINGS("Settings");

		private final String label;

		Section(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	void navigateTo(Section section);

	void openItemAnalysis(AlchMateItem item);

	void openProfitCalculator(AlchMateItem item);
}
