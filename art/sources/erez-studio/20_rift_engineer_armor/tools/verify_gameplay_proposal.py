from pathlib import Path
from collections import Counter
import json,hashlib,datetime
ROOT=Path(__file__).resolve().parents[1];CAND=Path('D:/repos/Minecraft/EREZCRAFT/.verification/ea01')
d=json.loads((ROOT/'source/engineer_gameplay_proposal.json').read_text(encoding='utf-8'))
assert d['status']=='DESIGN_PROPOSAL_NOT_IMPLEMENTED'
armor=d['proposed_balance']['armor'];assert sum(armor[s] for s in ('helmet','chestplate','leggings','boots'))==armor['total']==22
assert d['proposed_balance']['toughness_per_piece']*4==d['proposed_balance']['toughness_total']==14
assert d['hazards']['native_spider_bite_and_spit_reduction_per_piece']*4==d['hazards']['native_spider_bite_and_spit_full_set_reduction']
recipes=d['proposed_recipes'];counts=Counter()
for name,n in [('engineer_assembly_pack',4),('engineer_neuroadapter',1),('engineer_power_cell',1),('engineer_breathing_cassette',1)]:
    recipe=recipes['interstice:'+name];assert sum(recipe['ingredients'].values())<=9
    for item,count in recipe['ingredients'].items():
        namespace,path=item.split(':')
        if namespace=='interstice':assert (CAND/'src/main/resources/assets/interstice/models/item'/(path+'.json')).exists(),item
        counts[item]+=count*n
counts['interstice:riftsteel_ingot']+=3
assert counts['interstice:riftsteel_ingot']==12 and counts['interstice:chemotrophic_fabric']==13 and counts['interstice:pure_vitriolite_lining']==9
assert counts['interstice:etched_circuit_board']==2 and counts['interstice:riftsilver_wire']==3 and counts['minecraft:redstone']==4
assert counts['minecraft:copper_ingot']==6 and counts['interstice:phosphorite_paste']==3
assert not set(d['progression']['not_required']) & {s.split(':')[-1] for s in counts}
power=d['neurointerface']['power'];assert power['capacity']/power['hud_drain_per_second']==1800 and power['capacity']/power['hud_and_night_vision_total_drain_per_second']==900
harvest=power['realm_harvesting'];ambient=harvest['ambient_units_per_second'];maximum=harvest['maximum_total_units_per_second']
assert power['automatic_realm_power'] and ambient+harvest['exposed_surge_bonus_max_units_per_second']==maximum==3
assert power['capacity']/(power['hud_drain_per_second']-ambient)==3600
assert power['capacity']/(power['hud_and_night_vision_total_drain_per_second']-ambient)==1200
assert power['capacity']/ambient==3600
assert maximum-power['hud_drain_per_second']==2 and maximum-power['hud_and_night_vision_total_drain_per_second']==1
breathing=d['hazards']['breathing_loop'];assert breathing['reserve_seconds']==600 and breathing['battery_independent']
assert not d['hazards']['lava_immunity'] and not d['hazards']['unlimited_underwater_air']
assert 'filter' not in d['hazards'] and 'interstice:engineer_filter_cartridge' not in recipes
paths=['src/main/java/pro/erez/interstice/gear/'+n+'.java' for n in ('RealmArmaments','RealmArmor','AegisArmor','AegisPower','RealmGear','GearHazards')]+['src/main/java/pro/erez/interstice/ToxicLiquidBlock.java','src/main/java/pro/erez/interstice/tide/BuoyancyController.java']
paths+=['src/main/resources/data/interstice/recipe/'+n+'.json' for n in ('resonant_processor','rift_lens','retort_etched_circuit_board','aegis_control_unit')]
source={p:hashlib.sha256((CAND/p).read_bytes()).hexdigest() for p in paths}
record=dict(recorded_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),scope='Design arithmetic, ingredient references and source traceability only; no gameplay implementation or Survival balance verification',passed=True,proposal_sha256=hashlib.sha256((ROOT/'source/engineer_gameplay_proposal.json').read_bytes()).hexdigest(),direct_assembly_materials=dict(counts),additional_bases='Four riftsilver armor parts, standard 24-ingot base; upstream intermediate manufacture not recursively expanded',code_sources=source,gameplay_implemented=False)
(ROOT/'reports/gameplay_design_review.json').write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(design_arithmetic_passed=True,ingredient_references_passed=True,gameplay_implemented=False)))
