"""Shared wiki API plumbing: identifying User-Agent, retry with backoff."""
import time

import requests

# The wiki API guidelines ask for a User-Agent that identifies the caller.
HEADERS = {"User-Agent": "glamourer-data sheet generator (https://github.com/jhughes/glamourer_data)"}

RETRIES = 5


def get(url, params):
    """GET with exponential backoff; raises after the last attempt so runs fail loudly
    rather than write partial data."""
    for attempt in range(RETRIES):
        try:
            response = requests.get(url, params=params, headers=HEADERS, timeout=30)
            response.raise_for_status()
            return response
        except Exception as e:
            if attempt == RETRIES - 1:
                raise
            delay = 2 ** attempt
            print(f"  request failed ({e}); retrying in {delay}s")
            time.sleep(delay)
