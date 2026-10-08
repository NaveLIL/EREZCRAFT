"""Read-only agriculture asset, model-reference and recipe audit; JSON report on stdout."""
from pathlib import Path
from collections import Counter
import hashlib
import io
import json
import sys
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/interstice"
SOURCES = ROOT / "art/sources/cc0/agriculture"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    errors, warnings, palettes, recipes = [], [], [], []
    provenance = json.loads((ASSETS / "provenance/agriculture.json").read_text(encoding="utf-8"))
    declarations = json.loads((SOURCES / "sources.json").read_text(encoding="utf-8"))["sources"]
    for source in declarations:
        path = SOURCES / source["file"]
        if digest(path) != source["sha256"]:
            errors.append("Source SHA mismatch: " + source["file"])
        if source.get("archive"):
            archive = ROOT / source["archive"]
            if digest(archive) != source["archive_sha256"]:
                errors.append("Archive SHA mismatch: " + source["archive"])
            with zipfile.ZipFile(archive) as content:
                matches = [n for n in content.namelist() if not n.endswith("/") and hashlib.sha256(content.read(n)).hexdigest() == source["sha256"]]
                if not matches:
                    errors.append("Whole source not present in archive: " + source["file"])
                if not source.get("source_member"):
                    warnings.append({"source_member_missing": source["file"], "matching_archive_members": matches})
        elif source.get("source") and digest(ROOT / source["source"]) != source["sha256"]:
            errors.append("Copied source differs: " + source["file"])
        if source.get("license") != "CC0-1.0" or not source.get("source_url") or not source.get("author"):
            errors.append("Source license/origin declaration incomplete: " + source["file"])
    for entry in provenance["outputs"]:
        source_path = SOURCES / entry["source"]
        original = Image.open(source_path).convert("RGBA")
        output_path = ASSETS / "textures" / entry["output"]
        output = Image.open(output_path).convert("RGBA")
        forward, reverse = {}, {}
        ok = original.size == output.size and digest(output_path) == entry["output_sha256"] and digest(source_path) == entry["source_sha256"]
        for a, b in zip(original.get_flattened_data(), output.get_flattened_data()):
            ok &= a[3] == b[3] and (a not in forward or forward[a] == b) and (b not in reverse or reverse[b] == a)
            forward[a], reverse[b] = b, a
        ok &= len(forward) == entry["classes"]
        if not ok:
            errors.append("Palette/alpha/resolution/SHA changed: " + entry["output"])
        palettes.append({"file": entry["output"], "size": output.size, "source": entry["source"], "classes": len(forward), "alpha_bbox": output.getchannel("A").getbbox(), "bijective_palette_only": bool(ok)})
    minecraft = zipfile.ZipFile(ROOT / "build/moddev/artifacts/neoforge-21.1.252-client-extra-aka-minecraft-resources.jar")

    def resource(kind, ref):
        if ":" not in ref:
            ref = "minecraft:" + ref
        namespace, name = ref.split(":", 1)
        relative = f"assets/{namespace}/{kind}/{name}.json"
        if namespace == "minecraft":
            return json.loads(minecraft.read(relative))
        return json.loads((RES / relative).read_text(encoding="utf-8"))

    def model(ref, visiting=()):
        if ref in visiting:
            raise ValueError("Model cycle: " + ref)
        document = resource("models", ref)
        if document.get("parent") in ("builtin/generated", "builtin/entity"):
            inherited = {}
        elif document.get("parent"):
            inherited = model(document["parent"], visiting + (ref,))
        else:
            inherited = {}
        result = {**inherited, **document}
        result["textures"] = {**inherited.get("textures", {}), **document.get("textures", {})}
        return result

    model_paths = list((ASSETS / "models/block/agriculture").glob("*.json"))
    model_paths += [p for p in (ASSETS / "models/item").glob("*.json") if "agriculture/" in p.read_text(encoding="utf-8")]
    for path in model_paths:
        ref = "interstice:" + path.relative_to(ASSETS / "models").with_suffix("").as_posix()
        try:
            document = model(ref)
            textures = document.get("textures", {})
            for key in textures:
                value, seen = textures[key], set()
                while value.startswith("#"):
                    if value in seen:
                        raise ValueError("Texture variable cycle: " + value)
                    seen.add(value)
                    value = textures[value[1:]]
                if ":" not in value:
                    value = "minecraft:" + value
                ns, name = value.split(":", 1)
                target = f"assets/{ns}/textures/{name}.png"
                if ns == "minecraft":
                    minecraft.getinfo(target)
                elif not (RES / target).exists():
                    errors.append(f"Missing texture in {ref}: {value}")
            for part in document.get("elements", []):
                if any(a > b for a, b in zip(part["from"], part["to"])):
                    errors.append("Inverted element: " + ref)
                for face in part.get("faces", {}).values():
                    if face["texture"].startswith("#") and face["texture"][1:] not in textures:
                        errors.append("Unbound texture in " + ref + ": " + face["texture"])
        except (ValueError, KeyError, FileNotFoundError) as exc:
            errors.append(ref + ": " + str(exc))
    blocks = ("ash_grain_crop", "crimson_root_crop", "toxic_farmland", "reaction_retort", "nutrient_reservoir", "reinforced_fence", "reinforced_fence_gate", "bioluminescent_lantern")
    state_counts, loot_counts = {}, {}
    for block in blocks:
        document = json.loads((ASSETS / "blockstates" / (block+".json")).read_text(encoding="utf-8"))
        variants = list(document.get("variants", {}).values())
        variants += [part["apply"] for part in document.get("multipart", [])]
        applications = [a for v in variants for a in (v if isinstance(v,list) else [v])]
        for application in applications:
            try:
                model(application["model"])
            except (ValueError, KeyError, FileNotFoundError) as exc:
                errors.append("Blockstate " + block + ": " + str(exc))
        state_counts[block] = len(applications)
        loot = json.loads((RES / "data/interstice/loot_table/blocks" / (block+".json")).read_text(encoding="utf-8"))
        loot_counts[block] = len(loot["pools"])
        if loot.get("type") != "minecraft:block" or not loot_counts[block]:
            errors.append("Block loot pool missing: " + block)
    for path in (RES / "data/interstice/recipe").glob("*.json"):
        recipe = json.loads(path.read_text(encoding="utf-8"))
        if not (path.stem.startswith("retort_") or path.stem in {"reaction_retort", "reinforced_fence", "reinforced_fence_gate", "nutrient_reservoir", "bioluminescent_lantern", "slicing_cultivator", "mineral_fertilizer", "riftsilver_wire", "riftsilver_mesh"}):
            continue
        if recipe["type"] == "minecraft:crafting_shaped":
            pattern = recipe["pattern"]
            counts = Counter("".join(pattern).replace(" ", ""))
            ok = 1 <= len(pattern) <= 3 and all(1 <= len(row) <= 3 for row in pattern) and len({len(row) for row in pattern}) == 1 and set(counts) == set(recipe["key"])
            recipes.append({"name": path.stem, "slots": sum(counts.values()), "ingredients": {k: {"count": counts[k], "ingredient": recipe["key"][k]} for k in counts}, "valid_3x3": ok})
        elif recipe["type"] == "minecraft:crafting_shapeless":
            ok = 1 <= len(recipe["ingredients"]) <= 9
            recipes.append({"name": path.stem, "slots": len(recipe["ingredients"]), "valid_3x3": ok})
        else:
            ok = 1 <= len(recipe["inputs"]) <= 6 and 1 <= len(recipe["outputs"]) <= 3 and 20 <= recipe["ticks"] <= 32000 and all(1 <= part["count"] <= 64 for part in recipe["inputs"] + recipe["outputs"])
            recipes.append({"name": path.stem, "input_stacks": len(recipe["inputs"]), "output_stacks": len(recipe["outputs"]), "valid_retort_limits": ok})
        if not ok:
            errors.append("Recipe shape/codec limits invalid: " + path.stem)
    for part in ("fence_post", "fence_side", "fence_inventory", "gate_closed", "gate_open", "gate_wall", "gate_wall_open"):
        document = json.loads((ASSETS / "models/block/agriculture" / (part + ".json")).read_text(encoding="utf-8"))
        if not document.get("elements") and list(document.get("textures", {}).values()) == ["interstice:block/garden/paleheart_planks"]:
            warnings.append({"ordinary_wood_template_without_visible_reinforcement": part})
    for name in ("riftsilver_mesh", "riftsilver_wire"):
        entry = next(e for e in provenance["outputs"] if e["output"].endswith("/" + name + ".png"))
        item = json.loads((ASSETS / "models/item" / (name+".json")).read_text(encoding="utf-8"))
        if entry["source"] == "stick.png" and item.get("parent") == "minecraft:item/generated":
            warnings.append({"component_uses_straight_stick_silhouette": name})
    silver = Image.open(ASSETS / "textures/block/minerals/riftsilver.png").convert("RGBA")
    silver_uv_opaque = all(silver.getpixel((x,y))[3] == 255 for x in range(6,10) for y in range(7,9))
    if not silver_uv_opaque:
        errors.append("Silver lattice geometry UV region contains transparent pixels")
    lantern_bounds = {}
    for name in ("lantern_false", "lantern_true"):
        document = json.loads((ASSETS / "models/block/agriculture" / (name+".json")).read_text(encoding="utf-8"))
        bounds = [min(e["from"][1] for e in document["elements"]), max(e["to"][1] for e in document["elements"])]
        lantern_bounds[name] = bounds
        if bounds[0] < 0 or bounds[1] > 16:
            errors.append("Lantern geometry extends into adjacent blocks: " + name)
    silver_faces = 0
    for name in ("retort_false", "retort_true", "cultivator"):
        document = json.loads((ASSETS / "models/block/agriculture" / (name+".json")).read_text(encoding="utf-8"))
        for element in document["elements"]:
            for face in element["faces"].values():
                if face["texture"] in ("#silver", "#metal"):
                    silver_faces += 1
                    if face.get("uv") != [6,7,10,9]:
                        errors.append("Silver face uses unverified transparent UV region: " + name)
    crop_heights = {"grain":[16-p["alpha_bbox"][1] for p in palettes if "/grain_stage_" in p["file"]], "root":[16-p["alpha_bbox"][1] for p in palettes if "/root_stage_" in p["file"]]}
    if crop_heights != {"grain":[2,6,11,14,15],"root":[3,6,9,11]}:
        errors.append("Crop stage silhouette no longer matches current selection-outline heights")
    print(json.dumps({"passed": not errors, "errors": errors, "warnings": warnings, "source_count":len(declarations), "palette_count": len(palettes), "palettes": palettes, "models_checked": len(model_paths), "blockstate_applications":state_counts, "loot_pool_counts":loot_counts, "crop_sprite_heights":crop_heights, "silver_lattice_uv_opaque":silver_uv_opaque, "silver_retort_cultivator_faces":silver_faces, "lantern_vertical_bounds":lantern_bounds, "recipes": recipes}, ensure_ascii=False, indent=2))
    return bool(errors)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())
