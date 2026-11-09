package com.alchmatepro;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents static OSRS item metadata retrieved from the mapping endpoint or ItemManager.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemMetadata
{
	private int id;
	private String name;
	private String examine;
	private boolean members;
	private int lowAlchValue;
	private int highAlchValue;
	private int buyLimit;
	private int baseValue;
	private String iconName;

	/**
	 * Returns true if this item has a positive High Level Alchemy value.
	 */
	public boolean isAlchable()
	{
		return id > 0 && name != null && !name.trim().isEmpty() && highAlchValue > 0;
	}
}
