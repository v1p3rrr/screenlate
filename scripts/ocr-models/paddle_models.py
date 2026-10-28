"""Builds the PaddleOCR model archives for the Paddle engine and writes their catalog entries.

    python -I scripts/ocr-models/paddle_models.py --mnnconvert <path to MNN's mnnconvert> [--work testdata/ocr/paddle]

Each model comes from PaddlePaddle's ONNX repository on Hugging Face at a pinned commit, checked by the SHA-256 of
its inference.onnx, and is converted to MNN's format with MNN's converter (pip package mnn 3.6.1, as the app's MNN).
A recognizer's character list is taken from its inference.yml. Each archive holds model.mnn, dict.txt for a
recognizer, detector.properties for a detector with its own thresholds, the Apache License 2.0 and a note on where the
model comes from. Archives are written to <work>/archives with fixed timestamps, so the same files give the same
SHA-256; the version is the start of it. Entries of the "paddle-*" templates in dictionaries-v2.json are replaced;
the template texts are kept as they are in the file. A model directory under <work> that already holds the converted
files is used as it is.
"""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import urllib.request
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
CATALOG = os.path.join(ROOT, "dictionary", "api", "src", "main", "assets", "catalog", "dictionaries-v2.json")
LICENSE = os.path.join(ROOT, "core", "ocr-mnn", "src", "main", "cpp", "MNN", "LICENSE.txt")

# GitHub release that hosts the archives.
RELEASE = "https://github.com/v1p3rrr/screenlate/releases/download/ocr-models-1"

# Hugging Face repository PaddlePaddle/<name>_onnx: pinned commit and the SHA-256 of its inference.onnx.
SOURCES = {
    "PP-OCRv5_mobile_det": ("e6f4fa85f00e168c862bc462aebca69eef9b3d3d",
                            "a431985659dc921974177a95adcfbb90fd9e51989a5e04d70d0b75f597b6e61d"),
    "PP-OCRv6_small_det": ("28fe5895c24fd108c19eb3e8479f4ab385fbfc62",
                           "d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e"),
    "PP-OCRv6_small_rec": ("b8f84f0b80c529de40b4fbb3544b84fa7233a513",
                           "5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634"),
    "latin_PP-OCRv5_mobile_rec": ("89d3a50e2c27e2e7cceeab0e944c25c807d5db4f",
                                  "7888113072263cb471b93f66dd5e2ad70548dc526fa1ace760d0d973dd121498"),
    "cyrillic_PP-OCRv5_mobile_rec": ("2cef88145434beb8afa9dd82d77d799eb1ad7b29",
                                     "5371ee1ddaa7983cc62d0818d99e982b6804638c85e4f960d59a574094e172e5"),
    "korean_PP-OCRv5_mobile_rec": ("5c6f574b8e2230adf4287b33e736d71b9fabd28e",
                                   "92f0b7785e64fc9090106a241cf4c1eb97472824558272751b88a2a4476d3a08"),
    "el_PP-OCRv5_mobile_rec": ("8152b89d2ee0e1d4c92decab75fe75e5e3d836b6",
                               "2acf17fcaea2bc81b878e311e6263b8885f48bb03796f75f9f30ed3242bbaa6d"),
}

DETECTORS = {
    "paddle-detector-v5": {"source": "PP-OCRv5_mobile_det", "template": "paddle-detector-v5"},
    # PP-OCRv6's own post-processing values; the unclip ratio stays at v5's 1.5, as measured in the comparison.
    "paddle-detector-v6": {"source": "PP-OCRv6_small_det", "template": "paddle-detector-v6",
                           "properties": {"threshold": "0.2", "box_threshold": "0.45"}},
}

# Esperanto is left out: both models read every one of its hat letters wrong (ĝ as ġ or ğ, ŭ as ǔ or ů).
LATIN = ["en", "de", "fr", "es", "it", "pt", "pl", "tr", "lv", "nl", "cs", "id", "ms", "tl", "hr", "sv", "no", "da", "ro", "la", "lt", "sk", "sl", "sq", "sw", "cy", "ga"]

