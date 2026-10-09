#!/usr/bin/env python3
"""Read-only release audit: pinned forest sources, palette geometry and new module links.

Requires the already downloaded research archives/pages for the release provenance check.
Never downloads, regenerates or modifies a PNG. Writes an evidence manifest under .verification.
"""
from pathlib import Path
import hashlib
import io
import json
import sys
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/interstice"
SOURCES = ROOT / "art/sources/cc0/forest-ecology"
SOURCE_PAGES = {
    "thistle.png": "research/cc0-20261008/block-set-page.html",
    "poison_cap.png": "research/cc0-20261008/assorted-page.html",
}
RETORT_BASELINE = {
    "retort_chemotrophic_fabric", "retort_grain_separation", "retort_phosphorite_paste",
    "retort_protective_coating", "retort_pure_vitriolite_lining", "retort_purified_bread",
    "retort_purified_root", "retort_umbral_sorbent", "retort_venom_fiber", "retort_vitriolite_lining",
}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def check(condition, message):
    if not condition:
        raise ValueError(message)


def pixel_classes(image):
    labels = {}
    pixels = list(image.convert("RGBA").get_flattened_data())
    return tuple(pixel[3] for pixel in pixels), tuple(labels.setdefault(pixel, len(labels)) for pixel in pixels)


