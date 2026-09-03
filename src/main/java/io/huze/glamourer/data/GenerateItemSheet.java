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
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.providers.ModelProvider;

@Slf4j
public class GenerateItemSheet
{
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
	private static final Pattern TRAILING_PARENS = Pattern.compile("(\\s*\\([^)]*\\))+$");
	private static final Pattern TRAILING_INTEGER_PAREN = Pattern.compile("\\s*\\(\\d+\\)$");
	private static final Pattern TRAILING_NUMBER = Pattern.compile("\\s\\d+$");
	private static final Map<Integer, Integer> LOOKS_LIKE = Map.ofEntries(
		// Regular Ahrim's robetop (contains an unused color)
		Map.entry(4712, 4868),
		// Broken Echo Ahrim's staff
		Map.entry(30574, 30570),
		// Broken Ahrim's set
		Map.entry(4860, 4856), Map.entry(4866, 4862), Map.entry(4872, 4868), Map.entry(4878, 4874),
		// Broken Dharok's set
		Map.entry(4884, 4880), Map.entry(4890, 4886), Map.entry(4896, 4892), Map.entry(4902, 4898),
		// Broken Guthan's set
		Map.entry(4908, 4904), Map.entry(4914, 4910), Map.entry(4920, 4916), Map.entry(4926, 4922),
		// Broken Karil's set
		Map.entry(4932, 4928), Map.entry(4938, 4934), Map.entry(4944, 4940), Map.entry(4950, 4946),
		// Broken Torag's set
		Map.entry(4956, 4952), Map.entry(4962, 4958), Map.entry(4968, 4964), Map.entry(4974, 4970),
		// Broken Verac's set
		Map.entry(4980, 4976), Map.entry(4986, 4982), Map.entry(4992, 4988), Map.entry(4998, 4994)
	);

	private static final Comparator<ItemDefinition> PRIMARY_FIRST =
		Comparator.<ItemDefinition>comparingInt(idef -> idef.name.length())
			.thenComparing(idef -> idef.name, Comparator.reverseOrder())
			.thenComparingInt(idef -> idef.id);

	private final Collection<ItemDefinition> itemDefs;
	private final ModelProvider modelProvider;
	private final Csv csv;
	private final Map<Integer, ModelFaces> faceCache = new HashMap<>();
	private final Map<Integer, ItemDefinition> itemsById = new HashMap<>();
	private final String wikiProvenance;
	private final Map<Integer, WikiFacts> wikiFacts;

	public GenerateItemSheet(Collection<ItemDefinition> itemDefs, ModelProvider modelProvider, Csv csv, File wikiItemsJson) throws IOException
	{
		this.itemDefs = itemDefs;
		for (ItemDefinition idef : itemDefs)
		{
			itemsById.put(idef.id, idef);
		}
		this.modelProvider = modelProvider;
		this.csv = csv;
		WikiItemsFile wiki = readWikiItems(wikiItemsJson);
		this.wikiProvenance = "wiki items fetched: " + wiki.fetched_utc;
		this.wikiFacts = wikiFacts(wiki.items);
	}

	public void exportItems(File out) throws IOException
	{
		List<ItemRow> rows = rows();
		rows.removeIf(row -> !row.isWorthSerializing());

		try (PrintWriter writer = csv.open(out, wikiProvenance))
		{
			writer.println(ItemRow.CSV_HEADER);
			for (ItemRow row : rows)
			{
				writer.println(row.toCsvString());
			}
		}
		log.info("Wrote to " + out.getAbsolutePath());
	}

	/// Groups of items the plugin should treat as one visual item but cannot derive from names + appearance alone:
	/// - Numbered variants (Barrows Gear, fungicide spray) - Serum 207 / 208 makes general name matching fail
	/// - Potion doses - Different models
	/// - Fruit baskets - Different models
	/// - Watering Can - Different models
	public void exportDedupeGroups(File out) throws IOException
	{
		var pages = new HashMap<String, List<ItemDefinition>>();
		for (var idef : itemDefs)
		{
			var page = wikiFacts.getOrDefault(idef.id, WikiFacts.NONE).pageName;
			if (page != null && !filterItem(idef))
			{
				pages.computeIfAbsent(page, k -> new ArrayList<>()).add(idef);
			}
		}

		var groups = new ArrayList<List<ItemDefinition>>();
		var grouped = new HashSet<Integer>();
		for (var page : pages.values())
		{
			var sets = numberedVariantSets(page);
			sets.addAll(doseSets(page));
			for (var set : sets)
			{
				set.sort(PRIMARY_FIRST);
				for (ItemDefinition idef : set)
				{
					if (!grouped.add(idef.id))
					{
						throw new IllegalStateException(idef.id + " " + idef.name + " is in two dedupe groups");
					}
				}
			}
			groups.addAll(sets);
		}
		groups.sort(Comparator.comparingInt(group -> group.get(0).id));

		try (var writer = csv.open(out, wikiProvenance))
		{
			writer.println("primary_id,deduped_ids");
			for (var group : groups)
			{
				ItemDefinition primary = group.get(0);
				writer.println("# " + primary.name);
				writer.println(primary.id + "," + group.subList(1, group.size()).stream()
					.map(idef -> String.valueOf(idef.id))
					.collect(Collectors.joining("|")));
			}
		}
		log.info("Wrote to {} ({} groups)", out.getAbsolutePath(), groups.size());
	}

