"""Record a design proposal and render a schematic. No game/world mutation."""
from pathlib import Path
import hashlib,json,sys,textwrap
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'.verification/marine-design-audit/python-deps'))
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import Polygon,Rectangle

OUT=ROOT/'art/designs/marine-expedition'
OUT.mkdir(parents=True,exist_ok=True)
def room(name,x,w,y=0,h=4.8,kind='room'):
    return dict(name=name,x=x,width=w,y=y,height=h,kind=kind)
decks=[
 dict(id='fly',name='ОБЗОРНАЯ ПАЛУБА',rooms=[room('Зона отдыха под навесом',9,7.5,0,4.6),room('Второй пост',17,3,0,4)],terminals=[]),
 dict(id='bridge',name='ВЕРХНЯЯ ПАЛУБА',rooms=[room('Открытая площадка',2,4,0,6,'outside'),room('Общая зона отдыха',7,6.7),room('Рабочий стол',14,2.8,-1.35,2),room('Лестница',14,2.8,1.35,2,'stair'),room('Закрытый мостик',17.2,4.8)],terminals=[dict(id='M1',x=20,y=0)]),
 dict(id='main',name='ГЛАВНАЯ ПАЛУБА',rooms=[room('Кормовая площадка',.8,3,0,5.8,'outside'),room('Тамбур',4.1,2.0,-1.5,2.2),room('Мастерская',6.4,3.7,-1.65,2.0),room('Лестница',6.4,3.7,1.65,2.0,'stair'),room('Салон / столовая',10.5,6.6),room('Камбуз',17.5,3,-1.4,2.2),room('Лестница',17.5,3,1.4,2.2,'stair'),room('Главная каюта',21,4.3,0,3.1)],terminals=[dict(id='M2',x=5,y=-1.5),dict(id='M3',x=8.2,y=-1.65),dict(id='M4',x=19,y=-1.4)]),
 dict(id='lower',name='НИЖНЯЯ ПАЛУБА',rooms=[room('Каюта · 2 места',1,4,-1.8,2.2),room('Каюта · 2 места',1,4,1.8,2.2),room('Машинный отсек',5.5,7.2,0,5.6,'machinery'),room('МЕ-аппаратная',13.1,3,-1.8,2.3,'me'),room('Санузлы',13.1,3,1.8,2.3),room('Каюта · 2 места',16.5,5.5,-1.8,2.3),room('Каюта · 2 места',16.5,5.5,1.8,2.3),room('Выход наверх',22.5,2.5,0,2,'stair')],terminals=[dict(id='M5',x=18.2,y=0),dict(id='M6',x=14.6,y=-1.8)])]
sources=[
 dict(id='N96',url='https://nordhavn.com/nordhavn-yacht-models/n96/',scope='Published nominal dimensions and vessel identity'),
 dict(id='N96-GA',url='https://nordhavn.com/wp-content/uploads/2019/08/N96-GA-drawing.pdf',scope='Visually reviewed general arrangement; own schematic is an adaptation'),
 dict(id='N96-PROFILE',url='https://nordhavn.com/wp-content/uploads/2019/08/N96-profile-drawing.pdf',scope='Visually reviewed external profile'),
 dict(id='AE2-TERMINALS',url='https://guide.appliedenergistics.org/1.21.1/items-blocks-machines/terminals/',scope='Native network access parts'),
 dict(id='AE2-CHANNELS',url='https://guide.appliedenergistics.org/1.21.1/ae2-mechanics/channels/',scope='Default 8/32 cable capacities'),
 dict(id='SABLE-1222',url='https://github.com/ryanhcode/sable/issues/1222',scope='Reported save crash on older 2.0.3/19.2.17; current pair not tested'),
 dict(id='SABLE-1396',url='https://github.com/ryanhcode/sable/pull/1396',scope='Open proposed fix, not assumed to be in installed Sable'),
 dict(id='SABLE-RELEASES',url='https://github.com/ryanhcode/sable/releases',scope='2.0.6 notes; absence of fix text is not proof of a current bug')]
concept=dict(id='NE01',revision='P01',date='2026-10-29',status='preliminary_design_not_3d_model_or_runtime',
 prototype=dict(name='Nordhavn 96',length_m=29.44,beam_m=7.32,published_draft_m=2.49,units='1 metre = 1 Minecraft block',draft_is_not_game_tuning=True),
 intended_berths=10,terminals=6,storage='one genuine AE2 network; no mirrored inventories or cargo chests',
 requirements=dict(clear_door_width_m=1,clear_door_height_m=2.1,corridor_width_m=1.2,side_walkway_width_m=1,stair_width_m=1.2,route_headroom_m=2.2),
 requirements_are_targets_not_verified_geometry=True,
 me=dict(controller=1,initial_drives=2,expansion_drives=4,dense_backbone_default_channels=32,independent_house_supply=True,installed_moving_network_tested=False),
 propulsion=dict(existing_skat='air propellers',proposed='two submerged propellers, rudder and docking thrusters',implemented=False),
 schematic_coordinates='x stern to bow; y across beam; zone allocation only, no detailed furniture/collision or stair tread geometry',
 decks=decks,sources=sources,implementation_stage='first separate powered AE2 + Sable saving/movement proof, then walkable greybox, then detailed exterior and systems')
