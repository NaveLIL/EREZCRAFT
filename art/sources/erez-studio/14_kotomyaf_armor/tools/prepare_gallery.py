"""Extend the proven A04 gallery with cat audio completion and real mob avoidance checks."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
base=(ROOT.parent/'13_minecraft_armor/tools/gallery_java/ArmorPlayerGallery.java').read_text(encoding='utf-8')
base=base.replace('ArmorPlayerGallery','KotomyafGallery')
base=base.replace('new String[]','new String[]')
base=base.replace('{"riftsilver","riftsteel","aegis"}','{"kotomyaf","kotomyaf","aegis"}')
base=base.replace('{"EREZ_Silver","EREZ_Steel","EREZ_Magen"}','{"Kotomyaf_Wide","Kotomyaf_Slim","EREZ_Magen"}')
base=base.replace('tier.equals("aegis")?AegisArmor.item(tier+"_"+kind(slot)):RealmArmaments.item(tier+"_"+kind(slot))',
                  'tier.equals("aegis")?AegisArmor.item(tier+"_"+kind(slot)):KotomyafArmor.item(tier+"_"+kind(slot))')
base=base.replace('TIERS[i].equals("aegis")?AegisArmor.item("aegis_"+kind(slot)):RealmArmaments.item(TIERS[i]+"_"+kind(slot))',
                  'TIERS[i].equals("aegis")?AegisArmor.item("aegis_"+kind(slot)):KotomyafArmor.item(TIERS[i]+"_"+kind(slot))')
base=base.replace('"gallery-"','"kotomyaf-"').replace('armor-player-gallery.json','kotomyaf-gallery.json')
base=base.replace('silver-close','wide-close').replace('steel-close','slim-close')
base=base.replace('armor-gallery','kotomyaf-gallery')
base=base.replace('inspect(mc);REPORT.addProperty("mod_count"', 'inspect(mc);require(RealmArmorClient.model("kotomyaf",EquipmentSlot.HEAD).cubeCount()==75,"A04 without a tail required");REPORT.addProperty("tail_absent",true);REPORT.addProperty("mod_count"')
base=base.replace('private static UUID observer;', '''private static UUID observer;
    private static long beforeMeow,beforePurr,beforePurrFinished;
    private static int audioPolls,fearPolls;
    private static final List<net.minecraft.world.entity.Mob> FEAR_MOBS=new ArrayList<>();
    private static double[] fearStart;
''')
base=base.replace('if(movement==1){double z=', 'if(movement==1||movement==2&&i==0){double z=')
base=base.replace('for(var p:ACTORS)mc.getSingleplayerServer().getPlayerList().remove(p);prepared=false;',
    'for(var mob:FEAR_MOBS)mob.discard();for(var p:ACTORS)mc.getSingleplayerServer().getPlayerList().remove(p);prepared=false;')
old='shot(mc,"all-swimming");REPORT.addProperty("swimming_checked",true);inspect(mc);finish(mc,true,"");'
assert old in base
base=base.replace(old,'''shot(mc,"all-swimming");REPORT.addProperty("swimming_checked",true);inspect(mc);
                beforeMeow=KotomyafVoiceClient.meowsStarted;beforePurr=KotomyafVoiceClient.purrsStarted;beforePurrFinished=KotomyafVoiceClient.purrsNaturallyFinished;
                job=server.submit(()->{
                    var level=server.overworld();
                    for(int x=24;x<=40;x++)for(int z=29;z<=35;z++)for(int y=200;y<=204;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
                    for(int i=0;i<3;i++)reset(ACTORS.get(i),i);
                    ACTORS.getFirst().setShiftKeyDown(true);movement=2;return true;
                });camera(mc,32,201.02,25,0,0);phase=13;
            }else if(phase==13){
                require(++audioPolls<6,"Native purr did not start during sneak movement");
                if(KotomyafVoiceClient.purrsStarted<=beforePurr)return;
                job=server.submit(()->{movement=0;var p=ACTORS.getFirst();p.setShiftKeyDown(false);p.setOnGround(true);
                    p.jumpFromGround();p.jumpFromGround();p.jumpFromGround();return true;});phase=14;audioPolls=0;
            }else if(phase==14){
                require(++audioPolls<7,"Native queued meow / natural purr completion timed out");
                if(KotomyafVoiceClient.meowsStarted<=beforeMeow||KotomyafVoiceClient.purrsNaturallyFinished<=beforePurrFinished)return;
                require(KotomyafVoiceClient.shortestPurrFinishedMillis>=2900,"Purr must finish its full >=3-second native clip after Shift release and queued jumps");
                REPORT.addProperty("native_purr_finished_after_shift_release",true);REPORT.addProperty("native_jump_meow_queued_without_cut",true);
                REPORT.addProperty("purrs_started",KotomyafVoiceClient.purrsStarted);REPORT.addProperty("meows_started",KotomyafVoiceClient.meowsStarted);
                REPORT.addProperty("natural_sound_finishes",KotomyafVoiceClient.naturallyFinished);REPORT.addProperty("shortest_purr_ms",KotomyafVoiceClient.shortestPurrFinishedMillis);
                job=server.submit(()->{
                    var level=server.overworld();for(int i=0;i<3;i++)reset(ACTORS.get(i),i);
                    ACTORS.get(1).setPos(48,200,48);ACTORS.get(2).setPos(48,200,48);
                    var c=net.minecraft.world.entity.EntityType.CREEPER.create(level);c.setPos(36.8,200,32);c.setInvulnerable(true);level.addFreshEntity(c);FEAR_MOBS.add(c);
                    var z=net.minecraft.world.entity.EntityType.ZOMBIE.create(level);z.setBaby(true);z.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.LEATHER_HELMET));z.setPos(34.5,200,34.3);z.setInvulnerable(true);level.addFreshEntity(z);FEAR_MOBS.add(z);
                    fearStart=FEAR_MOBS.stream().mapToDouble(m->m.distanceTo(ACTORS.getFirst())).toArray();
                    for(var mob:FEAR_MOBS)mob.setTarget(ACTORS.getFirst());return true;
                });phase=15;
            }else if(phase==15){
                require(++fearPolls<8,"Actual creeper and baby zombie did not retreat on the gallery floor");
                job=server.submit(()->{
                    boolean away=true;var distances=new JsonArray();
                    for(int i=0;i<FEAR_MOBS.size();i++){var mob=FEAR_MOBS.get(i);double distance=mob.distanceTo(ACTORS.getFirst());away&=distance>fearStart[i]+1;
                        var row=new JsonObject();row.addProperty("type",net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());row.addProperty("before",fearStart[i]);row.addProperty("after",distance);row.addProperty("protected_target_cleared",mob.getTarget()!=ACTORS.getFirst());distances.add(row);}
                    REPORT.add("fear_distances",distances);return away;
                });phase=16;
            }else if(phase==16){
                if(!Boolean.TRUE.equals(job.join())){phase=15;return;}
                shot(mc,"creeper-and-baby-zombie-retreat");REPORT.addProperty("native_creeper_and_baby_zombie_retreated",true);finish(mc,true,"");''')
out=ROOT/'tools/gallery_java/KotomyafGallery.java';out.parent.mkdir(exist_ok=True);out.write_text(base,encoding='utf-8')
print(out)
