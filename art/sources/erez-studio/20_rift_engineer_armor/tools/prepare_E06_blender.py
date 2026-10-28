from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
s=(ROOT/'tools/build_blender.py').read_text(encoding='utf-8').replace('E04','E06')
start=s.index('# Reuse the preserved S03 cloth texels');end=s.index('ATLAS=1024;',start)
s=s[:start]+'''# The owner requested a richer pixel fabric; this is the actual generated production material.
cloth_path=ROOT/'source/materials/fabric_E06_pixels32.png';cloth=Image.open(cloth_path).convert('RGBA');refs['owner_fabric']=cloth
provenance.append({'asset':'Owner-directed charcoal pixel fabric','file':str(cloth_path),'sha256':sha(cloth_path.read_bytes()),'generation':'Built-in image_gen; see materials/generation.json'})
tiles={}
for role,hexcolor in data['palette'].items():
    if role in ('coat','panel','seam','collar'):
        src=cloth;dst=src.copy();factor={'coat':1.0,'panel':1.10,'seam':.85,'collar':.78}[role]
        dst.putdata([tuple(min(255,round(c*factor)) for c in (r,g,b))+(a,) for r,g,b,a in src.getdata()]);original=cloth_path
        operation='Generated pixel fabric, exact layout; material brightness variant'
    else:
        name='black_wool' if role in ('dark','black','undercloth') else 'brown_wool' if role in ('lining','leather') else 'iron_block' if role in ('metal','steel','brass','mask_plate') else 'white_wool'
        src=refs[name];base=tuple(int(hexcolor[i:i+2],16) for i in (1,3,5));lum=[.2126*r+.7152*g+.0722*b for r,g,b,a in src.getdata()];mean=sum(lum)/len(lum);dst=src.copy();values=[]
        for (r,g,b,a),L in zip(src.getdata(),lum):
            strength=max(.50,min(1.55,L/max(mean,1)))
            if role in ('light','cyan'):strength=.9+.1*strength
            elif role in ('dark','black','undercloth'):strength=.8+.2*strength
            elif role=='leather':strength=.35+.65*strength
            values.append(tuple(min(255,round(c*strength)) for c in base)+(a,))
        dst.putdata(values);original=CAT/(name+'.png');operation='Pointwise luminance palette only; source coordinates and alpha retained'
    tiles[role]=dst;derived=ROOT/'source'/('palette_'+role+'.png');dst.save(derived)
    provenance.append({'role':role,'original':str(original),'derived':derived.name,'sha256':sha(derived.read_bytes()),'operation':operation,'tint':hexcolor})

'''+s[end:]
needle="vertices=[list(rotation@v.co+pivot) for v in m.vertices];polygons=[]"
replacement="""vertices=[list(rotation@v.co+pivot) for v in m.vertices];polygons=[]
    if 'mesh_transform' in p:
        transform=Matrix(p['mesh_transform']['matrix']);translation=Vector(p['mesh_transform']['translation']);vertices=[list(transform@Vector(v)+translation) for v in vertices]"""
assert needle in s;s=s.replace(needle,replacement)
s=s.replace("dims=[max(hi[i]-lo[i],.01) for i in (0,1)];w,h=[max(1,math.ceil(d*4)) for d in dims]", "dims=[max(hi[i]-lo[i],.01) for i in (0,1)];density=2 if p['role'] in ('coat','panel','seam','collar') else 4;w,h=[max(1,math.ceil(d*density)) for d in dims]")
(ROOT/'tools/build_blender_E06.py').write_text(s,encoding='utf-8')
print('E06 Blender authoring script prepared')
