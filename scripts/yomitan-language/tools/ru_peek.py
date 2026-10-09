import io, json, sys, zipfile
sys.stdout.reconfigure(encoding="utf-8")
path, words = sys.argv[1], set(sys.argv[2:])
z = zipfile.ZipFile(path)
print(json.loads(z.read("index.json")))
total = forms = 0
stressed = 0
for name in sorted(z.namelist()):
    if not name.startswith("term_bank"):
        continue
    for row in json.loads(z.read(name)):
        total += 1
        if any(isinstance(g, list) for g in row[5]):
            forms += 1
        if "́" in row[0]:
            stressed += 1
        if row[0] in words:
            print(json.dumps(row, ensure_ascii=False)[:400])
print("rows", total, "form-of rows", forms, "stressed headwords", stressed)