# Languages each recognizer is offered for: those the OCR comparison measured it on. Serbian, written in both scripts,
# which no model reads together, has the Cyrillic model and the Latin one measured best on Serbo-Croatian.
RECOGNIZERS = {
    "paddle-v6": {"source": "PP-OCRv6_small_rec", "template": "paddle-v6", "requires": "paddle-detector-v6",
                  "languages": ["ja", "zh"] + LATIN + ["sr"]},
    "paddle-latin": {"source": "latin_PP-OCRv5_mobile_rec", "template": "paddle-v5", "requires": "paddle-detector-v5",
                     "languages": LATIN},
    "paddle-cyrillic": {"source": "cyrillic_PP-OCRv5_mobile_rec", "template": "paddle-v5",
                        "requires": "paddle-detector-v5", "languages": ["ru", "uk", "be", "sr", "kk", "ky"]},
    "paddle-korean": {"source": "korean_PP-OCRv5_mobile_rec", "template": "paddle-v5",
                      "requires": "paddle-detector-v5", "languages": ["ko"]},
    "paddle-el": {"source": "el_PP-OCRv5_mobile_rec", "template": "paddle-v5", "requires": "paddle-detector-v5",
                  "languages": ["el"]},
}

# The recommendation rule over the clean and hard screens (ai/notes/ocr-engines.md); ko and vi keep ML Kit, and the
# Latin-script languages not measured (nl, id, ms, tl) recommend none. Belarusian, not measured, takes Ukrainian's.
# Kazakh: 2.2% of the characters wrong over the clean and hard screens against Tesseract's 22.8-24.0%. Kyrgyz, whose
# letters are a subset of Kazakh's: 0.0% on drawn screens in eleven styles. Swedish: the Latin v5 model makes the
# fewest errors of the on-device models over the clean and hard screens; Norwegian: v6; Danish: v5 and v6 both make
# none, and v5 is the smaller download; Romanian: v5 and v6 0.9% each (Tesseract fast 1.1%), v5 the smaller download;
# Slovenian and Albanian: v5 and v6 both make none, and v5 is the smaller download. Swahili: ML Kit makes none
# either, so no model is marked. Irish: ML Kit's 0.45% of the characters wrong leaves less than 0.5 points to gain,
# so none is marked.
RECOMMENDED = {
    "ja": "paddle-v6", "zh": "paddle-v6", "de": "paddle-v6", "fr": "paddle-v6", "it": "paddle-v6", "pl": "paddle-v6",
    "lv": "paddle-v6", "en": "paddle-latin", "es": "paddle-latin", "pt": "paddle-latin", "tr": "paddle-latin",
    "ru": "paddle-cyrillic", "cs": "paddle-v6", "uk": "paddle-cyrillic", "be": "paddle-cyrillic", "el": "paddle-el",
    "hr": "paddle-v6", "sr": "paddle-cyrillic", "kk": "paddle-cyrillic",
    "ky": "paddle-cyrillic", "sv": "paddle-latin", "no": "paddle-v6", "da": "paddle-latin", "ro": "paddle-latin",
    "la": "paddle-v6", "sl": "paddle-latin", "sq": "paddle-latin",
}

# Languages ML Kit reads whose comparison found several models more accurate than ML Kit: each is marked, Tesseract's
# too (tessdata_catalog.py), and all stay unticked at setup. Welsh: Latin v5 1.25% and v6 1.09% of the characters
# wrong against ML Kit's 2.19%.
MORE_ACCURATE = {"cy": {"paddle-latin", "paddle-v6"}}

TEMPLATES = {"paddle-detector-v5", "paddle-detector-v6", "paddle-v6", "paddle-v5"}

ZIP_TIME = (2026, 1, 1, 0, 0, 0)


def source_url(name, file):
    commit, _ = SOURCES[name]
    return f"https://huggingface.co/PaddlePaddle/{name}_onnx/resolve/{commit}/{file}"


