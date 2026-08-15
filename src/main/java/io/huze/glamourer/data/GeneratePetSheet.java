package io.huze.glamourer.data;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.providers.ModelProvider;

@Slf4j
public class GeneratePetSheet
{
	private final Collection<ItemDefinition> itemDefs;
	private final Collection<NpcDefinition> npcDefs;
	private final ModelProvider modelProvider;
	private final Csv csv;
	private final Map<Integer, Set<Short>> colorCache = new HashMap<>();

	public GeneratePetSheet(Collection<ItemDefinition> itemDefs, Collection<NpcDefinition> npcDefs, ModelProvider modelProvider, Csv csv)
	{
		this.itemDefs = itemDefs;
		this.npcDefs = npcDefs;
		this.modelProvider = modelProvider;
		this.csv = csv;
	}

	public void export(File out, File wikiPetsJson) throws IOException
	{
		WikiPetsFile wiki = readPets(wikiPetsJson);
		List<PetRow> rows = rows(wiki.pets);
		try (PrintWriter writer = csv.open(out, "wiki pets fetched: " + wiki.fetched_utc))
		{
			writer.println(PetRow.CSV_HEADER);
			for (PetRow row : rows)
			{
				writer.println(row.toCsvString());
			}
		}
		log.info("Wrote to " + out.getAbsolutePath());
	}

	private List<PetRow> rows(List<WikiPet> pets)
	{
		Map<Integer, ItemDefinition> itemsById = new HashMap<>();
		for (ItemDefinition item : itemDefs)
		{
			itemsById.put(item.id, item);
		}

		Map<Integer, NpcDefinition> npcsById = new HashMap<>();
		for (NpcDefinition npc : npcDefs)
		{
			npcsById.put(npc.id, npc);
		}

		List<PetRow> rows = new ArrayList<>();
		for (WikiPet pet : pets)
		{
			for (WikiVersion version : pet.versions)
			{
				List<NpcDefinition> npcs = knownNpcs(pet, version, npcsById);
				if (npcs.isEmpty())
				{
					continue;
				}
				for (int itemId : version.item_ids)
				{
					ItemDefinition item = itemsById.get(itemId);
					if (item == null)
					{
						log.warn("Pet \"{}\": wiki item {} is not in the cache", pet.page, itemId);
						continue;
					}
					rows.add(new PetRow(itemId, ids(npcs), extraModels(item, npcs),
						comment(pet, version)));
				}
			}
		}

		rows.sort(Comparator.comparingInt(PetRow::getItemId));
		warnSharedNpcs(rows);
		log.info("Paired {} pet items from {} wiki pets", rows.size(), pets.size());
		return rows;
	}

	private void warnSharedNpcs(List<PetRow> rows)
	{
		Map<Integer, Integer> itemByNpc = new HashMap<>();
		for (PetRow row : rows)
		{
			for (int npcId : row.getNpcIds())
			{
				Integer previous = itemByNpc.put(npcId, row.getItemId());
				if (previous != null)
				{
					log.warn("Npc {} is claimed by items {} and {}; consumers keep only the last",
						npcId, previous, row.getItemId());
				}
			}
		}
	}

	private List<NpcDefinition> knownNpcs(WikiPet pet, WikiVersion version, Map<Integer, NpcDefinition> npcsById)
	{
		List<NpcDefinition> known = new ArrayList<>();
		for (int npcId : version.npc_ids)
		{
			NpcDefinition npc = npcsById.get(npcId);
			if (npc != null)
			{
				known.add(npc);
			}
			else
			{
				log.warn("Pet \"{}\" ({}): wiki npc {} is not in the cache", pet.page, version.name, npcId);
			}
		}
		return known;
	}

	private static List<Integer> ids(List<NpcDefinition> npcs)
	{
		List<Integer> ids = new ArrayList<>();
		for (NpcDefinition npc : npcs)
		{
			ids.add(npc.id);
		}
		return ids;
	}

	private List<Integer> extraModels(ItemDefinition item, List<NpcDefinition> npcs)
	{
		Set<Short> seen = new HashSet<>(modelColors(item.inventoryModel));
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
				Set<Short> colors = modelColors(modelId);
				if (!seen.containsAll(colors))
				{
					seen.addAll(colors);
					extra.add(modelId);
				}
			}
		}
		return extra;
	}

	private String comment(WikiPet pet, WikiVersion version)
	{
		return version.name == null || version.name.isBlank()
			? pet.page
			: pet.page + " (" + version.name + ")";
	}

	/// Cached: a pet's follower and house copies share models.
	private Set<Short> modelColors(int modelId)
	{
		if (modelId <= 0)
		{
			return Collections.emptySet();
		}
		return colorCache.computeIfAbsent(modelId, id -> {
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

	private WikiPetsFile readPets(File wikiPetsJson) throws IOException
	{
		try (Reader reader = new InputStreamReader(new FileInputStream(wikiPetsJson), StandardCharsets.UTF_8))
		{
			return new Gson().fromJson(reader, WikiPetsFile.class);
		}
	}

	private static class WikiPetsFile
	{
		String fetched_utc;
		List<WikiPet> pets;
	}

	private static class WikiPet
	{
		String page;
		List<WikiVersion> versions = Collections.emptyList();
	}

	private static class WikiVersion
	{
		String name;
		List<Integer> item_ids = Collections.emptyList();
		List<Integer> npc_ids = Collections.emptyList();
	}
}
