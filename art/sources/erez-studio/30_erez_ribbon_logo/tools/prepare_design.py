from pathlib import Path
import json,hashlib
ROOT=Path(__file__).resolve().parents[1]
# Pixel-space tracing controls refer to the supplied 1280px logo, not a font.
# Depth is independently authored; negative Y is toward the viewer.
knots=[
 [480,242,.16,0],[830,242,.16,0],[892,266,.03,8],[912,319,-.18,15],
 [875,378,-.46,8],[802,388,-.58,0],[664,325,-.58,0],[536,252,-.52,0],
 [469,241,-.38,0],[396,292,-.25,0],[351,391,-.21,0],[371,486,-.18,0],
 [468,568,-.03,0],[626,637,.13,0],[774,699,.18,0],[850,703,.14,0],
 [894,661,-.08,10],[893,610,-.29,16],[852,567,-.5,8],[786,560,-.58,0],
 [658,594,-.62,0],[515,653,-.62,0],[405,734,-.55,0],[353,841,-.39,0],
 [352,940,-.26,0],[402,1013,-.32,0],[477,1031,-.43,0],[584,982,-.57,0],
 [712,914,-.62,0],[801,886,-.53,0],[860,913,-.35,8],[901,964,-.14,18],
 [892,1016,.06,8],[842,1035,.16,0],[766,1035,.18,0],[648,1035,.18,0],
 [531,1035,.18,0],[452,1014,.18,0],[411,975,.18,0]]
data={'revision':'LG01','name':'EREZ / purple ribbon emblem',
 'geometry_authority':'source/ribbon_LG01.json','reference':'references/owner_logo.jpg',
 'coordinates':'Authoring frame: X right, -Y front, Z up. Knots use reference image px; conversion is (px-640)/120, (640-py)/120.',
 'knots_px_depth_twist':knots,'pixels_per_art_unit':120,'image_center_px':[640,640],
 'ribbon_width':.72,'ribbon_thickness':.075,'ribbon_crown':0,
 'samples_per_span':9,'width_segments':4,'end_taper_length':.16,
 'disc_radius':5.17,'disc_depth':.32,'disc_front_y':.49,
 'palette':{'ribbon_front':'#8D43A6','ribbon_back':'#54266D','ribbon_edge':'#B676CA','disc':'#191A1C','rim':'#303135'},
 'game_export':{'format':'GLB','wall_badge_diameter_blocks':2,'geometry_ready_only':True,'registered_in_minecraft':False},
 'requirements':['Trace supplied purple ribbon logo rather than replace with a font','Three-dimensional ribbon folds and foreground/background crossings','Dark round base','Preserve existing branding and previous authored projects']}
(ROOT/'source/ribbon_LG01.json').write_text(json.dumps(data,indent=2)+'\n')
old=Path('D:/repos/Minecraft/EREZCRAFT/art/branding/v1')
preserved={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in old.iterdir() if p.is_file()}
(ROOT/'reports/BRANDING_BASELINE.json').write_text(json.dumps(preserved,indent=2)+'\n')
print(json.dumps({'revision':'LG01','knots':len(knots),'original_brand_files':len(preserved)}))
