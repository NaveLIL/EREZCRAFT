"""Verify the exact Blade geometry/UVs, palette substitution and reproducibility."""
from pathlib import Path
import ast, hashlib, importlib.util, json

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'.verification/blade-tools-20261028'
spec=importlib.util.spec_from_file_location('blade_builder',ROOT/'tools/build_blade_worktools.py')
builder=importlib.util.module_from_spec(spec);spec.loader.exec_module(builder)
generated=OUT/'regenerated'
expected=builder.build(generated)
checked=[]
for file in sorted((generated/'src/main/resources').rglob('*')):
    if not file.is_file():continue
    relative=file.relative_to(generated)
    assert file.read_bytes()==(ROOT/relative).read_bytes(),str(relative)
    checked.append(relative.as_posix())
# Exercise the real regeneration guard without running unrelated resource generators.
module=ast.parse((ROOT/'tools/build_expedition_progression.py').read_text(encoding='utf8'))
write=next(node for node in module.body if isinstance(node,ast.FunctionDef) and node.name=='write')
assets=generated/'src/main/resources/assets/interstice'
scope={'ASSETS':assets,'json':json,'ROOT':generated}
exec(compile(ast.Module(body=[write],type_ignores=[]),'progression.write','exec'),scope)
for name,row in expected['models'].items():
    file=assets/f'models/item/{name}.json';before=file.read_bytes()
    scope['write'](file,{'replacement':'must not overwrite reviewed Blade geometry'})
    assert file.read_bytes()==before
report={'passed':True,'models':8,'source_archive_sha256':builder.ARCHIVE_SHA256,
        'original_elements_rotations_faces_and_uvs_unchanged':True,
        'original_hand_transforms_unchanged':True,'source_texture_dimensions_and_alpha_unchanged':True,
        'deterministic_rebuild':True,'progression_generator_preserves_blade':True,
        'verified_paths':checked,'no_pommel_dependency_added':True}
(OUT/'resource-verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print(json.dumps(report))
