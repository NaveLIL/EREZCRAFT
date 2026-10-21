"""Check optional V6 ore IDs and shared processing tags against actual, unchanged pack JARs."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/java/pro/erez/interstice/minerals/PackOreDepositsV6.java"
JARS = {
    "create": "create-1.21.1-6.0.10.jar",
    "mekanism": "Mekanism-1.21.1-10.7.19.85.jar",
    "modern_industrialization": "Modern-Industrialization-2.5.10.jar",
    "draconicevolution": "Draconic-Evolution-1.21.1-3.1.4.633.jar",
}


def audit(mods: Path) -> dict:
    rows = []
    for match in re.finditer(r'ore\("([a-z]+)",([^\n]+)\)', SOURCE.read_text(encoding="utf-8")):
        names = re.findall(r'"([a-z_]+:[a-z_]+)"', match.group(2))
        rows.append({"resource": match.group(1), "alternatives": names})
    assert len(rows) == 21 and len({row["resource"] for row in rows}) == 21, "Unexpected ore contract"
    checked = []
    artifacts = []
    for namespace, filename in JARS.items():
        path = mods / filename
        artifacts.append({"file": filename, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
        with zipfile.ZipFile(path) as jar:
            entries = set(jar.namelist())
            for row in rows:
                for identifier in row["alternatives"]:
                    owner, name = identifier.split(":", 1)
                    if owner != namespace:
                        continue
                    assert f"assets/{owner}/blockstates/{name}.json" in entries, f"Absent ore block {identifier}"
                    assert f"data/{owner}/loot_table/blocks/{name}.json" in entries, f"Absent ore loot {identifier}"
                    checked.append(identifier)
            if namespace in {"mekanism", "modern_industrialization"}:
                for resource in ("tin", "lead", "uranium"):
                    ore_tag = json.loads(jar.read(f"data/c/tags/item/ores/{resource}.json"))
                    raw_tag = json.loads(jar.read(f"data/c/tags/item/raw_materials/{resource}.json"))
                    assert f"{namespace}:{resource}_ore" in ore_tag["values"], "Missing shared ore processing tag"
                    assert f"{namespace}:raw_{resource}" in raw_tag["values"], "Missing shared raw processing tag"
    assert len(checked) == 17, "An optional ore was not checked in its actual owning JAR"
    forbidden = {"platinum", "iridium", "titanium", "certus", "coal"}
    assert not forbidden.intersection(row["resource"] for row in rows), "Unreviewed progression shortcut"
    return {
        "passed": True,
        "scope": "Read-only original installed JAR metadata and loot; not a native registry or Survival test",
        "dimension": "interstice:islands_v6",
        "logical_resources": rows,
        "optional_block_ids_checked": sorted(checked),
        "shared_processing_tags": [f"c:{kind}/{resource}" for kind in ("ores", "raw_materials") for resource in ("tin", "lead", "uranium")],
        "original_artifacts": artifacts,
        "source_sha256": hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
        "owner_worlds_used": False,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mods", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = audit(args.mods)
    serialized = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(serialized, encoding="utf-8")
    print(f"PASS: {len(report['logical_resources'])} resources; {len(report['optional_block_ids_checked'])} actual optional ore IDs; shared MI/Mekanism tags")


if __name__ == "__main__":
    main()
