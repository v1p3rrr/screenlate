"""Runs Screenlate's Commons searches (Lingua Libre, Wiktionary) for sample words; args: language codes (en zh ru ko)."""
import json, sys, time, urllib.error, urllib.parse, urllib.request

sys.stdout.reconfigure(encoding="utf-8", line_buffering=True)
API = "https://commons.wikimedia.org/w/api.php"
UA = "Screenlate-research/0.1 (language audio check)"
SPECIAL = set('.?+*|{}[]()"\#@&<>~')

def esc(t): return "".join("\\" + c if c in SPECIAL else c for c in t)
def title_word(t): return 'intitle:"%s" ' % t.replace('"', '')
def ll(term, qid, iso3): return title_word(term) + 'intitle:/LL-%s \(%s\)-.*-%s\.wav/' % (qid, iso3, esc(term))
def wikt(term, code):
    prefix = "[%s%s]%s" % (code[0].upper(), code[0].lower(), code[1:])
    return title_word(term) + 'intitle:/%s(-[a-zA-Z]{2})?-%s[0-9]*\.(ogg|oga|opus|wav|mp3|flac)/' % (prefix, esc(term))

def search(q, limit=10):
    url = API + "?" + urllib.parse.urlencode({"action": "query", "format": "json", "list": "search", "srsearch": q, "srnamespace": "6", "srlimit": str(limit)})
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for attempt in range(5):
        time.sleep(2)
        t = time.time()
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                data = json.load(r)
            break
        except urllib.error.HTTPError as e:
            if e.code != 429: raise
            time.sleep(10 * (attempt + 1))
    return [s["title"] for s in data.get("query", {}).get("search", [])], int((time.time() - t) * 1000)

LANGS = {
    "en": ("Q1860", "eng", ["cat", "run", "beautiful", "water", "look up", "although", "dog", "house", "think", "quickly"]),
    "zh": ("Q727694", "cmn", ["猫", "水", "学习", "朋友", "电脑", "電腦", "中国", "吃饭", "漂亮", "天气"]),
    "ru": ("Q7737", "rus", ["кошка", "вода", "дом", "красивый", "идти", "человек", "ёжик", "спасибо", "собака", "хорошо"]),
    "ko": ("Q9176", "kor", ["먹다", "물", "사람", "학교", "공부하다", "한국어", "가다", "사랑", "친구", "읽다"]),
}

for code, (qid, iso3, words) in LANGS.items():
    if len(sys.argv) > 1 and code not in sys.argv[1:]: continue
    ll_hits = wk_hits = 0
    for w in words:
        a, ta = search(ll(w, qid, iso3))
        b, tb = search(wikt(w, code))
        ll_hits += bool(a); wk_hits += bool(b)
        print(f"{code} {w}: LL {len(a)} ({ta} ms) {a[:2]} | Wikt {len(b)} ({tb} ms) {b[:3]}")
    print(f"== {code}: Lingua Libre {ll_hits}/{len(words)}, Wiktionary {wk_hits}/{len(words)}")
