#!/usr/bin/env python3
"""Fetch the OSRS wiki's pets, with the item and NPC ids behind each one.

Reads the pet tables on /w/Pet, then each pet page's {{Infobox NPC}} and {{Infobox Item}}, whose
versionN/idN parameters are index-aligned. Writes osrs_wiki_pets.json for the pet sheet generator.
"""
import json
import re
import time
from datetime import datetime, timezone

from wiki_common import get

API_URL = "https://oldschool.runescape.wiki/api.php"

SECTION_START = "==List of pets=="
SECTION_END = "==Pet-free locations=="


def wikitext(page):
    params = {"action": "parse", "page": page, "prop": "wikitext", "format": "json", "redirects": 1}
    data = get(API_URL, params).json()
    if "error" in data:
        print(f"  {page}: {data['error'].get('info', data['error'])}")
        return None
    return data["parse"]["wikitext"]["*"]


def fetch_pet_pages():
    text = wikitext("Pet")

    # Confine the scan to the pet tables so navboxes can't contribute names; if the headings
    # move, fall back to the whole page loudly.
    try:
        section = text[text.index(SECTION_START):text.index(SECTION_END)]
    except ValueError:
        print(f"WARNING: could not find {SECTION_START}..{SECTION_END}, scanning whole page")
        section = text

    return sorted({m.group(1).split("#")[0].strip() for m in re.finditer(r"\{\{plinkt\|([^\}\|]+)", section)})


def infobox(text, kind):
    """The body of a {{Infobox <kind>}}, found by matching braces -- nested templates defeat regex."""
    start = text.find("{{Infobox " + kind)
    if start < 0:
        return None

    depth = 0
    i = start
    while i < len(text) - 1:
        if text[i:i + 2] == "{{":
            depth += 1
            i += 2
        elif text[i:i + 2] == "}}":
            depth -= 1
            i += 2
            if depth == 0:
                return text[start:i]
        else:
            i += 1
    return text[start:]


def indexed(body, field):
    """Values of an indexed infobox parameter as {index: value}; un-numbered ones file under 1."""
    found = {}
    for m in re.finditer(rf"(?m)^\|\s*{field}(\d*)\s*=\s*(.*?)\s*$", body):
        index = int(m.group(1)) if m.group(1) else 1
        value = m.group(2).strip()
        if value:
            found[index] = value
    return found


def ids(value):
    return [int(n) for n in re.findall(r"\d+", value)]


def fetch_pet(page):
    text = wikitext(page)
    if text is None:
        return None

    npc_box = infobox(text, "NPC")
    item_box = infobox(text, "Item")
    if not npc_box or not item_box:
        print(f"  {page}: missing an NPC or Item infobox")
        return None

    npc_ids = indexed(npc_box, "id")
    item_ids = indexed(item_box, "id")
    npc_names = indexed(npc_box, "version")
    item_names = indexed(item_box, "version")

    by_name = {}
    for index, name in npc_names.items():
        if index in npc_ids:
            by_name[name.strip().lower()] = npc_ids[index]
    shared = sum(1 for i in item_ids if item_names.get(i, "").strip().lower() in by_name)

    versions = []
    if shared:
        # Names agree, so use them; position can't be trusted when one box lists extra versions
        # (Archibald), which shifts every later index.
        for index in sorted(item_ids):
            name = item_names.get(index)
            matched = by_name.get(name.strip().lower()) if name else None
            if matched is None:
                if name:
                    print(f"  {page}: item version '{name}' has no npc of that version")
                continue
            versions.append({
                "name": name,
                "item_ids": ids(item_ids[index]),
                "npc_ids": ids(matched),
            })
    else:
        # No names in common (single unnamed form, or the boxes name versions differently, like
        # Rocky); trust position only when both boxes list the same number of versions.
        if len(item_ids) != len(npc_ids):
            print(f"  {page}: {len(item_ids)} item versions vs {len(npc_ids)} npc versions, none named alike")
            return None
        for index in sorted(set(npc_ids) & set(item_ids)):
            versions.append({
                "name": item_names.get(index, npc_names.get(index, "")),
                "item_ids": ids(item_ids[index]),
                "npc_ids": ids(npc_ids[index]),
            })

    if not versions:
        print(f"  {page}: no item/npc versions could be aligned")
        return None
    return {"page": page, "versions": versions}


pets = []
pages = fetch_pet_pages()
print(f"Found {len(pages)} pets on /w/Pet, reading pages...")

for page in pages:
    pet = fetch_pet(page)
    if pet:
        pets.append(pet)
    time.sleep(0.1)

with open("osrs_wiki_pets.json", "w") as f:
    json.dump({
        "fetched_utc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "pets": pets,
    }, f, indent=4)

versions = sum(len(p["versions"]) for p in pets)
items = sum(len(v["item_ids"]) for p in pets for v in p["versions"])
print(f"Done! Saved {len(pets)} pets, {versions} versions, {items} item ids to osrs_wiki_pets.json")
