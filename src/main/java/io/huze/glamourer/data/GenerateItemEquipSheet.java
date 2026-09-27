package io.huze.glamourer.data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.definitions.ItemDefinition;

@Slf4j
public class GenerateItemEquipSheet
{
	private static final String HEADER =
		"item_id,male_model0,male_model1,male_model2,female_model0,female_model1,female_model2";
	private static final int NO_MODEL = -1;

	private final Collection<ItemDefinition> itemDefs;
	private final Csv csv;

	public GenerateItemEquipSheet(Collection<ItemDefinition> itemDefs, Csv csv)
	{
		this.itemDefs = itemDefs;
		this.csv = csv;
	}

	public void export(File out) throws IOException
	{
		var items = new ArrayList<>(itemDefs);
		items.sort(Comparator.comparingInt(item -> item.id));

		int written = 0;
		try (var writer = csv.open(out))
		{
			writer.println(HEADER);
			for (var item : items)
			{
				int[] models = equipModels(item);
				if (!isWorn(models) || !Items.hasName(item))
				{
					continue;
				}
				writer.println(item.id
					+ "," + models[0]
					+ "," + models[1]
					+ "," + models[2]
					+ "," + models[3]
					+ "," + models[4]
					+ "," + models[5]);
				written++;
			}
		}
		log.info("Wrote equipment models for {} items to {}", written, out.getAbsolutePath());
	}

	private static int[] equipModels(ItemDefinition item)
	{
		return new int[]{
			modelId(item.maleModel0), modelId(item.maleModel1), modelId(item.maleModel2),
			modelId(item.femaleModel0), modelId(item.femaleModel1), modelId(item.femaleModel2)};
	}

	private static int modelId(int modelId)
	{
		return modelId <= 0 ? NO_MODEL : modelId;
	}

	private static boolean isWorn(int[] models)
	{
		for (int modelId : models)
		{
			if (modelId != NO_MODEL)
			{
				return true;
			}
		}
		return false;
	}
}