def download(url, path):
    with urllib.request.urlopen(url) as response:
        data = response.read()
    with open(path, "wb") as file:
        file.write(data)
    return data


def characters(yml):
    """The character list of a PP-OCR inference.yml (PostProcess.character_dict)."""
    lines = yml.split("\n")
    start = next(i for i, line in enumerate(lines) if line.strip() == "character_dict:")
    result = []
    for line in lines[start + 1:]:
        stripped = line.lstrip(" ")
        if not stripped.startswith("- "):
            break
        value = stripped[2:]
        if value.startswith("'") and value.endswith("'"):
            value = value[1:-1].replace("''", "'")
        elif value.startswith('"') and value.endswith('"'):
            value = json.loads(value)
        result.append(value)
    return result


def prepare(name, work, mnnconvert, recognizer):
    """<work>/<name> with model.mnn (and dict.txt for a recognizer), downloaded and converted when missing."""
    directory = os.path.join(work, name)
    model = os.path.join(directory, "model.mnn")
    dictionary = os.path.join(directory, "dict.txt")
    if os.path.exists(model) and (not recognizer or os.path.exists(dictionary)):
        return directory
    if not mnnconvert:
        raise SystemExit(f"{name} is not converted yet: pass --mnnconvert")
    os.makedirs(directory, exist_ok=True)
    onnx = os.path.join(directory, "inference.onnx")
    data = download(source_url(name, "inference.onnx"), onnx)
    if hashlib.sha256(data).hexdigest() != SOURCES[name][1]:
        raise SystemExit(f"{name}: inference.onnx does not match its pinned SHA-256")
    if recognizer:
        yml = download(source_url(name, "inference.yml"), os.path.join(directory, "inference.yml"))
        with open(dictionary, "w", encoding="utf-8", newline="\n") as file:
            file.write("\n".join(characters(yml.decode("utf-8"))) + "\n")
    subprocess.run([mnnconvert, "-f", "ONNX", "--modelFile", onnx, "--MNNModel", model, "--bizCode", "MNN"],
                   check=True, stdout=subprocess.DEVNULL)
    return directory


def apache_license():
    """The Apache License 2.0 terms, without MNN's own copyright appendix."""
    with open(LICENSE, encoding="utf-8") as file:
        text = file.read()
    end = text.index("END OF TERMS AND CONDITIONS") + len("END OF TERMS AND CONDITIONS")
    return text[:end].rstrip() + "\n"


def note(name):
    commit, sha256 = SOURCES[name]
    return (
        f"{name}, a PaddleOCR model by the PaddlePaddle Authors (https://github.com/PaddlePaddle/PaddleOCR),\n"
        "licensed under the Apache License 2.0 (LICENSE).\n\n"
        f"Converted for Screenlate (https://github.com/v1p3rrr/screenlate) from\n"
        f"https://huggingface.co/PaddlePaddle/{name}_onnx at commit {commit}\n"
        f"(inference.onnx SHA-256 {sha256}) to MNN's format with MNN's converter 3.6.1.\n"
        "dict.txt, where present, is the character list from the repository's inference.yml.\n"
        "The weights are not changed otherwise.\n"
    )


def archive(model, name, directory, out, properties=None):
    """Writes <out>/<model>.zip and returns its bytes."""
    files = [("model.mnn", os.path.join(directory, "model.mnn"))]
    if os.path.exists(os.path.join(directory, "dict.txt")):
        files.append(("dict.txt", os.path.join(directory, "dict.txt")))
    texts = [("LICENSE", apache_license()), ("NOTICE", note(name))]
    if properties:
        texts.append(("detector.properties", "".join(f"{k}={v}\n" for k, v in properties.items())))
    os.makedirs(out, exist_ok=True)
    path = os.path.join(out, f"{model}.zip")
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as zip_file:
        for entry, source in files:
            with open(source, "rb") as file:
                zip_file.writestr(zipfile.ZipInfo(entry, ZIP_TIME), file.read(), zipfile.ZIP_DEFLATED)
        for entry, text in texts:
            zip_file.writestr(zipfile.ZipInfo(entry, ZIP_TIME), text.encode("utf-8"), zipfile.ZIP_DEFLATED)
    with open(path, "rb") as file:
        return file.read()


