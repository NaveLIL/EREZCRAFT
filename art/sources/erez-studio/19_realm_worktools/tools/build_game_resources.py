from pathlib import Path
import json,shutil
ROOT=Path(__file__).resolve().parents[1];CAND=Path('D:/repos/Minecraft/EREZCRAFT/.verification/wt01')
RES=CAND/'src/main/resources'
def tag(name,values):
    p=RES/'data/interstice/tags/block'/(name+'.json');p.write_text(json.dumps({'replace':False,'values':values},indent=2)+'\n',encoding='utf-8')
stone=['stone','deepslate','granite','diorite','andesite','tuff','calcite','netherrack','end_stone','blackstone','basalt','smooth_basalt']
tag('excavation_stone',['minecraft:'+n for n in stone]+['interstice:'+n for n in ['riftstone','rift_shale','vaultstone','palestone']])
tag('excavation_soil',['minecraft:'+n for n in ['dirt','grass_block','coarse_dirt','rooted_dirt','podzol','mycelium','sand','red_sand','gravel','clay','mud','snow_block']]+['#interstice:forest_floor'])
tag('felling_logs',['minecraft:'+n+'_log' for n in ['oak','spruce','birch','jungle','acacia','dark_oak','mangrove','cherry']]+['minecraft:crimson_stem','minecraft:warped_stem','interstice:gloomcrown_log','interstice:crown_log','interstice:paleheart_log'])
tag('area_tillable',['minecraft:'+n for n in ['grass_block','dirt','coarse_dirt','rooted_dirt','dirt_path']]+['#interstice:forest_floor'])
tag('riftsilver_resonant_materials',['#interstice:mineral_ores','#interstice:forest_floor','#interstice:crown_logs','#interstice:paleheart_logs',
    'interstice:gloomcrown_log','interstice:stripped_gloomcrown_log','interstice:gloomcrown_leaves','interstice:riftstone','interstice:rift_shale','interstice:vaultstone','interstice:palestone'])
ru={
'message.interstice.worktool.mode':'Режим: %s',
'worktool.interstice.single':'Одиночная работа',
'worktool.interstice.pickaxe':'Выработка 3×3',
'worktool.interstice.axe':'Валка — до 16 брёвен',
'worktool.interstice.shovel':'Расчистка 3×3',
'worktool.interstice.hoe':'Грядка 3×3',
'tooltip.interstice.worktool.mode':'Режим: %s',
'tooltip.interstice.worktool.switch':'Shift + ПКМ по воздуху: сменить режим. Присесть: временно одиночная работа.',
'tooltip.interstice.worktool.pickaxe':'Обрабатывает камень перед собой. Руды и блоки с содержимым не затрагивает. Прочность — за каждый блок.',
'tooltip.interstice.worktool.axe':'Связанные брёвна одной породы от места сруба вверх, до 16 за раз. Прочность — за каждое бревно.',
'tooltip.interstice.worktool.shovel':'Очищает участок 3×3 по выбранной грани. Прочность — за каждый блок.',
'tooltip.interstice.worktool.hoe':'ПКМ по земле: обработать 3×3, включая местную почву. Прочность — за каждый блок.',
'tooltip.interstice.worktool.resonance':'Резонанс: +25% к базовой скорости по подходящим местным природным материалам. Уровень добычи не повышает.'}
en={
'message.interstice.worktool.mode':'Mode: %s','worktool.interstice.single':'Single block','worktool.interstice.pickaxe':'Excavation 3×3','worktool.interstice.axe':'Felling — up to 16 logs',
'worktool.interstice.shovel':'Clearance 3×3','worktool.interstice.hoe':'Tilling 3×3','tooltip.interstice.worktool.mode':'Mode: %s',
'tooltip.interstice.worktool.switch':'Sneak + use in air: change mode. Sneak while working: temporarily single block.',
'tooltip.interstice.worktool.pickaxe':'Mines a stone plane in front of you. Skips ores and block entities. Durability per block.',
'tooltip.interstice.worktool.axe':'Connected logs of the same type from the cut upward, up to 16 per cut. Durability per log.',
'tooltip.interstice.worktool.shovel':'Clears a 3×3 plane along the selected face. Durability per block.',
'tooltip.interstice.worktool.hoe':'Use on soil: till 3×3, including native soil. Durability per block.',
'tooltip.interstice.worktool.resonance':'Resonance: +25% base mining speed on suitable native natural materials. Does not raise harvest tier.'}
for lang,new in [('ru_ru',ru),('en_us',en)]:
    p=RES/'assets/interstice/lang'/(lang+'.json');data=json.loads(p.read_text(encoding='utf-8'));data.update(new);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
shutil.copytree(ROOT/'exports/assets',RES/'assets',dirs_exist_ok=True)
# Keep the existing resource generator from reverting custom worktool models.
p=CAND/'tools/build_expedition_progression.py';s=p.read_text(encoding='utf-8')
needle='def tool_model('
if needle in s:
    print('Inspect generator before rerunning it; candidate generation does not invoke it.')
print('WT03 assets, tag allowlists, bilingual controls prepared')
