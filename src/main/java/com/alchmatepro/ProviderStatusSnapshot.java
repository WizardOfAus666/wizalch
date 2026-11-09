package com.alchmatepro;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Telemetry snapshot displayed on the Market Data transparency page (Section 14).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderStatusSnapshot
{
	public enum ConnectionState
	{
		LIVE("LIVE DATA"),
		CACHED("USING CACHE"),
		DEGRADED_FALLBACK("FALLBACK ACTIVE"),
		REFRESHING("REFRESHING..."),
		OFFLINE_ERROR("DATA UNAVAILABLE");

		private final String label;

		ConnectionState(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	private String activeProviderName;
	private String primaryProviderName;
	private String primaryProviderStatusNote;
	private String secondaryProviderName;
	@Builder.Default
	private ConnectionState connectionState = ConnectionState.REFRESHING;

	private long lastSuccessfulUpdateEpochSec;
	private long lastAttemptedUpdateEpochSec;

	private int totalItemsLoaded;
	private int validPricesCount;
	private int profitableItemsCount;
	private int stalePricesCount;
	private int failedOrMissingItemsCount;

	private String cacheStatusDescription;
	private String lastErrorMessage;
	private long natureRuneLivePrice;
	private long fireRuneLivePrice;
}
