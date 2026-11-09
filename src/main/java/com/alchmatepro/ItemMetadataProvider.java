package com.alchmatepro;

import java.io.IOException;
import java.util.Map;

/**
 * Abstraction for OSRS item metadata providers (item IDs, names, high/low alch values, GE buy limits, members flags).
 */
public interface ItemMetadataProvider
{
	/**
	 * Fetches a map of itemId -> {@link ItemMetadata} for all supported OSRS items.
	 */
	Map<Integer, ItemMetadata> fetchItemMetadata() throws IOException;
}
