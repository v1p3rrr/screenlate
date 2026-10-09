import json, os, sys
sys.stdout.reconfigure(encoding="utf-8")
d = sys.argv[1]
targets = ["en", "ru", "es", "fr", "de", "it", "pt", "pl", "tr", "vi", "ja", "ko", "zh"]
print("src | " + " | ".join(targets))
for name in sorted(os.listdir(d)):
    src = name[:-5]
    files = [f for f in json.load(open(os.path.join(d, name), encoding="utf-8")) if f["type"] == "file" and f["path"].endswith(".zip")]
    cells = []
    for t in targets:
        main = [f for f in files if os.path.basename(f["path"]) == f"wty-{src}-{t}.zip"]
        glos = [f for f in files if os.path.basename(f["path"]) == f"wty-{src}-{t}-gloss.zip"]
        c = ""
        if main: c += f"{main[0]['size']/1e6:.1f}"
        if glos: c += f" g{glos[0]['size']/1e6:.2f}"
        cells.append(c or "-")
    print(src + " | " + " | ".join(cells))
names = sorted({os.path.basename(f["path"]) for f in json.load(open(os.path.join(d, "zh.json"), encoding="utf-8")) if f["type"] == "file"})
print(names[:15])
