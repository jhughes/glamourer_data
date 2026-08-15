package io.huze.glamourer.data;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.runelite.cache.ItemManager;
import net.runelite.cache.NpcManager;
import net.runelite.cache.definitions.providers.ModelProvider;
import net.runelite.cache.fs.Store;
import net.runelite.cache.fs.flat.FlatStorage;

public class GenerateSheets
{
	public static void main(String[] args) throws IOException
	{
		Map<String, String> options = parse(args);
		File cacheDir = new File(required(options, "--cache"));
		File wikiItems = new File(required(options, "--wiki-items"));
		File wikiPets = new File(required(options, "--wiki-pets"));
		File outDir = new File(required(options, "--out"));
		Csv csv = new Csv(required(options, "--cache-version"));
		outDir.mkdirs();

		try (Store store = openStore(cacheDir))
		{
			store.load();

			ItemManager items = new ItemManager(store);
			items.load();
			NpcManager npcs = new NpcManager(store);
			npcs.load();
			ModelProvider models = new StoreModelProvider(store);

			GenerateItemSheet itemSheet = new GenerateItemSheet(items.getItems(), models, csv);
			itemSheet.export(new File(outDir, "item_sheet.csv"), wikiItems);
			itemSheet.exportStackVariants(new File(outDir, "stack_variant_sheet.csv"));

			new GeneratePetSheet(items.getItems(), npcs.getNpcs(), models, csv)
				.export(new File(outDir, "pet_sheet.csv"), wikiPets);

			new GenerateNpcSheet(npcs.getNpcs(), csv)
				.export(new File(outDir, "npc_sheet.csv"));
		}
	}

	private static Store openStore(File cacheDir) throws IOException
	{
		File[] flat = cacheDir.listFiles((dir, name) -> name.endsWith(".flatcache"));
		if (flat != null && flat.length > 0)
		{
			return new Store(new FlatStorage(cacheDir));
		}
		return new Store(cacheDir);
	}

	private static Map<String, String> parse(String[] args)
	{
		Map<String, String> options = new HashMap<>();
		for (int i = 0; i + 1 < args.length; i += 2)
		{
			options.put(args[i], args[i + 1]);
		}
		return options;
	}

	private static String required(Map<String, String> options, String name)
	{
		String value = options.get(name);
		if (value == null)
		{
			System.err.println("Usage: GenerateSheets --cache <cache dir> --cache-version <description> --wiki-items <osrs_wiki_items.json> --wiki-pets <osrs_wiki_pets.json> --out <output dir>");
			System.exit(1);
		}
		return value;
	}
}
