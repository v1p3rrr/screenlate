"""Builds a Yomitan frequency dictionary for one language from wordfreq's word list.

    pip install wordfreq
    python scripts/frequency/wordfreq_dictionary.py en wordfreq-en.zip [--size 100000]

Ranks follow wordfreq's 'best' list: 1 is the most frequent word. Tokens without a letter are left out. wordfreq
lowercases everything, while dictionaries write names and German nouns capitalized, so each word also gets its
capitalized form with the same rank (--no-capitalized leaves them out). The index credits wordfreq and its sources, as
the CC BY-SA 4.0 license of its data asks; the dictionary is shared under the same license.
"""
import argparse
import json
import sys
import unicodedata
import zipfile

BANK_SIZE = 10000

ATTRIBUTION = (
    "Word frequencies from wordfreq {version} by Robyn Speer (https://github.com/rspeer/wordfreq), data licensed "
    "CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/). wordfreq's data comes from Wikipedia "
    "(https://www.wikipedia.org); OPUS OpenSubtitles 2018 (http://opus.nlpl.eu/OpenSubtitles.php), which originates "
    "from the OpenSubtitles project (http://www.opensubtitles.org/); the SUBTLEX word lists by Marc Brysbaert et al., "
    "freely available data (http://crr.ugent.be/programs-data/subtitle-frequencies); Google Books Ngrams "
    "(http://books.google.com/ngrams); the Leeds Internet Corpus (http://corpus.leeds.ac.uk/list.html); ParaCrawl "
    "(https://paracrawl.eu); NewsCrawl, GlobalVoices, OSCAR, Twitter and Reddit; for Chinese, the word list of the "
    "Jieba segmenter. This dictionary is shared under CC BY-SA 4.0."
)


def has_letter(token):
    return any(unicodedata.category(c).startswith("L") for c in token)


def capitalized(word):
    """The word with its first letter in upper case, or None when that changes nothing."""
    upper = word[:1].upper() + word[1:]
    return upper if upper != word else None


def term_meta(words, size, with_capitalized=True):
    """Yomitan `term_meta_bank` rows for the first [size] words with a letter, ranked from 1."""
    rows = []
    seen = set()
    rank = 0
    for word in words:
        if rank >= size:
            break
        if not has_letter(word) or word in seen:
            continue
        rank += 1
        seen.add(word)
        rows.append([word, "freq", rank])
        upper = capitalized(word) if with_capitalized else None
        if upper is not None and upper not in seen:
            seen.add(upper)
            rows.append([upper, "freq", rank])
    return rows


def index(language, version, size, download_url=None, index_url=None):
    result = {
        "title": f"wordfreq {language}",
        "format": 3,
        "revision": f"wordfreq-{version}-{size}",
        "sequenced": False,
        "frequencyMode": "rank-based",
        "author": "Robyn Speer (wordfreq)",
        "url": "https://github.com/rspeer/wordfreq",
        "description": f"Frequency ranks of the {size} most frequent words of wordfreq {version} for the language "
                       f"{language}: 1 is the most frequent.",
        "attribution": ATTRIBUTION.format(version=version),
        "sourceLanguage": language,
    }
    if download_url and index_url:
        result.update({"isUpdatable": True, "downloadUrl": download_url, "indexUrl": index_url})
    return result


def write(path, index_json, rows):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("index.json", json.dumps(index_json, ensure_ascii=False, indent=2))
        for number, start in enumerate(range(0, len(rows), BANK_SIZE), start=1):
            bank = rows[start:start + BANK_SIZE]
            archive.writestr(f"term_meta_bank_{number}.json", json.dumps(bank, ensure_ascii=False, separators=(",", ":")))


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("language", help="wordfreq language code, e.g. en")
    parser.add_argument("output", help="the zip archive to write")
    parser.add_argument("--size", type=int, default=100000, help="words to keep (default 100000)")
    parser.add_argument("--no-capitalized", action="store_true", help="leave out the capitalized forms")
    parser.add_argument("--download-url", help="where the archive will be hosted, for update checks")
    parser.add_argument("--index-url", help="where its index.json will be hosted, for update checks")
    args = parser.parse_args()

    import wordfreq
    from importlib.metadata import version as package_version

    if args.language not in wordfreq.available_languages("best"):
        sys.exit(f"wordfreq has no word list for {args.language}")
    version = package_version("wordfreq")
    # Tokens without letters are dropped, so ask for more than needed.
    words = wordfreq.top_n_list(args.language, args.size * 2, wordlist="best")
    rows = term_meta(words, args.size, with_capitalized=not args.no_capitalized)
    ranked = max((row[2] for row in rows), default=0)
    write(args.output, index(args.language, version, ranked, args.download_url, args.index_url), rows)
    print(f"{args.output}: {ranked} words, {len(rows)} rows")


if __name__ == "__main__":
    main()
