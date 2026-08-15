package io.huze.glamourer.data;

import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PetRow
{
	final int itemId;
	final List<Integer> npcIds;
	final List<Integer> npcModels;
	final String comment;

	public static final String CSV_HEADER = "item_id,npc_ids,npc_models";

	public String toCsvString()
	{
		return String.format("# %s%n%d,%s,%s", comment, itemId, join(npcIds), join(npcModels));
	}

	private static String join(List<Integer> values)
	{
		return values.stream().map(String::valueOf).collect(Collectors.joining("|"));
	}
}
