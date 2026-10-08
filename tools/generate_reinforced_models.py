"""Generate a reviewable silver lattice using existing PNGs unchanged.

Default destination is an isolated proposal. Pass --output src/main/resources/assets/interstice/models
only after reviewing; run after generate_agriculture_resources.py when accepted.
"""
from pathlib import Path
import argparse
import copy
import json
import zipfile

ROOT = Path(__file__).resolve().parents[1]
WOOD = "interstice:block/garden/paleheart_planks"
SILVER = "interstice:block/minerals/riftsilver"
FACES = ("north", "south", "east", "west", "up", "down")


def box(fr, to, material, silver=False):
    # Geometry UVs sample an opaque inner region of the existing ready nugget sprite.
    # No source or runtime bitmap is cropped, changed, or generated here.
    face = {"texture": material}
    if silver:
        face["uv"] = [6, 7, 10, 9]
    return {"from": fr, "to": to, "faces": {side: dict(face) for side in FACES}}


def model(parts, display=None):
    result = {"parent": "minecraft:block/block", "ambientocclusion": True,
              "textures": {"particle": WOOD, "texture": WOOD, "silver": SILVER}, "elements": parts}
    if display is not None:
        result["display"] = display
    return result


def longitudinal_mesh(x, z0, z1, y0=7, y1=13):
    parts = [box([x-.2, y-.2, z0], [x+.2, y+.2, z1], "#silver", True)
             for y in sorted(set([y1] + list(range(int(y0), int(y1)+1, 2))))]
    for z in range(int(z0)+1, int(z1), 2):
        parts.append(box([x-.2, y0, z-.2], [x+.2, y1, z+.2], "#silver", True))
    return parts


def transverse_mesh(x0, x1, y0, y1, z):
    parts = [box([x0, y-.2, z-.2], [x1, y+.2, z+.2], "#silver", True)
             for y in (y0, y0+2, y1)]
    for x in range(int(x0)+1, int(x1), 2):
        parts.append(box([x-.2, y0, z-.2], [x+.2, y1, z+.2], "#silver", True))
    return parts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / ".verification/agriculture-reinforcement-proposal/models")
    args = parser.parse_args()
    destination = args.output.resolve()
    generated = {}
    post = [box([6,0,6],[10,16,10],"#texture")]
    post += [box([5.9,y,5.9],[10.1,y+.5,10.1],"#silver", True) for y in (5.5,13)]
    generated["block/agriculture/fence_post"] = model(post)
    side = [box([7,5.5,0],[9,7,8],"#texture"),box([7,13,0],[9,14.5,8],"#texture")]
    generated["block/agriculture/fence_side"] = model(side + longitudinal_mesh(8,0,6))
    inventory = [box([6,0,0],[10,16,4],"#texture"),box([6,0,12],[10,16,16],"#texture")]
    inventory += [box([7,y,-2],[9,y+1.5,18],"#texture") for y in (5.5,13)]
    inventory += longitudinal_mesh(8,4,12)
    generated["block/agriculture/fence_inventory"] = model(inventory)
    resources = zipfile.ZipFile(ROOT / "build/moddev/artifacts/neoforge-21.1.252-client-extra-aka-minecraft-resources.jar")
    for name, vanilla in (("closed","fence_gate"),("open","fence_gate_open"),("wall","fence_gate_wall"),("wall_open","fence_gate_wall_open")):
        source = json.loads(resources.read(f"assets/minecraft/models/block/template_{vanilla}.json"))
        parts = copy.deepcopy(source["elements"])
        shift = -3 if name.startswith("wall") else 0
        if name.endswith("open"):
            parts += longitudinal_mesh(1,9,13,9+shift,12+shift)
            parts += longitudinal_mesh(15,9,13,9+shift,12+shift)
        else:
            parts += transverse_mesh(2,6,9+shift,12+shift,8)
            parts += transverse_mesh(10,14,9+shift,12+shift,8)
        # Copy vanilla geometry only; textures refer exclusively to the project's own assets.
        generated["block/agriculture/gate_"+name] = model(parts)
    mesh = []
    for y in (2,5,8,11,14):
        mesh.append(box([2,y-.3,7.7],[14,y+.3,8.3],"#silver",True))
    for x in (2,5,8,11,14):
        mesh.append(box([x-.3,2,7.7],[x+.3,14,8.3],"#silver",True))
    generated["block/agriculture/silver_mesh_component"] = model(mesh, {
        "gui":{"rotation":[0,0,0],"translation":[0,0,0],"scale":[.9,.9,.9]},
        "ground":{"rotation":[0,0,0],"translation":[0,2,0],"scale":[.5,.5,.5]},
        "fixed":{"rotation":[0,0,0],"translation":[0,0,0],"scale":[.8,.8,.8]},
        "firstperson_righthand":{"rotation":[0,-90,25],"translation":[1,4,1],"scale":[.7,.7,.7]}})
    generated["item/riftsilver_mesh"] = {"parent":"interstice:block/agriculture/silver_mesh_component"}
    for name, content in generated.items():
        path = destination / (name + ".json")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(content,indent=2)+"\n",encoding="utf-8")
    print(f"Wrote {len(generated)} geometry-only review models to {destination}")


if __name__ == "__main__":
    main()
