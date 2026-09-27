package io.huze.glamourer.data;

import net.runelite.cache.definitions.ItemDefinition;

final class Items
{
	static boolean hasName(ItemDefinition def)
	{
		return def.name != null
			&& !def.name.isBlank()
			&& !def.name.equalsIgnoreCase("null");
	}
}
