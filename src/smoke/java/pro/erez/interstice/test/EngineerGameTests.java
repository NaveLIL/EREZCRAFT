package pro.erez.interstice.test;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.*;
import pro.erez.interstice.*;
import pro.erez.interstice.gear.*;
import pro.erez.interstice.tide.*;
import pro.erez.interstice.worldgen.IslandWorld;

@GameTestHolder("interstice_engineer")
@PrefixGameTestTemplate(false)
public final class EngineerGameTests {
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("interstice",name);}
    private static String kind(EquipmentSlot s){return switch(s){case HEAD->"helmet";case CHEST->"chestplate";case LEGS->"leggings";case FEET->"boots";default->throw new IllegalArgumentException();};}
    public static void equip(LivingEntity p){for(var s:RealmArmor.SLOTS)p.setItemSlot(s,new ItemStack(EngineerArmor.item(kind(s))));}
    public static void modules(LivingEntity p,int energy,int air){var cell=new ItemStack(EngineerArmor.CELL.get());EngineerModules.energy(cell,energy);EngineerModules.set(p.getItemBySlot(EquipmentSlot.HEAD),0,cell);var cassette=new ItemStack(EngineerArmor.CASSETTE.get());EngineerModules.reserve(cassette,air);EngineerModules.set(p.getItemBySlot(EquipmentSlot.CHEST),0,cassette);}
    private static ServerPlayer player(GameTestHelper h){return TestPlayers.create(h,new BlockPos(3,2,3),GameType.SURVIVAL);}
    private static void damageReady(ServerPlayer player){player.setNoGravity(true);player.setInvulnerable(false);for(int i=0;i<80;i++){player.tick();player.connection.tick();}player.invulnerableTime=0;player.setHealth(20);}
    private static Consumer<EngineerBreathing.MediumEvent> airless(ServerPlayer player){Consumer<EngineerBreathing.MediumEvent> listener=e->{if(e.getEntity()==player)e.setNonrespirable(true);};NeoForge.EVENT_BUS.addListener(listener);return listener;}
    @GameTest(template="empty") public static void materialAndPhysicalBonusesAreD02NotIronOrMagen(GameTestHelper h){
        var p=player(h);try{equip(p);for(int i=0;i<3;i++)p.connection.tick();
            h.assertTrue(p.getArmorValue()==22&&p.getAttributeValue(Attributes.ARMOR_TOUGHNESS)==14,"22 defense / 14 toughness");
            h.assertTrue(Math.abs(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)-.32)<.001&&p.getMaxHealth()==20,"No free health or Magen stability");
            int[] durability={495,720,675,585};int i=0;for(var s:RealmArmor.SLOTS)h.assertTrue(p.getItemBySlot(s).getMaxDamage()==durability[i++],"D02 durability "+s);
            h.assertTrue(Math.abs(p.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER)-.65)<.001,"35 percent fall reduction");
            h.assertTrue(RealmArmor.pieces(p,true)==0&&AegisArmor.pieces(p)==0,"Independent branch");
        }finally{TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void modulesConserveRealObjectsAndRejectNestedOrWrongSlots(GameTestHelper h){
        var p=player(h);try{equip(p);var cell=new ItemStack(EngineerArmor.CELL.get());EngineerModules.energy(cell,12345);cell.set(DataComponents.CUSTOM_NAME,Component.literal("My cell"));
            h.assertTrue(EngineerModules.install(p,cell)&&cell.isEmpty(),"Install consumes one physical cell");
            var stored=EngineerModules.get(p.getItemBySlot(EquipmentSlot.HEAD),0);h.assertTrue(EngineerModules.energy(stored)==12345&&stored.getHoverName().getString().equals("My cell"),"Full metadata retained");
            h.assertTrue(!EngineerModules.install(p,new ItemStack(EngineerArmor.CELL.get())),"Occupied socket is not overwritten");
            h.assertTrue(!EngineerModules.allowed(p.getItemBySlot(EquipmentSlot.CHEST),1,stored),"A belt socket cannot hold a cell");
            h.assertTrue(!EngineerModules.allowed(p.getItemBySlot(EquipmentSlot.HEAD),0,new ItemStack(EngineerArmor.CELL.get(),2)),"No stacked cell ownership");
            stored.set(DataComponents.CONTAINER,net.minecraft.world.item.component.ItemContainerContents.EMPTY);h.assertTrue(!EngineerModules.allowed(p.getItemBySlot(EquipmentSlot.HEAD),0,stored),"No nested module storage");
        }finally{TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void nativeMenuShiftTransfersExactlyOneCellAndInvalidatesArmorLease(GameTestHelper h){
        var p=player(h);try{equip(p);modules(p,812345,4321);var menu=new EngineerMenu(144,p.getInventory());p.containerMenu=menu;
            var moved=menu.quickMoveStack(p,0);h.assertTrue(!moved.isEmpty()&&EngineerModules.get(p.getItemBySlot(EquipmentSlot.HEAD),0).isEmpty(),"Native shift transfer removes socket ownership");
            int count=0;for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).is(EngineerArmor.CELL.get()))count+=p.getInventory().getItem(i).getCount();h.assertTrue(count==1,"One and only one physical cell");
            var previous=p.getItemBySlot(EquipmentSlot.HEAD);p.setItemSlot(EquipmentSlot.HEAD,previous.copy());h.assertTrue(!menu.stillValid(p),"Replacing armor invalidates old lease even with matching data");
            h.assertTrue(menu.quickMoveStack(p,1).isEmpty(),"Stale menu cannot extract another item");
        }finally{p.closeContainer();TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void nativeSmithingPreservesWearEnchantmentsNameAndUnknownData(GameTestHelper h){
        var p=player(h);try{var table=h.absolutePos(new BlockPos(6,1,5));h.getLevel().setBlock(table,Blocks.SMITHING_TABLE.defaultBlockState(),3);
            var menu=new SmithingMenu(122,p.getInventory(),ContainerLevelAccess.create(h.getLevel(),table));p.containerMenu=menu;
            var base=new ItemStack(RealmArmaments.item("riftsilver_helmet"));base.setDamageValue(25);base.set(DataComponents.CUSTOM_NAME,Component.literal("Personal engineer"));var tag=EngineerModules.data(base);tag.putString("owner_note","retain");EngineerModules.data(base,tag);
            var enchant=h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);base.enchant(enchant,4);
            menu.getSlot(0).setByPlayer(new ItemStack(EngineerArmor.ASSEMBLY.get(),2));menu.getSlot(1).setByPlayer(base);menu.getSlot(2).setByPlayer(new ItemStack(EngineerArmor.ADAPTER.get(),2));var result=menu.getSlot(3).getItem();
            h.assertTrue(result.is(EngineerArmor.item("helmet"))&&result.getMaxDamage()==495&&result.getDamageValue()==25,"Correct upgraded material and preserved wear");
            h.assertTrue(result.getHoverName().getString().equals("Personal engineer")&&EngineerModules.data(result).getString("owner_note").equals("retain")&&EnchantmentHelper.getItemEnchantmentLevel(enchant,result)==4,"Native smithing preserves full patch");
            menu.quickMoveStack(p,3);h.assertTrue(menu.getSlot(0).getItem().getCount()==1&&menu.getSlot(1).getItem().isEmpty()&&menu.getSlot(2).getItem().getCount()==1,"Actual table pays all three inputs once");
        }finally{p.closeContainer();TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void realCraftingRefillConservesModuleAndConsumesReagents(GameTestHelper h){
        var p=player(h);try{var table=h.absolutePos(new BlockPos(6,1,5));h.getLevel().setBlock(table,Blocks.CRAFTING_TABLE.defaultBlockState(),3);
            var menu=new CraftingMenu(123,p.getInventory(),ContainerLevelAccess.create(h.getLevel(),table));p.containerMenu=menu;
            var cell=new ItemStack(EngineerArmor.CELL.get());cell.set(DataComponents.CUSTOM_NAME,Component.literal("Owned empty cell"));var tag=EngineerModules.data(cell);tag.putString("note","unchanged");EngineerModules.data(cell,tag);
            menu.getSlot(1).setByPlayer(cell);menu.getSlot(2).setByPlayer(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id("phosphorite_paste"))));menu.getSlot(3).setByPlayer(new ItemStack(Items.REDSTONE));menu.getSlot(4).setByPlayer(new ItemStack(Items.REDSTONE));
            var result=menu.getSlot(0).getItem();h.assertTrue(EngineerModules.energy(result)==EngineerModules.CAPACITY&&result.getHoverName().getString().equals("Owned empty cell")&&EngineerModules.data(result).getString("note").equals("unchanged"),"Full resource on same module metadata");
            menu.quickMoveStack(p,0);for(int i=1;i<=4;i++)h.assertTrue(menu.getSlot(i).getItem().isEmpty(),"Spent module and reagents are consumed");
            int count=0;for(int i=0;i<36;i++)if(p.getInventory().getItem(i).is(EngineerArmor.CELL.get()))count++;h.assertTrue(count==1,"No free duplicate from crafting remainder");
        }finally{p.closeContainer();TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void cassetteRefillRejectsPartlyUsedAndRetainsUnrelatedData(GameTestHelper h){
        var recipe=h.getLevel().getRecipeManager().byKey(id("engineer_breathing_cassette_refill")).orElseThrow().value();var cassette=new ItemStack(EngineerArmor.CASSETTE.get());EngineerModules.reserve(cassette,0);cassette.set(DataComponents.CUSTOM_NAME,Component.literal("Air cassette"));
        var inputs=List.of(cassette,new ItemStack(pro.erez.interstice.agriculture.RealmAgriculture.PURE_LINING.get()),new ItemStack(Items.CHARCOAL),new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id("phosphorite_paste"))));var input=CraftingInput.of(2,2,inputs);
        var craft=(CraftingRecipe)recipe;h.assertTrue(craft.matches(input,h.getLevel()),"Spent cassette matches service chemistry");var result=craft.assemble(input,h.getLevel().registryAccess());h.assertTrue(EngineerModules.reserve(result)==12000&&result.getHoverName().getString().equals("Air cassette"),"Refill retains cassette identity data");
        EngineerModules.reserve(cassette,1);h.assertTrue(!craft.matches(input,h.getLevel()),"Partial module cannot refill for free");h.succeed();
    }
    @GameTest(template="empty") public static void breathingHoldsExistingAirAndPaysOnceAcrossOverlappingCallbacks(GameTestHelper h){
        var p=player(h);Consumer<EngineerBreathing.MediumEvent> medium=null;try{equip(p);modules(p,0,100);p.setAirSupply(71);
            h.assertTrue(!EngineerBreathing.breathe(p)&&EngineerModules.reserve(EngineerModules.get(p.getItemBySlot(EquipmentSlot.CHEST),0))==100,"Clean air costs nothing");medium=airless(p);
            h.assertTrue(EngineerBreathing.breathe(p),"Independent mechanical breathing with zero battery");EngineerBreathing.breathe(p);EngineerBreathing.tick(p);
            h.assertTrue(p.getAirSupply()==71&&EngineerModules.reserve(EngineerModules.get(p.getItemBySlot(EquipmentSlot.CHEST),0))==99,"No lung reset and one reserve tick for overlapping media");
            p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);h.assertTrue(!EngineerBreathing.breathe(p),"Removal closes no imaginary seal");
        }finally{if(medium!=null)NeoForge.EVENT_BUS.unregister(medium);TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void breathingNeverCancelsHeatContactToxinOrInjectedPoison(GameTestHelper h){
        var p=player(h);Consumer<EngineerBreathing.MediumEvent> medium=null;try{damageReady(p);equip(p);modules(p,0,1000);medium=airless(p);p.setHealth(20);p.invulnerableTime=0;p.hurt(p.damageSources().lava(),4);h.assertTrue(p.getHealth()<20,"A breathing mask is not heat insulation");
            p.setHealth(20);p.invulnerableTime=0;p.hurt(new net.minecraft.world.damagesource.DamageSource(h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ToxicLiquidBlock.TOXIN)),6);h.assertTrue(p.getHealth()<20,"Unmarked upper/contact toxin remains dangerous");
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,200));h.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.POISON),"Injected poison not globally erased");
        }finally{if(medium!=null)NeoForge.EVENT_BUS.unregister(medium);TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void lowerSeaLiningIsExactlySixtyPercentNotMultiplied(GameTestHelper h){
        var p=player(h);try{damageReady(p);equip(p);for(int i=0;i<3;i++)p.connection.tick();var source=new net.minecraft.world.damagesource.DamageSource(h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ToxicLiquidBlock.TOXIN));
            p.setHealth(20);p.invulnerableTime=0;EngineerArmor.lowerSeaDamage(p,source,10);h.assertTrue(Math.abs(p.getHealth()-16)<.01,"Ten bypass damage becomes four, not 24% multiplied again");
            p.setItemSlot(EquipmentSlot.FEET,ItemStack.EMPTY);p.setHealth(20);p.invulnerableTime=0;EngineerArmor.lowerSeaDamage(p,source,10);h.assertTrue(Math.abs(p.getHealth()-10)<.01,"Incomplete chemical suit loses emergency seal immediately");
        }finally{TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void nativeRiftWardIsCappedAndPhysicalWearDoesNotRefillModules(GameTestHelper h){
        var p=player(h);try{damageReady(p);equip(p);modules(p,12345,9876);var enchant=h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(RealmArmor.RIFT_WARD);p.getItemBySlot(EquipmentSlot.CHEST).enchant(enchant,3);
            var source=new net.minecraft.world.damagesource.DamageSource(h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ToxicLiquidBlock.TOXIN));p.setHealth(20);p.invulnerableTime=0;EngineerArmor.lowerSeaDamage(p,source,10);
            h.assertTrue(Math.abs(p.getHealth()-16)<.01,"Lining plus ward never exceeds .60");var chest=p.getItemBySlot(EquipmentSlot.CHEST);chest.setDamageValue(0);h.assertTrue(EngineerModules.reserve(EngineerModules.get(chest,0))==9876&&EngineerModules.energy(EngineerModules.get(p.getItemBySlot(EquipmentSlot.HEAD),0))==12345,"Physical repair never refills either independent resource");
        }finally{TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void pulseUsesServerLineOfSightEnergyAndPersistentCooldown(GameTestHelper h){
        var p=player(h);Cow visible=null,hidden=null;try{equip(p);modules(p,100000,0);for(int x=2;x<=10;x++)for(int y=1;y<=5;y++)for(int z=2;z<=7;z++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
            visible=EntityType.COW.create(h.getLevel());hidden=EntityType.COW.create(h.getLevel());var a=h.absolutePos(new BlockPos(5,2,3));var b=h.absolutePos(new BlockPos(3,2,6));visible.setPos(a.getX()+.5,a.getY(),a.getZ()+.5);hidden.setPos(b.getX()+.5,b.getY(),b.getZ()+.5);h.getLevel().addFreshEntity(visible);h.getLevel().addFreshEntity(hidden);
            for(int x=2;x<=4;x++)for(int y=1;y<=5;y++)h.setBlock(new BlockPos(x,y,5),Blocks.STONE);
            h.assertTrue(EngineerSystems.visible(p,visible)&&!EngineerSystems.visible(p,hidden),"Server LOS rejects an actual wall");
            h.assertTrue(EngineerSystems.pulse(p),"Server pays a real pulse");h.assertTrue(EngineerModules.energy(EngineerModules.get(p.getItemBySlot(EquipmentSlot.HEAD),0))==60000,"Exactly 40 energy paid");
            h.assertTrue(!EngineerSystems.pulse(p)&&EngineerSystems.cooldown(p)==240,"Replay cannot bypass server cooldown");
            p.setItemSlot(EquipmentSlot.HEAD,p.getItemBySlot(EquipmentSlot.HEAD).copy());h.assertTrue(!EngineerSystems.pulse(p),"Helmet swaps cannot reset player cooldown");
        }finally{if(visible!=null)visible.discard();if(hidden!=null)hidden.discard();TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void fractionalPowerAndNestedModulesSurviveNativeItemRoundTrip(GameTestHelper h){
        var p=player(h);try{equip(p);modules(p,1125025,11999);var belt=new ItemStack(RealmGear.BALLAST_BELT.get());belt.setDamageValue(37);var bt=EngineerModules.data(belt);bt.putInt("interstice_ballast_active_ticks",11);EngineerModules.data(belt,bt);EngineerModules.set(p.getItemBySlot(EquipmentSlot.CHEST),1,belt);
            var head=ItemStack.parse(h.getLevel().registryAccess(),p.getItemBySlot(EquipmentSlot.HEAD).save(h.getLevel().registryAccess())).orElseThrow();
            var chest=ItemStack.parse(h.getLevel().registryAccess(),p.getItemBySlot(EquipmentSlot.CHEST).save(h.getLevel().registryAccess())).orElseThrow();
            h.assertTrue(EngineerModules.energy(EngineerModules.get(head,0))==1125025,"Fractional quarter unit is exact");h.assertTrue(EngineerModules.reserve(EngineerModules.get(chest,0))==11999&&EngineerModules.get(chest,1).getDamageValue()==37&&EngineerModules.data(EngineerModules.get(chest,1)).getInt("interstice_ballast_active_ticks")==11,"Breathing and real belt state survive together");
        }finally{TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void tickCannotHarvestTwiceOrInventOfflineEnergy(GameTestHelper h){
        var p=player(h);try{equip(p);modules(p,100000,0);EngineerSystems.tick(p);int value=EngineerModules.energy(EngineerModules.get(p.getItemBySlot(EquipmentSlot.HEAD),0));EngineerSystems.tick(p);h.assertTrue(value==99950&&EngineerModules.energy(EngineerModules.get(p.getItemBySlot(EquipmentSlot.HEAD),0))==value,"Exactly one worn foreign-world tick, no elapsed/offline grant");
            h.assertTrue(EngineerSystems.harvest(p,new TideState(TidePhase.SURGE,100,1000,0))==0,"No field in Overworld");
            var tiny=new ItemStack(EngineerArmor.CELL.get());EngineerModules.energy(tiny,1);EngineerModules.set(p.getItemBySlot(EquipmentSlot.HEAD),0,tiny);h.assertTrue(!EngineerSystems.active(p),"Sub-cost residue never grants eternal free HUD");
        }finally{TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void fieldAndMountedBeltKeepNativeEligibilityAndChooseOnePhysicalBelt(GameTestHelper h){
        var p=player(h);try{equip(p);var realm=h.getLevel().getServer().getLevel(IslandWorld.TALL_WORLD);realm.getChunk(50,50);for(int x=798;x<=802;x++)for(int z=798;z<=802;z++)for(int y=99;y<225;y++)realm.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);p.teleportTo(realm,800.5,100,800.5,Set.of(),0,0);p.hasChangedDimension();
            var surge=new TideState(TidePhase.SURGE,100,1000,0);h.assertTrue(EngineerSystems.harvest(p,new TideState(TidePhase.CALM,0,1000,0))==25,"Exactly .5 units/s ambient fixed point");h.assertTrue(EngineerSystems.harvest(p,surge)>25&&EngineerSystems.harvest(p,surge)<=150,"Bounded phase/intensity harvesting");
            var mounted=new ItemStack(RealmGear.BALLAST_BELT.get());mounted.setDamageValue(15);EngineerModules.set(p.getItemBySlot(EquipmentSlot.CHEST),1,mounted);h.assertTrue(RealmGear.buoyancyFactor(p)==.62,"Mounted real belt replaces holding it");
            var held=new ItemStack(RealmGear.BALLAST_BELT.get());p.setItemInHand(InteractionHand.OFF_HAND,held);BuoyancyController.applyEntityBuoyancy(p,1);h.assertTrue(Math.abs(p.getAttribute(Attributes.GRAVITY).getModifier(BuoyancyController.BUOYANCY_ID).amount()+.108*.62)<.0001,"Never multiply two belts");RealmGear.tick(p,surge);
            h.assertTrue(EngineerModules.data(EngineerModules.get(p.getItemBySlot(EquipmentSlot.CHEST),1)).getInt("interstice_ballast_active_ticks")==0&&EngineerModules.data(held).getInt("interstice_ballast_active_ticks")==1,"Only selected strongest belt accrues wear");
            realm.setBlock(new BlockPos(800,104,800),Blocks.STONE.defaultBlockState(),3);h.assertTrue(EngineerSystems.harvest(p,surge)==25,"Shelter removes the surge bonus");
        }finally{TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void previewSharesThreeByThreeEligibilityAndNeverTouchesBlocks(GameTestHelper h){
        var p=player(h);try{var origin=h.absolutePos(new BlockPos(3,2,5));for(var pos:RealmWorktools.plane(origin,Direction.Axis.Z))h.getLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),3);h.getLevel().setBlock(origin,Blocks.STONE.defaultBlockState(),3);
            var tool=new ItemStack(RealmArmaments.item("riftsteel_pickaxe"));RealmWorktools.setArea(tool,true);var plan=RealmWorktools.preview(p,tool,origin,Direction.NORTH);h.assertTrue(plan.size()==9,"Nine actual eligible positions");h.getLevel().setBlock(origin.above(),Blocks.BEDROCK.defaultBlockState(),3);var filtered=RealmWorktools.preview(p,tool,origin,Direction.NORTH);h.assertTrue(filtered.size()==8&&!filtered.contains(origin.above()),"Preview excludes unworkable neighbor");h.assertTrue(h.getLevel().getBlockState(origin).is(Blocks.STONE),"Read-only plan changes no blocks");p.setShiftKeyDown(true);h.assertTrue(RealmWorktools.preview(p,tool,origin,Direction.NORTH).isEmpty(),"Single-work suppression shared with server");
        }finally{TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void ordinaryEnchantmentsAndAttunementUseActualRegistrySupport(GameTestHelper h){
        var p=player(h);try{equip(p);for(int i=0;i<3;i++)p.connection.tick();
            h.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED)-.108)<.00001,"Eight percent base movement bonus");
            h.assertTrue(Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT)-1.1)<.00001&&p.getAttributeValue(Attributes.OXYGEN_BONUS)==1,"Half block step and one oxygen bonus");
            var registry=h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            for(var slot:RealmArmor.SLOTS){var armor=p.getItemBySlot(slot);for(var enchant:List.of(Enchantments.PROTECTION,Enchantments.MENDING,Enchantments.UNBREAKING))h.assertTrue(registry.getHolderOrThrow(enchant).value().isSupportedItem(armor),"Ordinary enchantment supports "+slot);}
            var attune=registry.getHolderOrThrow(EngineerSystems.ATTUNEMENT);var head=p.getItemBySlot(EquipmentSlot.HEAD);
            h.assertTrue(attune.value().isSupportedItem(head)&&!attune.value().isSupportedItem(p.getItemBySlot(EquipmentSlot.CHEST)),"Optional attunement is helmet only");
            head.enchant(attune,3);h.assertTrue(EngineerSystems.attunement(p)==3&&EngineerSystems.cost(2,3)==1400&&EngineerSystems.cost(40,3)==28000&&EngineerSystems.cost(1,3)==1000,"Thirty percent discount, positive minimum one");
        }finally{TestPlayers.remove(p);}h.succeed();
    }
    @GameTest(template="empty") public static void brokenSealRevokesBreathingWhileHelmetWorksIndependently(GameTestHelper h){
        var p=player(h);try{equip(p);modules(p,100000,1000);var chest=p.getItemBySlot(EquipmentSlot.CHEST);chest.setDamageValue(chest.getMaxDamage());
            h.assertTrue(!EngineerBreathing.ready(p)&&EngineerArmor.pieces(p)==3,"Broken piece immediately revokes the fitted seal");
            h.assertTrue(EngineerSystems.active(p),"Powered helmet does not require a full armor set");
            p.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY);p.setItemSlot(EquipmentSlot.LEGS,ItemStack.EMPTY);p.setItemSlot(EquipmentSlot.FEET,ItemStack.EMPTY);
            h.assertTrue(EngineerSystems.active(p)&&!EngineerBreathing.ready(p),"Independent electronics and closed breathing have distinct requirements");
            var head=p.getItemBySlot(EquipmentSlot.HEAD);head.setDamageValue(head.getMaxDamage());h.assertTrue(!EngineerSystems.active(p),"Broken helmet cannot power a virtual visor");
        }finally{TestPlayers.remove(p);}h.succeed();
    }
}
