package io.huze.glamourer.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.providers.ModelProvider;

@Slf4j
@RequiredArgsConstructor
final class ModelColors
{
	private final ModelProvider modelProvider;
	private final Map<Integer, Set<Short>> cache = new HashMap<>();

	Set<Short> of(int modelId)
	{
		if (modelId <= 0)
		{
			return Collections.emptySet();
		}
		return cache.computeIfAbsent(modelId, id -> {
			try
			{
				ModelDefinition model = modelProvider.provide(id);
				if (model == null || model.faceColors == null)
				{
					return Collections.emptySet();
				}
				Set<Short> colors = new HashSet<>();
				for (short color : model.faceColors)
				{
					colors.add(color);
				}
				return colors;
			}
			catch (IOException e)
			{
				log.debug("Failed to load model {}: {}", id, e.getMessage());
				return Collections.emptySet();
			}
		});
	}

	Set<Short> of(NpcDefinition npc)
	{
		Set<Short> colors = new HashSet<>();
		if (npc.models != null)
		{
			for (int modelId : npc.models)
			{
				colors.addAll(of(modelId));
			}
		}
		return colors;
	}

	/// Find NPC models carrying colors the item's icon does not.
	List<Integer> extraModels(ItemDefinition item, List<NpcDefinition> npcs)
	{
		Set<Short> seen = new HashSet<>(of(item.inventoryModel));
		List<Integer> extra = new ArrayList<>();

		for (NpcDefinition npc : npcs)
		{
			if (npc.models == null)
			{
				continue;
			}
			for (int modelId : npc.models)
			{
				if (extra.contains(modelId))
				{
					continue;
				}
				Set<Short> colors = of(modelId);
				if (!seen.containsAll(colors))
				{
					seen.addAll(colors);
					extra.add(modelId);
				}
			}
		}
		return extra;
	}
}
