from pathlib import Path
from PIL import Image
import numpy as np,json,hashlib
ROOT=Path(__file__).resolve().parents[1]
a=np.array(Image.open(ROOT/'preview/OC01_patch_control.png').convert('RGB')).astype(np.int16)
b=np.array(Image.open(ROOT/'preview/OC01_single_control.png').convert('RGB')).astype(np.int16)
diff=np.abs(a-b);maximum=int(diff.max());mean=float(diff.mean())
assert maximum<=2 and mean<.01,(maximum,mean)
end=np.array(Image.open(ROOT/'preview/OC01_loop_end_control.png').convert('RGB')).astype(np.int16)
loop_diff=np.abs(end-b);assert int(loop_diff.max())<=2 and float(loop_diff.mean())<.01
frames=[Image.open(ROOT/f'preview/loop_{i:02d}.png').convert('RGB') for i in range(16)]
assert all(frame.size==(640,640) for frame in frames)
assert np.abs(np.array(frames[4]).astype(float)-np.array(frames[0]).astype(float)).mean()>1
frames[0].save(ROOT/'preview/OC01_continuous_motion.gif',save_all=True,append_images=frames[1:],duration=1000,loop=0,optimize=False)
report={'revision':'OC01','method':'Same material, world position and orthographic camera: 64 independent mesh patches versus one unsplit plane',
    'comparison_size':[1024,1024],'world_window_blocks':[64,64],'control_density_pixels_per_block':16,
    'max_channel_difference_0_255':maximum,'mean_channel_difference_0_255':mean,'pixel_identical':maximum==0,
    'independent_patches_and_single_plane_agree':True,'spatial_and_temporal_math':'reports/CONTINUITY.json',
    'render_loop_end_max_channel_error_0_255':int(loop_diff.max()),'render_loop_end_mean_channel_error_0_255':float(loop_diff.mean()),
    'loop_preview_frames':16,'loop_seconds':16,'proof_scope':'Blender material only; no Minecraft, DH, Iris or network verification'}
(ROOT/'reports/RENDER_CONTINUITY.json').write_text(json.dumps(report,indent=2)+'\n')
files=['references/lower_ocean_OC01_concept.png','references/imagegen_prompt.txt','source/material_OC01.json','assembly/lower_ocean_OC01.blend',
       'preview/OC01_native_material.png','preview/OC01_patch_control.png','preview/OC01_single_control.png','preview/OC01_continuous_motion.gif',
       'reports/CONTINUITY.json','reports/RENDER_CONTINUITY.json','reports/NATIVE_BUILD.json','README.md']
artifacts={'revision':'OC01','imagegen_mode':'built-in','prompt':'references/imagegen_prompt.txt','game_files_modified':False,
    'artifacts':[{'file':f,'bytes':(ROOT/f).stat().st_size,'sha256':hashlib.sha256((ROOT/f).read_bytes()).hexdigest()} for f in files]}
(ROOT/'reports/ARTIFACTS.json').write_text(json.dumps(artifacts,indent=2)+'\n');print(json.dumps(report))