def audit():
    manifest = load(SOURCES / "sources.json")
    provenance_path = ASSETS / "provenance/forest-ecology.json"
    provenance = load(provenance_path)
    check(provenance["license"] == "CC0-1.0", "Forest provenance license changed")
    check(ROOT / provenance["sources"] == SOURCES / "sources.json", "Forest source registry link changed")
    declarations = {entry["file"]: entry for entry in manifest["sources"]}
    check(set(declarations) == set(SOURCE_PAGES), "Expected both pinned whole forest sources")
    license_path = SOURCES / "CC0-1.0.html"
    license_text = license_path.read_text(encoding="utf-8")
    check("CC0" in license_text and "zero/1.0" in license_text, "Saved CC0 license copy is incomplete")
    check(sha(license_path) == sha(ROOT / "art/sources/cc0/minerals/CC0-1.0.html"), "Forest license differs from the verified original CC0 copy")
    source_rows, palette_rows = [], []
    for name, entry in sorted(declarations.items()):
        source = SOURCES / name
        check(sha(source) == entry["sha256"], "Source SHA mismatch: " + name)
        check(entry["license"] == "CC0-1.0" and entry["author"] and entry["source_url"].startswith("https://opengameart.org/content/"), "Missing source author/license/origin: " + name)
        archive = ROOT / entry["archive"]
        check(sha(archive) == entry["archive_sha256"], "Archive SHA mismatch: " + name)
        with zipfile.ZipFile(archive) as content:
            check(content.read(entry["source_member"]) == source.read_bytes(), "Whole archive member differs: " + name)
        page = ROOT / SOURCE_PAGES[name]
        page_text = page.read_text(encoding="utf-8")
        check("CC0" in page_text and "zero/1.0" in page_text and entry["source_url"] in page_text, "Saved primary page lacks the declared CC0/source URL: " + name)
        source_rows.append({**entry, "archive_member_bytes_exact": True, "source_page": SOURCE_PAGES[name], "source_page_sha256": sha(page)})
    expected_outputs = {"block/forest/venom_reed.png", "block/forest/venom_fiber.png", "block/forest/spore_pod_armed.png", "block/forest/spore_pod_spent.png"}
    check({entry["output"] for entry in provenance["outputs"]} == expected_outputs and len(provenance["outputs"]) == 4, "Expected exactly four new forest PNG outputs")
    for entry in provenance["outputs"]:
        source = SOURCES / entry["source"]
        output = ASSETS / "textures" / entry["output"]
        check(entry["source"] in declarations and sha(source) == entry["source_sha256"] == declarations[entry["source"]]["sha256"], "Output references an unpinned source: " + entry["output"])
        check(sha(output) == entry["output_sha256"], "Output SHA mismatch: " + entry["output"])
        with Image.open(source) as original, Image.open(output) as prepared:
            before, after = original.convert("RGBA"), prepared.convert("RGBA")
            check(before.size == after.size == (16, 16), "Forest PNG resolution changed: " + entry["output"])
            forward, reverse = {}, {}
            for first, last in zip(before.get_flattened_data(), after.get_flattened_data()):
                check(first[3] == last[3], "Alpha changed: " + entry["output"])
                check(first not in forward or forward[first] == last, "Pixel pattern was repainted: " + entry["output"])
                check(last not in reverse or reverse[last] == first, "Distinct source pixel classes merged: " + entry["output"])
                forward[first], reverse[last] = last, first
            check(len(forward) == len(reverse) == entry["classes"], "Recorded class count changed: " + entry["output"])
            palette_rows.append({**entry, "resolution": [16, 16], "pixel_coordinates_and_alpha_exact": True, "bijective_palette_only": True})

    models_checked = set()
    with zipfile.ZipFile(ROOT / "build/moddev/artifacts/neoforge-21.1.252-client-extra-aka-minecraft-resources.jar") as minecraft:
        vanilla_patterns = {}
        vanilla_count = 0
        for name in minecraft.namelist():
            if not name.endswith(".png") or not name.startswith(("assets/minecraft/textures/block/", "assets/minecraft/textures/item/")):
                continue
            with Image.open(io.BytesIO(minecraft.read(name))) as image:
                if image.size == (16, 16):
                    vanilla_count += 1
                    vanilla_patterns.setdefault(pixel_classes(image), []).append(name)
        check(vanilla_count > 0, "No native whole 16x16 sprites available for source comparison")
        transforms = [None, Image.Transpose.ROTATE_90, Image.Transpose.ROTATE_180, Image.Transpose.ROTATE_270,
                      Image.Transpose.FLIP_LEFT_RIGHT, Image.Transpose.FLIP_TOP_BOTTOM, Image.Transpose.TRANSPOSE, Image.Transpose.TRANSVERSE]
        for entry in source_rows:
            matches = []
            with Image.open(SOURCES / entry["file"]) as image:
                for transform in transforms:
                    candidate = image if transform is None else image.transpose(transform)
                    matches.extend(vanilla_patterns.get(pixel_classes(candidate), []))
            check(not matches, "Whole source repeats a native Minecraft pixel-class/alpha pattern: " + entry["file"] + ": " + str(matches))
            entry["native_whole_sprite_pattern_matches"] = matches

        def resource(kind, reference, suffix=".json"):
            if ":" not in reference:
                reference = "minecraft:" + reference
            namespace, name = reference.split(":", 1)
            path = f"assets/{namespace}/{kind}/{name}{suffix}"
            return minecraft.read(path) if namespace == "minecraft" else (RES / path).read_bytes()

        def model(reference, visiting=()):
            check(reference not in visiting, "Model cycle: " + reference)
            document = json.loads(resource("models", reference))
            parent = document.get("parent")
            inherited = model(parent, visiting + (reference,)) if parent and parent not in {"builtin/generated", "builtin/entity"} else {}
            merged = {**inherited, **document}
            textures = {**inherited.get("textures", {}), **document.get("textures", {})}
            merged["textures"] = textures
            return merged

        def validate_model(reference):
            # Vanilla abstract parents bind their variables in the child, so validate after inheritance.
            merged = model(reference)
            textures = merged.get("textures", {})
            for value in textures.values():
                seen = set()
                while value.startswith("#"):
                    check(value not in seen, "Texture variable cycle: " + reference)
                    seen.add(value)
                    value = textures[value[1:]]
                resource("textures", value, ".png")
            for part in merged.get("elements", []):
                check(all(a <= b for a, b in zip(part["from"], part["to"])), "Inverted model element: " + reference)
                for face in part.get("faces", {}).values():
                    check(not face["texture"].startswith("#") or face["texture"][1:] in textures, "Unbound element texture: " + reference)
            models_checked.add(reference)

        models = []
        for module in ("forest", "gear", "tether", "lift"):
            models.extend((ASSETS / "models/block" / module).glob("*.json"))
        items = {"venom_reed", "venom_fiber", "spore_pod_shell", "route_beacon", "ballast_belt", "protective_coating", "rift_winch", "tether_spool", "field_anchor", "lift_controller"}
        models.extend(ASSETS / "models/item" / (item + ".json") for item in items)
        for path in sorted(models):
            validate_model("interstice:" + path.relative_to(ASSETS / "models").with_suffix("").as_posix())
        blockstates = {}
        for block in ("venom_reed", "spore_pod", "route_beacon", "rift_winch", "field_anchor"):
            document = load(ASSETS / "blockstates" / (block + ".json"))
            applications = list(document.get("variants", {}).values()) + [part["apply"] for part in document.get("multipart", [])]
            check(applications, "Blockstate has no model: " + block)
            for application in applications:
                for chosen in application if isinstance(application, list) else [application]:
                    validate_model(chosen["model"])
            loot = load(RES / "data/interstice/loot_table/blocks" / (block + ".json"))
            check(loot.get("type") == "minecraft:block", "Block loot type invalid: " + block)
            blockstates[block] = len(applications)
        recipes = []
        for path in sorted((RES / "data/interstice/recipe").glob("retort_*.json")):
            recipe = load(path)
            check(recipe["type"] == "interstice:retort" and 1 <= len(recipe["inputs"]) <= 6 and 1 <= len(recipe["outputs"]) <= 3 and 20 <= recipe["ticks"] <= 32000, "Retort codec bounds invalid: " + path.stem)
            item_refs = [part["ingredient"]["item"] for part in recipe["inputs"]] + [part["id"] for part in recipe["outputs"]]
            check(all(1 <= part["count"] <= 64 for part in recipe["inputs"] + recipe["outputs"]), "Retort stack count invalid: " + path.stem)
            for reference in item_refs:
                namespace, name = reference.split(":", 1)
                validate_model(namespace + ":item/" + name)
            recipes.append({"id": "interstice:" + path.stem, "sha256": sha(path), "ticks": recipe["ticks"], "linked_items": sorted(set(item_refs)), "codec_and_resource_links_valid": True})
        check(RETORT_BASELINE <= {row["id"].split(":", 1)[1] for row in recipes}, "A baseline retort operation is missing")
    return {"passed": True, "source_registry_sha256": sha(SOURCES / "sources.json"), "provenance_sha256": sha(provenance_path), "license_copy_sha256": sha(license_path), "license_copy": license_path.relative_to(ROOT).as_posix(), "sources": source_rows, "native_whole_16x16_sprites_compared": vanilla_count, "source_comparison_transformations": len(transforms), "palette_count": len(palette_rows), "palettes": palette_rows, "model_count": len(models_checked), "checked_models": sorted(models_checked), "blockstate_applications": blockstates, "retort_recipe_count": len(recipes), "recipes": recipes}


def main():
    try:
        report = audit()
    except (OSError, KeyError, ValueError, zipfile.BadZipFile) as failure:
        report = {"passed": False, "error": str(failure)}
    target = ROOT / ".verification/forest-ecology-release-audit.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key in {"passed", "error", "palette_count", "model_count", "retort_recipe_count"}}, ensure_ascii=False))
    return not report["passed"]


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())