	/// Same appearance, different stripped names, at least one ending in a number.
	private List<List<ItemDefinition>> numberedVariantSets(List<ItemDefinition> page)
	{
		var byAppearance = new HashMap<String, List<ItemDefinition>>();
		for (var idef : page)
		{
			var look = lookOf(idef);
			byAppearance.computeIfAbsent(look.inventoryModel + ":" + colours(look), k -> new ArrayList<>()).add(idef);
		}
		var sets = new ArrayList<List<ItemDefinition>>();
		for (var set : byAppearance.values())
		{
			var strippedNames = new HashSet<String>();
			var hasTrailingNumber = false;
			for (var idef : set)
			{
				var stripped = extractTrailingParens(idef.name);
				strippedNames.add(stripped);
				hasTrailingNumber |= TRAILING_NUMBER.matcher(stripped).find();
			}
			if (strippedNames.size() > 1 && hasTrailingNumber)
			{
				sets.add(set);
			}
		}
		return sets;
	}

	/// Names differing only by a trailing parenthesised integer, same colours, differing models.
	private List<List<ItemDefinition>> doseSets(List<ItemDefinition> page)
	{
		var byDoselessName = new HashMap<String, List<ItemDefinition>>();
		for (ItemDefinition idef : page)
		{
			var dose = TRAILING_INTEGER_PAREN.matcher(idef.name.trim());
			if (dose.find())
			{
				String doseless = dose.replaceAll("");
				byDoselessName.computeIfAbsent(doseless + ":" + colours(lookOf(idef)), k -> new ArrayList<>()).add(idef);
			}
		}
		var sets = new ArrayList<List<ItemDefinition>>();
		for (var set : byDoselessName.values())
		{
			if (set.stream().anyMatch(idef -> idef.inventoryModel != set.get(0).inventoryModel))
			{
				sets.add(set);
			}
		}
		return sets;
	}

	private ItemDefinition lookOf(ItemDefinition idef)
	{
		var lookalike = LOOKS_LIKE.get(idef.id);
		return lookalike != null ? itemsById.get(lookalike) : idef;
	}

	private static String colours(ItemDefinition idef)
	{
		return Arrays.toString(idef.colorFind) + Arrays.toString(idef.colorReplace)
			+ Arrays.toString(idef.textureFind) + Arrays.toString(idef.textureReplace);
	}

	private static String extractTrailingParens(String name)
	{
		return TRAILING_PARENS.matcher(name.trim()).replaceAll("").trim();
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

	private List<ItemRow> rows()
	{
		List<ItemRow> rows = new ArrayList<>();
		for (ItemDefinition idef : itemDefs)
		{
			if (filterItem(idef))
			{
				continue;
			}
			WikiFacts facts = wikiFacts.getOrDefault(idef.id, WikiFacts.NONE);

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
		static final WikiFacts NONE = new WikiFacts(0, Integer.MAX_VALUE, false, null);

		final long releaseDate;
		final long removalDate;
		final boolean isQuestItem;
		/// The wiki page the item is listed on, which groups its versions (doses, charges, degrade states).
		final String pageName;

		WikiFacts(long releaseDate, long removalDate, boolean isQuestItem, String pageName)
		{
			this.releaseDate = releaseDate;
			this.removalDate = removalDate;
			this.isQuestItem = isQuestItem;
			this.pageName = pageName;
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
					!("no".equalsIgnoreCase(item.quest) || "none".equalsIgnoreCase(item.quest)),
					item.page_name != null && !item.page_name.isBlank() ? item.page_name : null));
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
		String page_name;
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
