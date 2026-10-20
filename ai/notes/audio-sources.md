# Audio sources

## Hosts of the default sources (checked 2026-10-01)

- JapanesePod101 word audio: `assets.languagepod101.com/dictionary/japanese/audiomp3.php` (Apache on AWS EC2)
  answers 301 to `cdn.innovativelanguage.com/.../audio/assets/<id>.mp3` (AWS CloudFront). Real clips are small
  (1.5-2 KB, MPEG-2 layer III 64 kbps 24 kHz); unknown words get the 52 KB placeholder, which `JPOD_PLACEHOLDER_SHA256`
  still matches.
- LanguagePod101 dictionary search: a POST to `www.japanesepod101.com/learningcenter/reference/dictionary_post`
  (CloudFront); the answer is ~3.5 KB HTML, clips on `cdn.innovativelanguage.com/.../vocabulary/<n>.mp3`.
- Jisho: `jisho.org/search/<term>` (nginx at OVH, AS16276); the page is ~85 KB, ~13 KB gzipped; clips on
  `d1vjc5dkcd3yh2.cloudfront.net/audio/<hash>.mp3`.
- The parsers in `AudioPages` read the current pages (checked with the same regexes against live answers).

## Mobile networks that freeze foreign hosting

The owner's phone gets timeouts from all three default sources on mobile data only (Wi-Fi works; a VPN made no
difference), while the owner's own Yomitan audio server works on both. From the cloud every source answers in under a
second. The pattern matches mobile networks that freeze a TCP connection to foreign hosting (OVH, AWS CloudFront) after
roughly 16 KB: a small clip passes on a fresh connection, a page as large as Jisho's does not, and a second request on
a kept-open connection hangs (JapanesePod101 played once in the test, then not). The app cannot fix the network; it
opens a new connection for every audio request (`ConnectionPool(0, ...)` in `AudioFinder`), asks all sources at once
with a 5 s budget per source, and keeps found clips for a minute.

Downloads hit the same networks. Jiten (`api.jiten.moe`, Hetzner) never finished on the owner's phone on mobile data
(2026-10-18); from a computer it answers in 0.4 s and sends its 7.5 MB in under a second, but it answers a range
request with the whole file and no ETag or Last-Modified, so every try starts over. The import queue therefore breaks a
download off after 20 s without data and retries it at the end of the queue (`docs/architecture.md`).

## The owner's server

A Yomitan local-audio style server (`?term={term}&reading={reading}`) answering `audioSourceList` JSON with NHK16,
SMK8 and Forvo clips (opus). It must be a "Custom URL (JSON)" source; as a "Custom URL" source it found nothing, which
the source test now reports (`AudioError.Kind.SOURCE_LIST`).

## Other languages

Audio selection caches include the resolved per-language region order as well as the sources and term. Capture both
from one settings read and pass the same order to the source requests: re-reading it mid-request can cache a clip
under another order's key. Changing region priority must affect the next selection, even within the one-minute TTL.

Lingua Libre and Wiktionary results for English, Russian, Korean and Chinese, the Mandarin codes and pinyin file
names, and the compound matches of the Lingua Libre pattern: `languages.md`, "Audio per language".

Lingua Libre titles name the language twice, by its Wikidata item and by a code in parentheses
(`LL-Q9129 (ell)-Speaker-word.wav`). The item is what identifies it; the code varies: Greek files carry `ell`, `gre`
(ISO 639-2/B) or `mis` (2026-10-19: 167, 130 and 84). `AudioPages.linguaLibreSearch` therefore matches any
three-letter code after the item. Checked for fr, de, nl, cs, ms, zh, sh, be and tl: one code each, with 105 French
and 9 Mandarin files under others. Commons answers 429 to fast series of searches; wait a few seconds between them.

Croatian and Serbian (2026-10-19, 20 common words in both scripts): Wiktionary's `Hr-` files (about 60 in the
category, some titled without diacritics, `Hr-garaza.oga`) cover 4 of the 20 (dan, ruka, ljubav, sreća);
`Sr-` files about 14, mostly titled in Cyrillic (`Sr-кућа.flac`), a few in Latin letters (`Sr-majka.ogg`,
`Sr-grad.ogg`). Lingua Libre has one Croatian recording (Q6654) and 93 Serbian ones (Q9299, `srp`), all titled in
Latin letters. So Croatian looks for `Hr-` first, then for the Serbian file of the word in Cyrillic, and searches
both Lingua Libre items, Croatian speakers first; Serbian looks for `Sr-` under both spellings and searches Lingua
Libre in Latin letters. A word Serbian spells otherwise (Croatian mlijeko, Serbian млеко) has no Serbian file.
The Commons regular expression is not anchored: `Sr-вода` also matched `LL-Q7737 (rus)-Tatiana Kerbush-вода.wav`,
so `AudioPages.wiktionaryFiles` keeps only titles that are a recording of the term as a whole.

Title case (2026-10-20): `intitle:/…/` is case-sensitive, while the plain `intitle:"…"` in front of it is not. Some
titles capitalize the word (`Cy-Bwyta.ogg`, `LL-Q9314 (gla)-DaoineÈibhinn-Uisge.wav`), so both searches take the
term's first letter in either case, by the case rules of the language's tag (`[bB]wyta`; Turkish `[iİ]yi`, `[ıI]lık`).
Only the first letter: a word capitalized throughout is rare in titles. A letter of a script without case, or one
whose capital is two letters (ß), stays as it is. Checked live: the new searches find both files above, the
lowercase ones found neither.

Longer tags between the language code and the word (2026-10-20, titles `<Prefix>-<3+ letters>-…` on Commons): de
9,471, nearly all compounds (`De-Corona-Testen.ogg`, `De-NATO-Mitglied.ogg`); fr 7,256: 6,386 `Fr-Paris--word.ogg`
(two hyphens, see below), 103 `Fr-Normandie-`, the rest compounds and phrases (`Fr-grand-mère.ogg`,
`Fr-avoir-soif.ogg`); en 575: one speaker's horse names (`En-voristu-…`), spoken articles, a few accents (`En-NYC-`,
`En-boston-us-`, `En-midwest-`); es 315 (`Es-chile-…` phrases, spoken articles); br 495 (`Br-<place>-<speaker>-
Wikikomzoù.flac`); pt 6, it 17; cy 4, all place names (`Cy-Ynys-hir.ogg`); gd 4, `Gd-Lewis-` (aon, dhà, dèideag,
dèideadh). Taking any such tag for a region would play `De-Corona-Testen` for testen and `Fr-grand-mère` for mère, so
only the longer codes a language lists in `audioRegions` count (`am-lat`, `fr-paris`). A dialect tag can be searched
as part of the code (`RecordingTitle("gd-Lewis", term)` from `recordings`), at one more search per lookup. The
measurement stopped at nl when Commons began answering 429; the languages after it were not counted.

French Paris recordings carry two conventions: `Fr-fr-paris-guenon.ogg` (57 files, the code `FrenchSupport` lists)
and `Fr-Paris--dioxyde.ogg` (7,886 files, two hyphens; many place names, also words such as dioxyde, attovolt). The
second is not searched yet. Listing it as a code would need the search to take a longer code's case as written
(`Paris-`): longer codes are matched case-sensitively there, while `wiktionaryFiles` ignores case.
