"""Runs Commons file searches given on the command line, e.g. intitle:"péngyou" filetype:audio."""
import json, sys, time, urllib.error, urllib.parse, urllib.request
sys.stdout.reconfigure(encoding="utf-8", line_buffering=True)
API = "https://commons.wikimedia.org/w/api.php"
UA = "Screenlate-research/0.1 (language audio check)"
def search(q, limit=15):
    url = API + "?" + urllib.parse.urlencode({"action": "query", "format": "json", "list": "search", "srsearch": q, "srnamespace": "6", "srlimit": str(limit)})
    for attempt in range(5):
        time.sleep(2)
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=30) as r:
                return [s["title"] for s in json.load(r).get("query", {}).get("search", [])]
        except urllib.error.HTTPError as e:
            if e.code != 429: raise
            time.sleep(10 * (attempt + 1))
for q in sys.argv[1:]:
    print(q, "->", search(q))
