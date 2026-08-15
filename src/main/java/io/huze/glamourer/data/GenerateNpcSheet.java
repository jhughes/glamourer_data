package io.huze.glamourer.data;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.definitions.NpcDefinition;

@Slf4j
@RequiredArgsConstructor
public class GenerateNpcSheet
{
	private final Collection<NpcDefinition> npcDefs;
	private final Csv csv;

	public void export(File out) throws IOException
	{
		List<NpcDefinition> npcs = new ArrayList<>();
		for (NpcDefinition def : npcDefs)
		{
			if (def.ambient != 0 || def.contrast != 0 || def.renderPriority != 0)
			{
				npcs.add(def);
			}
		}
		npcs.sort(Comparator.comparingInt(n -> n.id));

		try (PrintWriter writer = csv.open(out))
		{
			// The client scales contrast by 5.
			writer.println("id,ambient,contrast,render_priority");
			for (NpcDefinition n : npcs)
			{
				writer.println(n.id + "," + n.ambient + "," + (n.contrast * 5) + "," + n.renderPriority);
			}
		}
		log.info("Wrote {} npcs to {}", npcs.size(), out.getAbsolutePath());
	}
}
