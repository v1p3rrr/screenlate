"""Writes the catalog entries of the frequency dictionaries that the app builds from a corpus' word counts.

    python scripts/frequency/corpus_catalog.py [--cache testdata/corpus]

For languages wordfreq has no list for. Each entry points at one count list at a pinned commit, or at a Leipzig
Corpora Collection archive (a fixed file), with its size and SHA-256; the app downloads it, checks it and builds the
dictionary as CorpusCountsDictionary does. A list is `word<TAB>count` lines, the most frequent first; an archive holds
`<corpus>-words.txt` with `id<TAB>word<TAB>count` lines somewhere in it; this script checks both. Entries of the
corpora's templates in dictionaries-v2.json are replaced and placed after the wordfreq entries; the template texts are
kept as they are.
"""
import argparse
import hashlib
import io
import json
import os
import tarfile
import urllib.request

PYTHAINLP_COMMIT = "10c1e82db675344140ef514821bf3a40f9601c2b"

# Per language: the template of its texts, the title CorpusCountsDictionary gives the built dictionary, the list's
# address and file name, its version (the date of the commit that last changed the list), license and homepage.
CORPORA = {
    "th": {
        "template": "tnc",
        "title": "Thai National Corpus (th)",
        "url": f"https://raw.githubusercontent.com/PyThaiNLP/pythainlp/{PYTHAINLP_COMMIT}/pythainlp/corpus/tnc_freq.txt",
        "file": "tnc_freq.txt",
        "version": "2020-08-18",
        "license": "CC0-1.0",
        "homepage": "https://github.com/PyThaiNLP/pythainlp",
    },
}

LEIPZIG = "https://downloads.wortschatz-leipzig.de/corpora"


def leipzig(corpus, language, template="leipzig", version="2016"):
    """The entry of the Leipzig archive [corpus], whose texts [template] names: "leipzig" news, "leipzig-wikipedia"
    Wikipedia articles."""
    return {
        "template": template,
        "title": f"Leipzig Corpora Collection ({language})",
        "url": f"{LEIPZIG}/{corpus}.tar.gz",
        "file": f"{corpus}.tar.gz",
        "format": "leipzig",
        "version": version,
        "license": "CC BY",
        "homepage": "https://wortschatz.uni-leipzig.de/en/download",
    }


CORPORA["kk"] = leipzig("kaz_newscrawl_2016_100K", "kk")
CORPORA["ky"] = leipzig("kir_newscrawl_2016_100K", "ky")
CORPORA["la"] = leipzig("lat_wikipedia_2021_100K", "la", template="leipzig-wikipedia", version="2021")
CORPORA["eo"] = leipzig("epo_wikipedia_2021_100K", "eo", template="leipzig-wikipedia", version="2021")
CORPORA["sq"] = leipzig("sqi_news_2020_100K", "sq", version="2020")
CORPORA["sw"] = leipzig("swa_news_2020_30K", "sw", version="2020")
CORPORA["et"] = leipzig("est_news_2020_100K", "et", version="2020")
CORPORA["az"] = leipzig("aze_news_2020_30K", "az", version="2020")

BUILD = "corpus-counts"
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
CATALOG = os.path.join(ROOT, "dictionary", "api", "src", "main", "assets", "catalog", "dictionaries-v2.json")


def fetch(corpus, cache):
    """The count list of [corpus] from the cache, downloaded into it first when missing."""
    path = os.path.join(cache, corpus["file"])
    if not os.path.exists(path):
        with urllib.request.urlopen(corpus["url"]) as response:
            data = response.read()
        os.makedirs(cache, exist_ok=True)
        with open(path, "wb") as file:
            file.write(data)
    with open(path, "rb") as file:
        return file.read()


def check(data, format="counts"):
    """The number of words [data] lists; raises ValueError unless it is a list of the [format]."""
    if format == "leipzig":
        return check_leipzig(data)
    return check_counts(data)


def check_leipzig(data):
    """The number of lines of the archive's word list; each is an id, a word and a count, separated by tabs."""
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as archive:
        member = next((m for m in archive.getmembers() if m.isfile() and m.name.endswith("-words.txt")), None)
        if member is None:
            raise ValueError("no -words.txt in the archive")
        lines = archive.extractfile(member).read().decode("utf-8").splitlines()
    for number, line in enumerate(lines, 1):
        fields = line.split("\t")
        if len(fields) != 3 or not fields[2].isdigit():
            raise ValueError(f"line {number} is not an id, a word and a count")
    return len(lines)


def check_counts(data):
    """The number of lines of [data]; raises ValueError unless every line is a word, a tab and a count, the largest first."""
    previous = None
    lines = data.decode("utf-8").splitlines()
    for number, line in enumerate(lines, 1):
        word, tab, count = line.partition("\t")
        if not tab or not word.strip() or not count.strip().isdigit():
            raise ValueError(f"line {number} is not a word count")
        if previous is not None and int(count) > previous:
            raise ValueError(f"line {number} is not sorted by count")
        previous = int(count)
    return len(lines)


def entry(language, corpus, data):
    return {
        "id": f"corpus-{language}",
        "title": corpus["title"],
        "template": corpus["template"],
        "oldTitles": [corpus["title"]],
        "kind": "FREQUENCY",
        "category": "FREQUENCY",
        "sourceLanguage": language,
        "recommended": True,
        "downloadUrl": corpus["url"],
        "downloadSize": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
        "build": BUILD,
        "version": corpus["version"],
        "license": corpus["license"],
        "homepage": corpus["homepage"],
    }


def replace(document, built):
    """[document] with its corpus entries replaced by [built], placed after the last wordfreq entry."""
    templates = {corpus["template"] for corpus in CORPORA.values()}
    kept = [e for e in document["dictionaries"] if e.get("template") not in templates]
    at = max((i + 1 for i, e in enumerate(kept) if e.get("template") == "wordfreq"), default=len(kept))
    return {**document, "dictionaries": kept[:at] + built + kept[at:]}


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--cache", default=os.path.join(ROOT, "testdata", "corpus"),
                        help="where downloaded count lists are kept")
    args = parser.parse_args()
    with open(CATALOG, encoding="utf-8") as file:
        document = json.load(file)
    built = []
    for language, corpus in CORPORA.items():
        if corpus["template"] not in document.get("templates", {}):
            raise SystemExit(f"The catalog has no template {corpus['template']!r}")
        data = fetch(corpus, args.cache)
        print(f"{language}: {check(data, corpus.get('format', 'counts'))} words")
        built.append(entry(language, corpus, data))
    with open(CATALOG, "w", encoding="utf-8", newline="\n") as file:
        file.write(json.dumps(replace(document, built), ensure_ascii=False, indent=2) + "\n")
    print(f"{len(built)} corpus entries")


if __name__ == "__main__":
    main()
