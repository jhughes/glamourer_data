package io.huze.glamourer.data;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class Csv
{
	/// The source cache, e.g. "07816e6 Cache version 2026-08-12-rev240".
	private final String cacheVersion;

	PrintWriter open(File out, String... provenance) throws IOException
	{
		PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
			new FileOutputStream(out), StandardCharsets.UTF_8)));
		writer.println("# This file is generated; do not edit manually.");
		writer.println("# cache: " + cacheVersion);
		for (String line : provenance)
		{
			writer.println("# " + line);
		}
		return writer;
	}
}