def entry(model, language, template, data, requires=None, recommended=False):
    sha256 = hashlib.sha256(data).hexdigest()
    result = {
        "id": f"{model}-{language}",
        "title": f"PaddleOCR {model.removeprefix('paddle-')} ({language})",
        "template": template,
        "category": "OCR_MODEL",
        "sourceLanguage": language,
        "recommended": recommended,
        "downloadUrl": f"{RELEASE}/{model}.zip",
        "downloadSize": len(data),
        "sha256": sha256,
        "model": model,
        "engine": "paddle",
        "version": sha256[:12],
        "license": "Apache-2.0",
        "homepage": "https://github.com/PaddlePaddle/PaddleOCR",
    }
    if requires:
        result["requires"] = [requires]
    return result


def entries(archives):
    """Catalog entries: per language, each recognizer offered for it, then the detector it requires."""
    languages = []
    for spec in RECOGNIZERS.values():
        languages += [language for language in spec["languages"] if language not in languages]
    built = []
    for language in languages:
        detectors = []
        for model, spec in RECOGNIZERS.items():
            if language not in spec["languages"]:
                continue
            built.append(entry(model, language, spec["template"], archives[model], spec["requires"],
                               RECOMMENDED.get(language) == model or model in MORE_ACCURATE.get(language, ())))
            if spec["requires"] not in detectors:
                detectors.append(spec["requires"])
        for detector in detectors:
            built.append(entry(detector, language, DETECTORS[detector]["template"], archives[detector]))
    return built


def replace(document, built):
    """[document] with its Paddle entries replaced by [built], placed where the first one was or at the end."""
    dictionaries = document["dictionaries"]
    positions = [i for i, e in enumerate(dictionaries) if e.get("template") in TEMPLATES]
    kept = [e for e in dictionaries if e.get("template") not in TEMPLATES]
    at = positions[0] if positions else len(dictionaries)
    at -= sum(1 for i in positions if i < at)
    return {**document, "dictionaries": kept[:at] + built + kept[at:]}


def without_recommendation(document, languages):
    """Other models of [languages] lose their recommended mark: each language recommends one model."""
    def fix(e):
        if e.get("category") == "OCR_MODEL" and e.get("template") not in TEMPLATES and \
                e.get("sourceLanguage") in languages and e.get("recommended"):
            return {**e, "recommended": False}
        return e
    return {**document, "dictionaries": [fix(e) for e in document["dictionaries"]]}


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--work", default=os.path.join(ROOT, "testdata", "ocr", "paddle"),
                        help="where downloaded and converted models and the archives are kept")
    parser.add_argument("--mnnconvert", help="MNN's converter, needed for models not converted yet")
    args = parser.parse_args()
    with open(CATALOG, encoding="utf-8") as file:
        document = json.load(file)
    missing = sorted(t for t in TEMPLATES if t not in document.get("templates", {}))
    if missing:
        raise SystemExit(f"The catalog has no template {', '.join(missing)}")
    out = os.path.join(args.work, "archives")
    if os.path.isdir(out):
        shutil.rmtree(out)
    archives = {}
    for model, spec in {**DETECTORS, **RECOGNIZERS}.items():
        directory = prepare(spec["source"], args.work, args.mnnconvert, recognizer=model in RECOGNIZERS)
        archives[model] = archive(model, spec["source"], directory, out, spec.get("properties"))
        print(f"{model}.zip {len(archives[model])} {hashlib.sha256(archives[model]).hexdigest()}")
    built = entries(archives)
    document = without_recommendation(replace(document, built), set(RECOMMENDED))
    with open(CATALOG, "w", encoding="utf-8", newline="\n") as file:
        file.write(json.dumps(document, ensure_ascii=False, indent=2) + "\n")
    print(f"{len(built)} Paddle entries")


if __name__ == "__main__":
    main()