(OUT/'NE01.json').write_text(json.dumps(concept,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

plt.rcParams.update({'font.family':'DejaVu Sans','font.size':11})
fig,axes=plt.subplots(4,1,figsize=(14,16),layout='constrained')
fig.patch.set_facecolor('#f5f7f8')
palette={'room':'#e1e8eb','outside':'#f2efe6','stair':'#dce3ed','machinery':'#d1d8dc','me':'#c7e8e6'}
hull=[(0,-3),(3,-3.66),(20.5,-3.66),(25,-2.65),(28,-1.05),(29.44,0),(28,1.05),(25,2.65),(20.5,3.66),(3,3.66),(0,3)]
for ax,d in zip(axes,decks):
    ax.set_facecolor('#f5f7f8');ax.add_patch(Polygon(hull,closed=True,facecolor='#fafbfc',edgecolor='#536875',linewidth=1.6))
    for a in d['rooms']:
        ax.add_patch(Rectangle((a['x'],a['y']-a['height']/2),a['width'],a['height'],facecolor=palette[a['kind']],edgecolor='#a3b2bb',linewidth=.7))
        name=textwrap.fill(a['name'],width=max(9,int(a['width']*3)),break_long_words=False)
        if name=='МЕ-аппаратная':name='МЕ-\nаппаратная'
        has_terminal=any(a['x']<=t['x']<=a['x']+a['width'] and abs(t['y']-a['y'])<a['height']/2 for t in d['terminals'])
        ax.text(a['x']+a['width']/2,a['y']+(.55 if has_terminal else 0),name,ha='center',va='center',color='#253944',fontsize=10)
    for t in d['terminals']:
        ax.plot(t['x'],t['y'],'s',color='#197e7c',markersize=9)
        ax.annotate(t['id'],(t['x'],t['y']),xytext=(0,-20),textcoords='offset points',ha='center',color='#125f5d',fontweight='bold')
    if d['id']=='main':
        for s in [-1,1]:
            ax.plot([3.8,22.4],[s*3.13,s*3.13],color='#29928c',linewidth=2)
            ax.annotate('Трап',xy=(5,s*3.7),xytext=(5,s*4.4),ha='center',va='center',arrowprops=dict(arrowstyle='->',color='#29928c'),color='#125f5d')
    if d['id']=='lower':
        ax.plot([16.3,23.6],[0,0],color='#29928c',linewidth=2,alpha=.5)
        ax.annotate('Кормовой выход',xy=(4.8,.4),xytext=(3,4.4),ha='center',arrowprops=dict(arrowstyle='->',color='#536875'),color='#536875')
    ax.set_xlim(-.8,30.2);ax.set_ylim(-4.9,4.9);ax.set_aspect('equal');ax.axis('off')
    ax.set_title(d['name'],loc='left',pad=16,color='#253944',fontweight='bold',fontsize=12)
    ax.text(29.7,-4.45,'НОС →',ha='right',va='center',color='#536875')
fig.suptitle('NE01 · Экспедиционное судно на основе Nordhavn 96',fontsize=19,color='#253944')
fig.text(.5,.003,'Предварительное распределение зон · M1–M6: одна общая МЕ-сеть\nНе заводской чертёж · проходы и мебель требуют 3D-проверки',ha='center',color='#536875',fontsize=10)
fig.savefig(OUT/'NE01-layout.png',dpi=150);fig.savefig(OUT/'NE01-layout.svg');plt.close(fig)

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
mods=Path('D:/EREZCRAFT/data/stable/instances/archipelago/mods')
paths=[ROOT/p for p in ['docs/SKAT_SK02.md','docs/SKAT_SK03.md','docs/verification/SKAT_SK03.json','src/main/java/pro/erez/interstice/ship/SkatCargoEntity.java','src/main/java/pro/erez/interstice/ship/SkatPropellerEntity.java','src/main/java/pro/erez/interstice/ocean/ToxinHullOcclusion.java','src/main/java/pro/erez/interstice/ocean/HullSeal.java']]
paths += [mods/'appliedenergistics2-19.2.18.jar',mods/'sable-neoforge-1.21.1-2.0.6.jar']
paths += list((ROOT/'research/marine-expedition').glob('*.pdf'))
audit=dict(status='read_only_feasibility_audit',date='2026-10-29',inputs={str(p):dict(bytes=p.stat().st_size,sha256=sha(p)) for p in paths},
 findings=dict(installed_ae2='19.2.18',installed_sable='2.0.6',ae2_moving_save_tested=False,existing_ship_uses_air_propellers=True,thin_hull_high_resolution_is_skat_type_specific=True,schematic_is_not_walkability_proof=True),
 sources=sources,live_game_started=False,server_changed=False,owner_profiles_modified=False)
audit_path=ROOT/'.verification/marine-design-audit/audit.json';audit_path.parent.mkdir(parents=True,exist_ok=True);audit_path.write_text(json.dumps(audit,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps(dict(concept=str(OUT/'NE01.json'),plan=str(OUT/'NE01-layout.png'),terminal_count=sum(len(d['terminals']) for d in decks),audit=str(audit_path))))
