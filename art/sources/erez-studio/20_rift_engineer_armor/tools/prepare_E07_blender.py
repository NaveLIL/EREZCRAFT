from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
s=(ROOT/'tools/build_blender_E06.py').read_text(encoding='utf-8').replace('E06','E07')
s=s.replace('fabric_E07_pixels32.png','fabric_E06_pixels32.png')
needle="    else:\n        name='black_wool'"
replacement="""    elif role in ('worn_metal','worn_leather','rubber','back_patch'):
        material={'worn_metal':'metal','worn_leather':'leather','rubber':'rubber','back_patch':'rubber'}[role]
        original=ROOT/'source/materials'/('E07_'+material+'_pixels32.png');src=Image.open(original).convert('RGBA');dst=src.copy()
        if role=='back_patch':dst.putdata([tuple(round(c*.80) for c in (r,g,b))+(a,) for r,g,b,a in src.getdata()])
        operation='Generated flat pixel material; original texel layout preserved; see materials/generation_E07.json'
    else:
        name='black_wool'"""
assert needle in s;s=s.replace(needle,replacement)
s=s.replace("('palette_'+role+'.png')","('palette_E07_'+role+'.png')")
s=s.replace("(ROOT/'reports/blender.json').write_text", "(ROOT/'reports/blender_E07.json').write_text")
s=s.replace("'game_implemented':False", "'game_implemented':False") # Native proof is separately revision-bound.
(ROOT/'tools/build_blender_E07.py').write_text(s,encoding='utf-8')
print('E07 Blender authoring prepared')
