package io.huze.glamourer.data;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.SpotAnimDefinition;
import net.runelite.cache.definitions.loaders.SpotAnimLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Store;

/// Joins the hand-managed list of which spotanims an item plays on its wearer with each spotanim's cache
/// definition, so the plugin can rebuild and recolor those graphics without a spotanim API.
@Slf4j
@RequiredArgsConstructor
public class GenerateSpotAnimSheet
{
	private static final String HEADER = "item_id,spotanim_id,model_id,anim_id,ambient,contrast,resize_x,resize_y,rotation,"
		+ "recolor_find,recolor_replace,retexture_find,retexture_replace";

	private final Store store;
	private final Csv csv;

	public void export(File source, File out) throws IOException
	{
		var items = readSource(source).items;
		var definitions = loadSpotAnims();
		int written = 0;
		try (var writer = csv.open(out, "source: " + source.getName()))
		{
			writer.println(HEADER);
			for (Item item : items)
			{
				if (item.itemId <= 0 || item.spotAnimIds == null || item.spotAnimIds.length == 0)
				{
					throw new IllegalStateException(source.getName() + " entry \"" + item.name + "\" needs an itemId and spotAnimIds");
				}
				for (int spotAnimId : item.spotAnimIds)
				{
					SpotAnimDefinition def = definitions.get(spotAnimId);
					if (def == null)
					{
						throw new IllegalStateException(source.getName() + " names spotanim " + spotAnimId + ", which the cache does not have");
					}
					writer.println(item.itemId
						+ "," + def.id
						+ "," + def.modelId
						+ "," + def.animationId
						+ "," + def.ambient
						+ "," + def.contrast
						+ "," + def.resizeX
						+ "," + def.resizeY
						+ "," + def.rotaton
						+ "," + join(def.recolorToFind)
						+ "," + join(def.recolorToReplace)
						+ "," + join(def.textureToFind)
						+ "," + join(def.textureToReplace));
					written++;
				}
			}
		}
		log.info("Wrote {} item spotanims to {}", written, out.getAbsolutePath());
	}

	private static final class SourceFile
	{
		List<Item> items;
	}

	private static final class Item
	{
		String name;
		int itemId;
		int[] spotAnimIds;
	}

	private static SourceFile readSource(File source) throws IOException
	{
		SourceFile file;
		try (var reader = new BufferedReader(new InputStreamReader(new FileInputStream(source), StandardCharsets.UTF_8)))
		{
			file = new Gson().fromJson(reader, SourceFile.class);
		}
		if (file == null || file.items == null)
		{
			throw new IllegalStateException(source.getName() + " has no items list");
		}
		return file;
	}

	private Map<Integer, SpotAnimDefinition> loadSpotAnims() throws IOException
	{
		var archive = store.getIndex(IndexType.CONFIGS).getArchive(ConfigType.SPOTANIM.getId());
		var files = archive.getFiles(store.getStorage().loadArchive(archive));
		var definitions = new HashMap<Integer, SpotAnimDefinition>();
		for (var f : files.getFiles())
		{
			definitions.put(f.getFileId(), new SpotAnimLoader().load(f.getFileId(), f.getContents()));
		}
		return definitions;
	}

	private static String join(short[] values)
	{
		if (values == null)
		{
			return "";
		}
		var sb = new StringBuilder();
		for (int i = 0; i < values.length; i++)
		{
			if (i > 0)
			{
				sb.append('|');
			}
			sb.append(values[i]);
		}
		return sb.toString();
	}
}
