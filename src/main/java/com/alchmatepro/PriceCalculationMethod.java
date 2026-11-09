package com.alchmatepro;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Defines how the item purchase cost is estimated from Grand Exchange market data.
 */
@Getter
@RequiredArgsConstructor
public enum PriceCalculationMethod
{
	INSTANT_BUY_HIGH("Instant Buy (Latest High)", "Uses the most recent instant-buy (high) trade price"),
	STABLE_1H_HIGH("Stable 1h Average", "Uses the 1-hour rolling average buy price to smooth short-term spikes"),
	ROLLING_5M_HIGH("Recent 5m Average", "Uses the 5-minute rolling average buy price"),
	CONSERVATIVE_HIGH("Conservative (Max High/1h)", "Uses the higher of Latest Instant-Buy and 1h Average for safety"),
	PATIENT_OFFER_LOW("Patient Buy Offer (Latest Low)", "Uses the latest instant-sell (low) price for patient GE buy offers");

	private final String displayName;
	private final String description;

	@Override
	public String toString()
	{
		return displayName;
	}
}
