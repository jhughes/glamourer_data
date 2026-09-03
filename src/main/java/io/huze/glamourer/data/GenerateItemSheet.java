package io.huze.glamourer.data;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.providers.ModelProvider;

@Slf4j
public class GenerateItemSheet
{
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

	private final Collection<ItemDefinition> itemDefs;
	private final ModelProvider modelProvider;
	private final Csv csv;
	private final Map<Integer, ModelFaces> faceCache = new HashMap<>();

	public GenerateItemSheet(Collection<ItemDefinition> itemDefs, ModelProvider modelProvider, Csv csv)
	{
		this.itemDefs = itemDefs;
		this.modelProvider = modelProvider;
		this.csv = csv;
	}

	public void export(File out, File wikiItemsJson) throws IOException
	{
		WikiItemsFile wiki = readWikiItems(wikiItemsJson);
		List<ItemRow> rows = rows(wiki.items);
		rows.removeIf(row -> !row.isWorthSerializing());

		try (PrintWriter writer = csv.open(out, "wiki items fetched: " + wiki.fetched_utc))
		{
			writer.println(ItemRow.CSV_HEADER);
			for (ItemRow row : rows)
			{
				writer.println(row.toCsvString());
			}
		}
		log.info("Wrote to " + out.getAbsolutePath());
	}

	public void exportStackVariants(File out) throws IOException
	{
		List<int[]> pairs = new ArrayList<>();
		for (ItemDefinition item : itemDefs)
		{
			if (item.countObj == null)
			{
				continue;
			}
			for (int variant : item.countObj)
			{
				if (variant != 0)
				{
					pairs.add(new int[]{item.id, variant});
				}
			}
		}
		pairs.sort(Comparator.<int[]>comparingInt(p -> p[0]).thenComparingInt(p -> p[1]));

		try (PrintWriter writer = csv.open(out))
		{
			writer.println("id,variantId");
			for (int[] pair : pairs)
			{
				writer.println(pair[0] + "," + pair[1]);
			}
		}
		log.info("Wrote to " + out.getAbsolutePath());
	}

	private List<ItemRow> rows(List<WikiItem> wikiItems)
	{
		Map<Integer, WikiFacts> wiki = wikiFacts(wikiItems);

		List<ItemRow> rows = new ArrayList<>();
		for (ItemDefinition idef : itemDefs)
		{
			if (filterItem(idef))
			{
				continue;
			}
			WikiFacts facts = wiki.getOrDefault(idef.id, WikiFacts.NONE);

			// Seed with what the inventory model shows; a worn model earns a column only by adding
			// a color or texture not yet seen.
			ModelFaces inventory = faces(idef.inventoryModel);
			Set<Short> seenColors = new HashSet<>(inventory.colors);
			Set<Short> seenTextures = new HashSet<>(inventory.textures);

			rows.add(new ItemRow(
				idef.id,
				facts.releaseDate,
				facts.removalDate,
				// Anything the player can equip may be useful after the quest, so is not treated as a quest item.
				facts.isQuestItem && !equippable(idef),
				idef.category,
				modelIfNew(idef.maleModel0, seenColors, seenTextures),
				modelIfNew(idef.maleModel1, seenColors, seenTextures),
				modelIfNew(idef.maleModel2, seenColors, seenTextures),
				modelIfNew(idef.femaleModel0, seenColors, seenTextures),
				modelIfNew(idef.femaleModel1, seenColors, seenTextures),
				modelIfNew(idef.femaleModel2, seenColors, seenTextures)));
		}

		rows.sort(Comparator.comparingInt(ItemRow::getId));
		return rows;
	}

	/// The model's id if it introduces a color or texture not yet seen, else -1; seen grows to match.
	private int modelIfNew(int modelId, Set<Short> seenColors, Set<Short> seenTextures)
	{
		if (modelId <= 0)
		{
			return -1;
		}

		ModelFaces model = faces(modelId);
		if (seenColors.containsAll(model.colors) && seenTextures.containsAll(model.textures))
		{
			return -1;
		}
		seenColors.addAll(model.colors);
		seenTextures.addAll(model.textures);
		return modelId;
	}

