package io.huze.glamourer.data;

import lombok.Value;

@Value
public class ItemRow
{
	public static final String CSV_HEADER = "id,release_date,removal_date,quest,category,male_model0,male_model1,male_model2,female_model0,female_model1,female_model2";

	int id;
	long releaseDate;
	long removalDate;
	boolean isQuestItem;
	int category;
	int maleModel0;
	int maleModel1;
	int maleModel2;
	int femaleModel0;
	int femaleModel1;
	int femaleModel2;

	public boolean isWorthSerializing()
	{
		return category != 0 || releaseDate != 0 || removalDate != Integer.MAX_VALUE || isQuestItem
			|| maleModel0 != -1 || maleModel1 != -1 || maleModel2 != -1
			|| femaleModel0 != -1 || femaleModel1 != -1 || femaleModel2 != -1;
	}

	public String toCsvString()
	{
		return String.format("%d,%d,%d,%s,%d,%d,%d,%d,%d,%d,%d",
			id,
			releaseDate,
			removalDate,
			isQuestItem,
			category,
			maleModel0,
			maleModel1,
			maleModel2,
			femaleModel0,
			femaleModel1,
			femaleModel2);
	}
}
