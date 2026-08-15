package io.huze.glamourer.data;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.providers.ModelProvider;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;

@RequiredArgsConstructor
final class StoreModelProvider implements ModelProvider
{
	private final Store store;

	@Override
	public ModelDefinition provide(int modelId) throws IOException
	{
		Index models = store.getIndex(IndexType.MODELS);
		Archive archive = models.getArchive(modelId);
		if (archive == null)
		{
			return null;
		}
		byte[] data = archive.decompress(store.getStorage().loadArchive(archive));
		return new ModelLoader().load(modelId, data);
	}
}
