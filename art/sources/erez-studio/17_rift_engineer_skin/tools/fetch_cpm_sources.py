from pathlib import Path
import json,urllib.request,concurrent.futures
CAT=Path(__file__).resolve().parents[3]/'catalog/custom_player_models'
tree=json.loads((CAT/'source-tree.json').read_text())
keys=['editor/project/','editor/elements/ModelElement.java','editor/Exporter.java','editor/AnimationExporter.java','editor/anim/EditorAnim.java','animation/VanillaPose.java','animation/AnimationType.java','model/PlayerModelParts.java','io/ModelFile.java','parts/ModelPartPlayer.java','parts/ModelPartCollection.java','editor/Editor.java','editor/anim/AnimFrame.java','editor/anim/FrameData.java','editor/EditorEffect.java','editor/util/EditorUtil.java']
paths=[x['path'] for x in tree['tree'] if x['path'].endswith('.java') and x['path'].startswith('CustomPlayerModels/src/shared/java/') and any(k in x['path'] for k in keys)]
def get(path):
    target=CAT/'upstream'/path
    if not target.exists():
        data=urllib.request.urlopen('https://raw.githubusercontent.com/tom5454/CustomPlayerModels/'+tree['sha']+'/'+path,timeout=45).read()
        target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
    return target.name
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool: result=list(pool.map(get,paths))
print(len(result),'source files at',tree['sha'])
