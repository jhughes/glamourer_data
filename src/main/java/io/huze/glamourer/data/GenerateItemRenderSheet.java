package io.huze.glamourer.data;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.definitions.ItemDefinition;

@Slf4j
@RequiredArgsConstructor
public class GenerateItemRenderSheet
{
	private static final String HEADER =
		"id,zoom2d,xOffset2d,yOffset2d,resizeX,resizeY,resizeZ,boughtTemplateId,boughtId,variants";

	private static final int DEFAULT_ZOOM_2D = 2000;
	private static final int DEFAULT_RESIZE = 128;

	private final Collection<ItemDefinition> itemDefs;
	private final Csv csv;

	public void export(File out) throws IOException
	{
		var items = new ArrayList<>(itemDefs);
		items.sort(Comparator.comparingInt(i -> i.id));

		int written = 0;
		try (var writer = csv.open(out))
		{
			writer.println(HEADER);
			for (var item : items)
			{
				var variants = getVariants(item);
				if (isDefault(item, variants))
				{
					continue;
				}
				writer.println(item.id
					+ "," + emptyIf(item.zoom2d, DEFAULT_ZOOM_2D)
					+ "," + emptyIf(item.xOffset2d, 0)
					+ "," + emptyIf(item.yOffset2d, 0)
					+ "," + emptyIf(item.resizeX, DEFAULT_RESIZE)
					+ "," + emptyIf(item.resizeY, DEFAULT_RESIZE)
					+ "," + emptyIf(item.resizeZ, DEFAULT_RESIZE)
					+ "," + emptyIf(item.boughtTemplateId, -1)
					+ "," + emptyIf(item.boughtId, -1)
					+ "," + variants);
				written++;
			}
		}
		log.info("Wrote render params for {} items to {}", written, out.getAbsolutePath());
	}

	private static boolean isDefault(ItemDefinition item, String variants)
	{
		return item.zoom2d == DEFAULT_ZOOM_2D
			&& item.xOffset2d == 0
			&& item.yOffset2d == 0
			&& item.resizeX == DEFAULT_RESIZE
			&& item.resizeY == DEFAULT_RESIZE
			&& item.resizeZ == DEFAULT_RESIZE
			&& item.boughtTemplateId == -1
			&& variants.isEmpty();
	}

	private static String getVariants(ItemDefinition item)
	{
		if (item.countObj == null)
		{
			return "";
		}
		var sb = new StringBuilder();
		for (int i = 0; i < item.countObj.length; i++)
		{
			if (item.countObj[i] == 0 && item.countCo[i] == 0)
			{
				continue;
			}
			if (sb.length() > 0)
			{
				sb.append('|');
			}
			sb.append(item.countCo[i]).append(':').append(item.countObj[i]);
		}
		return sb.toString();
	}

	private static String emptyIf(int value, int defaultValue)
	{
		return value == defaultValue ? "" : Integer.toString(value);
	}
}
