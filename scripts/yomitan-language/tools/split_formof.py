import json, sys, zipfile
sys.stdout.reconfigure(encoding="utf-8")
src, lemmas_out, forms_out = sys.argv[1:4]
z = zipfile.ZipFile(src)
lem, frm = [], []
for name in sorted(z.namelist()):
    if name.startswith("term_bank"):
        for row in json.loads(z.read(name)):
            (frm if all(isinstance(g, list) for g in row[5]) else lem).append(row)
def write(path, rows, title):
    idx = json.loads(z.read("index.json"))
    idx["title"] = title
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as out:
        out.writestr("index.json", json.dumps(idx, ensure_ascii=False))
        for i in range(0, len(rows), 25000):
            out.writestr(f"term_bank_{i // 25000 + 1}.json", json.dumps(rows[i:i + 25000], ensure_ascii=False))
write(lemmas_out, lem, "ru-lemmas")
write(forms_out, frm, "ru-forms")
pairs = sum(len(r[5]) for r in frm)
keys = len({r[0] for r in frm})
print("lemma rows", len(lem), "form rows", len(frm), "form items", pairs, "distinct form texts", keys)