	private ModelFaces faces(int modelId)
	{
		if (modelId <= 0)
		{
			return ModelFaces.NONE;
		}
		return faceCache.computeIfAbsent(modelId, id -> {
			try
			{
				ModelDefinition model = modelProvider.provide(id);
				return model == null ? ModelFaces.NONE : new ModelFaces(model);
			}
			catch (IOException e)
			{
				log.debug("Failed to load model {}: {}", id, e.getMessage());
				return ModelFaces.NONE;
			}
		});
	}

	private static class ModelFaces
	{
		static final ModelFaces NONE = new ModelFaces();

		final Set<Short> colors = new HashSet<>();
		final Set<Short> textures = new HashSet<>();

		private ModelFaces()
		{
		}

		ModelFaces(ModelDefinition model)
		{
			if (model.faceColors != null)
			{
				for (short color : model.faceColors)
				{
					colors.add(color);
				}
			}
			if (model.faceTextures != null)
			{
				for (short texture : model.faceTextures)
				{
					if (texture != -1)
					{
						textures.add(texture);
					}
				}
			}
		}
	}

	private boolean equippable(ItemDefinition idef)
	{
		if (idef.interfaceOptions == null)
		{
			return false;
		}
		for (String option : idef.interfaceOptions)
		{
			if ("Wield".equalsIgnoreCase(option) || "Wear".equalsIgnoreCase(option))
			{
				return true;
			}
		}
		return false;
	}

	private boolean filterItem(ItemDefinition idef)
	{
		return idef.name == null ||
			idef.name.isBlank() ||
			idef.name.equalsIgnoreCase("null");
	}

	private static class WikiFacts
	{
		static final WikiFacts NONE = new WikiFacts(0, Integer.MAX_VALUE, false);

		final long releaseDate;
		final long removalDate;
		final boolean isQuestItem;

		WikiFacts(long releaseDate, long removalDate, boolean isQuestItem)
		{
			this.releaseDate = releaseDate;
			this.removalDate = removalDate;
			this.isQuestItem = isQuestItem;
		}
	}

	private Map<Integer, WikiFacts> wikiFacts(List<WikiItem> wikiItems)
	{
		Map<Integer, WikiFacts> facts = new HashMap<>();
		for (WikiItem item : wikiItems)
		{
			if (item.item_id == null)
			{
				continue;
			}
			String name = item.item_name != null ? item.item_name : "";
			if (name.isEmpty() || name.startsWith("null "))
			{
				continue;
			}
			for (String idStr : item.item_id)
			{
				if (!idStr.matches("\\d+"))
				{
					continue;
				}
				facts.put(Integer.parseInt(idStr), new WikiFacts(
					toUnix(item.release_date, 0),
					toUnix(item.removal_date, Integer.MAX_VALUE),
					!("no".equalsIgnoreCase(item.quest) || "none".equalsIgnoreCase(item.quest))));
			}
		}
		return facts;
	}

	private static class WikiItemsFile
	{
		String fetched_utc;
		List<WikiItem> items;
	}

	private static class WikiItem
	{
		List<String> item_id;
		String item_name;
		String release_date;
		String removal_date;
		String quest;
	}

	private WikiItemsFile readWikiItems(File wikiItemsJson) throws IOException
	{
		try (Reader reader = new InputStreamReader(new FileInputStream(wikiItemsJson), StandardCharsets.UTF_8))
		{
			return new Gson().fromJson(reader, WikiItemsFile.class);
		}
	}

	private static long toUnix(String dateStr, long defaultVal)
	{
		if (dateStr == null || dateStr.isEmpty() || dateStr.equals("0"))
		{
			return defaultVal;
		}
		try
		{
			LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
			return date.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
		}
		catch (Exception e)
		{
			return defaultVal;
		}
	}
}
