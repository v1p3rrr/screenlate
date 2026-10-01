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

## The owner's server

A Yomitan local-audio style server (`?term={term}&reading={reading}`) answering `audioSourceList` JSON with NHK16,
SMK8 and Forvo clips (opus). It must be a "Custom URL (JSON)" source; as a "Custom URL" source it found nothing, which
the source test now reports (`AudioError.Kind.SOURCE_LIST`).
