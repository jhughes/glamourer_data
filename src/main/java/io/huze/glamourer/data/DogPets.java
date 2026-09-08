package io.huze.glamourer.data;

import io.huze.glamourer.data.GeneratePetSheet.WikiPet;
import io.huze.glamourer.data.GeneratePetSheet.WikiVersion;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.NpcDefinition;

@Slf4j
@RequiredArgsConstructor
final class DogPets
{
	private static final List<String> BREEDS = List.of(
		"Labrador", "Chihuahua", "Border collie", "Corgi", "Greyhound", "Husky",
		"Pug", "Samoyed", "Bernese mountain dog", "Shiba", "Spaniel", "Yorkie");
	private static final int VARIANTS = 3;

	private final Collection<ItemDefinition> itemDefs;
	private final Collection<NpcDefinition> npcDefs;

	List<WikiPet> pets()
	{
		List<WikiPet> pets = new ArrayList<>();
		for (String breed : BREEDS)
		{
			for (String name : List.of(breed, breed + " puppy"))
			{
				WikiPet pet = pet(name);
				if (pet != null)
				{
					pets.add(pet);
				}
			}
		}
		return pets;
	}

	private WikiPet pet(String name)
	{
		List<ItemDefinition> items = itemDefs.stream()
			.filter(item -> name.equalsIgnoreCase(item.name))
			.sorted(Comparator.comparingInt(item -> item.id))
			.collect(Collectors.toList());
		if (items.size() != VARIANTS)
		{
			log.warn("{}: expected {} items, found {}; skipped", name, VARIANTS, items.size());
			return null;
		}

		Map<String, List<NpcDefinition>> byOptions = new LinkedHashMap<>();
		npcDefs.stream()
			.filter(npc -> name.equalsIgnoreCase(npc.name) && options(npc).contains("Pick-up"))
			.sorted(Comparator.comparingInt(npc -> npc.id))
			.forEach(npc -> byOptions.computeIfAbsent(String.join(",", options(npc)), k -> new ArrayList<>()).add(npc));

		List<WikiVersion> versions = new ArrayList<>();
		for (int i = 0; i < VARIANTS; i++)
		{
			versions.add(new WikiVersion(String.valueOf(i + 1), List.of(items.get(i).id), new ArrayList<>()));
		}
		for (Map.Entry<String, List<NpcDefinition>> run : byOptions.entrySet())
		{
			List<NpcDefinition> npcs = run.getValue();
			if (npcs.size() != VARIANTS)
			{
				log.warn("{}: {} npcs with options [{}], not {}; skipped", name, npcs.size(), run.getKey(), VARIANTS);
				continue;
			}
			for (int i = 0; i < VARIANTS; i++)
			{
				versions.get(i).npc_ids.add(npcs.get(i).id);
			}
		}

		if (versions.get(0).npc_ids.isEmpty())
		{
			log.warn("{}: no npcs to pair with; skipped", name);
			return null;
		}
		return new WikiPet(name, versions);
	}

	private static List<String> options(NpcDefinition npc)
	{
		List<String> texts = new ArrayList<>();
		if (npc.ops != null && npc.ops.ops != null)
		{
			for (var op : npc.ops.ops)
			{
				texts.add(op == null ? "-" : op.text);
			}
		}
		return texts;
	}
}
