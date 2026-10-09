"""Estimates a compact form-of table (form -> [(lemma, tags)]) for Yomitan term dictionaries."""
import json, sys, zipfile, zlib
sys.stdout.reconfigure(encoding="utf-8")

def varint(n):
    out = bytearray()
    while True:
        b = n & 0x7F; n >>= 7
        out.append(b | (0x80 if n else 0))
        if not n: return bytes(out)

def front_coded(keys, block=16):
    size = 0; offsets = 0
    for i in range(0, len(keys), block):
        offsets += 4
        prev = b""
        for j, k in enumerate(keys[i:i + block]):
            if j == 0:
                size += len(varint(len(k))) + len(k)
            else:
                p = 0
                while p < min(len(prev), len(k)) and prev[p] == k[p]: p += 1
                size += len(varint(p)) + len(varint(len(k) - p)) + len(k) - p
            prev = k
    return size + offsets

for path in sys.argv[1:]:
    z = zipfile.ZipFile(path)
    rows = form_rows = mixed = items = 0
    forms = {}            # expression -> list of (lemma, tags)
    lemma_rows = 0
    for name in sorted(z.namelist()):
        if not name.startswith("term_bank"): continue
        for row in json.loads(z.read(name)):
            rows += 1
            gl = row[5]
            fo = [g for g in gl if isinstance(g, list)]
            if not fo: lemma_rows += 1; continue
            if len(fo) == len(gl): form_rows += 1
            else: mixed += 1
            lst = forms.setdefault(row[0], [])
            for g in fo:
                lemma = g[0]; tags = tuple(g[1]) if len(g) > 1 else ()
                lst.append((lemma, tags)); items += 1
    lemmas = {}; tagsets = {}
    for lst in forms.values():
        for lemma, tags in lst:
            lemmas.setdefault(lemma, len(lemmas)); tagsets.setdefault(tags, len(tagsets))
    keys = sorted(k.encode() for k in forms)
    key_bytes = sum(len(k) for k in keys)
    fc = front_coded(keys)
    lemma_bytes = sum(len(l.encode()) + 1 for l in lemmas)
    lemma_fc = front_coded(sorted(l.encode() for l in lemmas))
    tag_bytes = sum(len(json.dumps(t, ensure_ascii=False).encode()) for t in tagsets)
    # Entries: count, then (lemma id, tag set id) pairs; ids ordered by first use, so frequent ones are small.
    entry_bytes = 0; entry_offsets = 4 * len(keys)
    for k, lst in forms.items():
        entry_bytes += len(varint(len(lst)))
        for lemma, tags in lst:
            entry_bytes += len(varint(lemmas[lemma])) + len(varint(tagsets[tags]))
    total = fc + lemma_fc + tag_bytes + entry_bytes + entry_offsets
    raw_inline = sum(len(k.encode()) for k in forms) + sum(len(l.encode()) + len(json.dumps(t, ensure_ascii=False).encode()) for lst in forms.values() for l, t in lst)
    print(f"{path.split('/')[-1]}: rows {rows}, lemma rows {lemma_rows}, form-of only {form_rows}, mixed {mixed}, "
          f"form items {items}, distinct forms {len(forms)}, lemmas {len(lemmas)}, tag sets {len(tagsets)}")
    print(f"  keys raw {key_bytes/1e6:.1f} MB, front-coded {fc/1e6:.1f} MB; lemmas {lemma_bytes/1e6:.2f} MB (fc {lemma_fc/1e6:.2f}); "
          f"tag sets {tag_bytes/1e3:.0f} KB; entries {entry_bytes/1e6:.1f} MB + offsets {entry_offsets/1e6:.1f} MB")
    print(f"  compact table total {total/1e6:.1f} MB (inline strings without dedup {raw_inline/1e6:.1f} MB)")
