#!/usr/bin/env python3
import json
import time
from datetime import datetime, timezone

from wiki_common import get

def fetch_all_wiki_items():
    base_url = "https://oldschool.runescape.wiki/api.php"
    all_results = []
    limit = 500  # The API's maximum
    offset = 0

    print("Starting full wiki export...")

    while True:
        query = (
            f"bucket('infobox_item')"
            f".orderBy('item_name')"
            f".select('page_name','item_name','item_id','release_date','removal_date','quest')"
            f".limit({limit})"
            f".offset({offset})"
            f".run()"
        )

        params = {
            "action": "bucket",
            "query": query,
            "format": "json"
        }

        data = get(base_url, params).json()
        results = data.get('bucket', [])
        if not results:
            break

        all_results.extend(results)
        print(f"Fetched {len(all_results)} items...")

        offset += limit
        time.sleep(0.1)

    return all_results

items = fetch_all_wiki_items()
with open('osrs_wiki_items.json', 'w') as f:
    json.dump({
        "fetched_utc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "items": items,
    }, f, indent=4)

print(f"Done! Saved {len(items)} total items to osrs_wiki_items.json")
