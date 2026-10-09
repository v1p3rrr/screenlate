"""Record bytes if form-of rows were stored in hoshidicts' redirects field (strings inline, no glossary blob)."""
import collections, json, sys, zipfile
sys.stdout.reconfigure(encoding="utf-8")
for path in sys.argv[1:]:
    z = zipfile.ZipFile(path)
    rec = rows = 0; tags = collections.Counter(); keys = set()
    for name in sorted(z.namelist()):
        if not name.startswith("term_bank"): continue
        for row in json.loads(z.read(name)):
            fo = [g for g in row[5] if isinstance(g, list)]
            if not fo: continue
            rows += 1
            expr, reading = row[0].encode(), (row[1] or row[0]).encode()
            keys.add(expr); keys.add(reading)
            # type, expr len+bytes, reading len+bytes, glossary offset/size, three tag strings with lengths, score
            size = 1 + 2 + len(expr) + 2 + len(reading) + 8 + 4 + 1 + len(row[2].encode()) + 1 + len(row[3].encode()) + 1 + len(str(row[7] if len(row) > 7 else "").encode()) + 4
            size += 4
            for g in fo:
                size += 4 + len(g[0].encode()) + 4
                for t in (g[1] if len(g) > 1 else []):
                    size += 4 + len(t.encode())
                    for part in t.split(): tags[part] += 1
            rec += size + 8  # plus the offset in the key's offset list
    print(f"{path.split('/')[-1]}: form rows {rows}, records with redirects {rec/1e6:.1f} MB, index keys {len(keys)}, "
          f"distinct tag words {len(tags)}; top: {[t for t, _ in tags.most_common(40)]}")
