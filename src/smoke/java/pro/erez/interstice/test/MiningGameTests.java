package pro.erez.interstice.test;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.TideSproutBlock;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.minerals.*;
import pro.erez.interstice.tide.*;
import pro.erez.interstice.worldgen.*;

@GameTestHolder("interstice_mining")
@PrefixGameTestTemplate(false)
public final class MiningGameTests {
    private static ProtoChunk rock(GameTestHelper h,boolean vault,int top){
        var c=new ProtoChunk(new ChunkPos(0,0),UpgradeData.EMPTY,LevelHeightAccessor.create(0,256),h.getLevel().registryAccess().registryOrThrow(Registries.BIOME),null);
        var biome=h.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(vault?RealmBiomes.STONE_VAULTS:RealmBiomes.PALE_GARDENS);
        c.fillBiomesFromNoise((x,y,z,s)->biome,Climate.empty());
        c.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.BIOMES);
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=1;y<=top;y++)c.setBlockState(new BlockPos(x,y,z),(y==top?Interstice.ABYSSAL_TURF.get():vault?VaultMaterials.VAULTSTONE.get():GardenMaterials.PALESTONE.get()).defaultBlockState(),false);
        return c;
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void depositsAreBoundedSeededAndRetainTheActualFiveRockHosts(GameTestHelper h){
        for(boolean vault:new boolean[]{false,true}){
            var a=rock(h,vault,vault?180:38);var b=rock(h,vault,vault?180:38);
            GroundStrata.generate(GeometryProfile.TALL,a,20261006);GroundStrata.generate(GeometryProfile.TALL,b,20261006);
            var ca=MineralDeposits.generate(GeometryProfile.TALL,a,20261006,3);var cb=MineralDeposits.generate(GeometryProfile.TALL,b,20261006,3);
            h.assertTrue(ca.equals(cb)&&ca.total()>0,"Ore deposits are empty or generation-order unstable");
            h.assertTrue(ca.coal()<=24&&ca.silver()<=18&&ca.phosphorite()<=10&&ca.vitriolite()<=4,"A mountain's rock volume multiplied its fixed ore budget");
            if(!vault)h.assertTrue(ca.vitriolite()==0,"Deep vault mineral leaked into a garden");
            var original=rock(h,vault,vault?180:38);GroundStrata.generate(GeometryProfile.TALL,original,20261006);
            for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=1;y<205;y++){
                var p=new BlockPos(x,y,z);var s=a.getBlockState(p);
                h.assertTrue(s.equals(b.getBlockState(p)),"Same seed changed an ore/soil cell");
                if(s.getBlock() instanceof RiftOreBlock){
                    h.assertTrue(s.getValue(RiftOreBlock.HOST)==RiftOreBlock.Host.from(original.getBlockState(p)),"Ore does not match the exact replaced stone");
                    var type=s.is(MineralEcology.RIFTSILVER_SEAM.get())?MineralDeposits.Type.SILVER:s.is(MineralEcology.UMBRAL_COAL_ORE.get())?MineralDeposits.Type.COAL:s.is(MineralEcology.PHOSPHORITE_ORE.get())?MineralDeposits.Type.PHOSPHORITE:MineralDeposits.Type.VITRIOLITE;
                    var rule=MineralDeposits.rule(type,vault?MineralDeposits.Habitat.VAULTS:MineralDeposits.Habitat.GARDENS,GeometryProfile.TALL);
                    int depth=(vault?180:38)-y;
                    h.assertTrue(y>=rule.minY()&&y<=rule.maxY()&&depth>=rule.minDepth()&&depth<=rule.maxDepth(),"Ore exceeds its absolute height or real surface-depth limits");
                }
                if(original.getBlockState(p).is(MineralEcology.ROOT_LOAM.get()))h.assertTrue(s.is(MineralEcology.ROOT_LOAM.get()),"Ore replaced soil");
            }
            var legacy=rock(h,vault,80);h.assertTrue(MineralDeposits.generate(GeometryProfile.TALL,legacy,20261006,2).total()==0,"New ores changed the saved revision-two algorithm");
            System.out.println("MINING_DEPOSITS vault="+vault+" counts="+ca);
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void naturalSoilStrataLeaveTurfFoundationAirAndFluidsIntact(GameTestHelper h){
        var chunk=rock(h,false,38);var air=new BlockPos(3,20,3);var fluid=new BlockPos(5,20,5);
        chunk.setBlockState(air,Blocks.AIR.defaultBlockState(),false);chunk.setBlockState(fluid,Interstice.HEAVY_BLOCK.get().defaultBlockState(),false);
        int changed=GroundStrata.generate(GeometryProfile.TALL,chunk,20261006);
        h.assertTrue(changed>0&&chunk.getBlockState(new BlockPos(7,37,7)).is(MineralEcology.ROOT_LOAM.get()),"Gardens have no actual soil underneath the turf");
        h.assertTrue(chunk.getBlockState(new BlockPos(7,38,7)).is(Interstice.ABYSSAL_TURF.get())&&chunk.getBlockState(new BlockPos(7,5,7)).is(GardenMaterials.PALESTONE.get()),"Strata replaced established turf or foundation");
        h.assertTrue(chunk.getBlockState(air).isAir()&&chunk.getBlockState(fluid).is(Interstice.HEAVY_BLOCK.get()),"Strata plugged caves or replaced toxic water");
        h.assertTrue(RiftOreBlock.Host.from(MineralEcology.RIFT_SHALE.get().defaultBlockState())==RiftOreBlock.Host.RIFT_SHALE&&RiftOreBlock.Host.from(MineralEcology.ROOT_LOAM.get().defaultBlockState())==null,"Soil and shale mining roles are confused");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void silkTouchRetainsHostAndNormalMiningDropsUsableMinerals(GameTestHelper h){
        var silk=new ItemStack(Items.IRON_PICKAXE);silk.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH),1);
        var point=h.absolutePos(new BlockPos(4,3,4));
        for(var ore:List.of(MineralEcology.RIFTSILVER_SEAM.get(),MineralEcology.UMBRAL_COAL_ORE.get(),MineralEcology.PHOSPHORITE_ORE.get(),MineralEcology.VITRIOLITE_ORE.get()))for(var host:RiftOreBlock.Host.values()){
            var state=ore.defaultBlockState().setValue(RiftOreBlock.HOST,host);
            var retained=Block.getDrops(state,h.getLevel(),point,null,null,silk).stream().filter(s->s.is(ore.asItem())).findFirst().orElseThrow();
            var props=retained.get(DataComponents.BLOCK_STATE);
            h.assertTrue(props!=null&&props.apply(ore.defaultBlockState()).getValue(RiftOreBlock.HOST)==host,"Silk touch lost the ore's actual host");
            var drops=Block.getDrops(state,h.getLevel(),point,null,null,new ItemStack(Items.IRON_PICKAXE));
            h.assertTrue(!drops.isEmpty()&&drops.stream().noneMatch(s->s.is(ore.asItem())),"Normal mining did not yield a functional mineral");
            h.assertTrue(state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE),"Ore has no mining tool tag");
        }h.succeed();
    }
    private static ItemStack craft(GameTestHelper h,ItemStack first,ItemStack second){var grid=CraftingInput.of(1,2,List.of(first,second));return h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,h.getLevel()).map(r->r.value().assemble(grid,h.getLevel().registryAccess())).orElse(ItemStack.EMPTY);}
    @GameTest(template="empty",timeoutTicks=100)
    public static void twoTorchesRequireWorldSticksAndCoalHasLongerFurnaceLife(GameTestHelper h){
        for(var wood:List.of(Interstice.GLOOMCROWN_PLANKS.get(),GardenMaterials.PALEHEART_PLANKS.get(),GardenMaterials.CROWN_PLANKS.get())){
            var grid=CraftingInput.of(1,1,List.of(new ItemStack(wood)));var sticks=h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,h.getLevel()).orElseThrow().value().assemble(grid,h.getLevel().registryAccess());
            h.assertTrue(sticks.is(MineralEcology.WORLD_STICK.get())&&sticks.getCount()==2,"An accepted realm wood cannot make world sticks");
        }
        var coal=craft(h,new ItemStack(MineralEcology.UMBRAL_COAL.get()),new ItemStack(MineralEcology.WORLD_STICK.get()));
        var living=craft(h,new ItemStack(MineralEcology.LUMINOUS_BUD.get()),new ItemStack(MineralEcology.WORLD_STICK.get()));
        h.assertTrue(coal.is(MineralEcology.COAL_TORCH_ITEM.get())&&coal.getCount()==4&&living.is(MineralEcology.LIVING_TORCH_ITEM.get()),"Realm lighting recipes do not produce both distinct torches");
        h.assertTrue(craft(h,new ItemStack(MineralEcology.LUMINOUS_BUD.get()),new ItemStack(Items.STICK)).isEmpty(),"Vanilla sticks bypass the living-torch wood chain");
        h.assertTrue(new ItemStack(MineralEcology.UMBRAL_COAL.get()).getBurnTime(RecipeType.SMELTING)==3200,"Own coal has no doubled furnace duration");
        h.assertTrue(MineralEcology.COAL_TORCH.get().defaultBlockState().getLightEmission(h.getLevel(),h.absolutePos(BlockPos.ZERO))==12&&MineralEcology.LIVING_TORCH.get().defaultBlockState().getLightEmission(h.getLevel(),h.absolutePos(BlockPos.ZERO))==13,"The two torch lights do not match their properties");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void livingTorchGivesBoundedHasteAndCannotReachThroughRockOrCurePoison(GameTestHelper h){
        var point=h.absolutePos(new BlockPos(4,3,4));var level=h.getLevel();level.setBlock(point.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(point,MineralEcology.LIVING_TORCH.get().defaultBlockState(),3);
        level.setBlock(h.absolutePos(new BlockPos(6,2,4)),Blocks.STONE.defaultBlockState(),3);var player=TestPlayers.create(h,new BlockPos(6,3,4),GameType.SURVIVAL);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,140));
        h.assertTrue(LivingTorchBlockEntity.reaches(level,point,player),"Open nearby player is outside the aura");
        h.runAfterDelay(45,()->{try{
            h.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SPEED)&&player.getEffect(net.minecraft.world.effect.MobEffects.DIG_SPEED).getAmplifier()==0,"Real block-entity ticks did not grant modest Haste I");
            h.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.POISON),"Living torch removed a toxic debuff");
            level.setBlock(h.absolutePos(new BlockPos(5,3,4)),Blocks.STONE.defaultBlockState(),3);level.setBlock(h.absolutePos(new BlockPos(5,4,4)),Blocks.STONE.defaultBlockState(),3);player.removeEffect(net.minecraft.world.effect.MobEffects.DIG_SPEED);
            h.assertTrue(!LivingTorchBlockEntity.reaches(level,point,player),"Aura crosses solid stone");
            h.runAfterDelay(45,()->{try{h.assertTrue(!player.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SPEED),"Blocked aura still refreshes Haste");h.succeed();}finally{TestPlayers.remove(player);}});
        }catch(RuntimeException|Error failure){TestPlayers.remove(player);throw failure;}});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void newMineralsMakeExistingLabBlocksAndSilverSeamSmeltsToItsIngot(GameTestHelper h){
        for(var pair:List.of(new net.minecraft.world.item.Item[]{MineralEcology.PHOSPHORITE_CRYSTAL.get(),Interstice.PHOSPHORITE_ITEM.get()},new net.minecraft.world.item.Item[]{MineralEcology.VITRIOLITE_SHARD.get(),Interstice.VITRIOLITE_ITEM.get()})){
            var grid=CraftingInput.of(2,2,List.of(new ItemStack(pair[0]),new ItemStack(pair[0]),new ItemStack(pair[0]),new ItemStack(pair[0])));
            var result=h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,h.getLevel()).orElseThrow().value().assemble(grid,h.getLevel().registryAccess());
            h.assertTrue(result.is(pair[1])&&result.getCount()==1,"New mineral shards do not integrate with the established material chain");
        }
        var input=new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(MineralEcology.RIFTSILVER_SEAM.get()));
        var silver=h.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING,input,h.getLevel()).orElseThrow().value().assemble(input,h.getLevel().registryAccess());
        h.assertTrue(silver.is(Interstice.RIFTSILVER_INGOT.get()),"Host-specific silver vein lost the existing ingot progression");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void shearsTopHarvestIsOncePerTideAndSurvivesSerializationAndBrokenStem(GameTestHelper h){
        var level=h.getLevel();var root=h.absolutePos(new BlockPos(4,3,4));var top=root.above(2);var block=Interstice.TIDE_SPROUT.get();
        var old=TideManager.getSavedData(level.getServer()).snapshot();TideManager.getSavedData(level.getServer()).setPhase(TidePhase.SURGE,10000);
        level.setBlock(root.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(root,block.defaultBlockState().setValue(TideSproutBlock.HEIGHT,2).setValue(TideSproutBlock.BLOOMED,true),3);
        level.setBlock(root.above(),block.defaultBlockState().setValue(TideSproutBlock.SECTION,1).setValue(TideSproutBlock.BLOOMED,true),3);level.setBlock(top,block.defaultBlockState().setValue(TideSproutBlock.SECTION,3).setValue(TideSproutBlock.HEIGHT,2).setValue(TideSproutBlock.BLOOMED,true),3);
        var player=TestPlayers.create(h,new BlockPos(4,4,5),GameType.SURVIVAL);player.getInventory().clearContent();var shears=new ItemStack(Items.SHEARS);player.setItemInHand(InteractionHand.MAIN_HAND,shears);
        try{
            var hit=new BlockHitResult(Vec3.atCenterOf(top),Direction.UP,top,false);
            var result=level.getBlockState(top).useItemOn(shears,level,player,InteractionHand.MAIN_HAND,hit);
            h.assertTrue(result.consumesAction()&&player.getInventory().countItem(MineralEcology.LUMINOUS_BUD.get())==1&&shears.getDamageValue()==1,"Shears at the opened upper tip did not harvest exactly one bud and one durability");
            h.assertTrue(level.getBlockState(top).getValue(TideSproutBlock.HARVESTED)&&level.getBlockState(top).getLightEmission(level,top)==0,"Cut flower still contains an emitting bud");
            try(var in=MiningGameTests.class.getClassLoader().getResourceAsStream("assets/interstice/blockstates/tide_sprout.json")){
                var states=com.google.gson.JsonParser.parseString(new String(java.util.Objects.requireNonNull(in).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("variants");
                h.assertTrue(states.getAsJsonObject("bloomed=true,harvested=true,height=2,section=3").get("model").getAsString().equals("interstice:block/tide_sprout_flower_harvested"),"The cut-bud state still selects the intact flower model");
            }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
            level.getBlockState(top).useItemOn(shears,level,player,InteractionHand.MAIN_HAND,hit);
            h.assertTrue(player.getInventory().countItem(MineralEcology.LUMINOUS_BUD.get())==1&&shears.getDamageValue()==1,"The same bloom gives infinite immediate buds");
            var saved=SproutHarvestData.get(level).save(new CompoundTag(),level.registryAccess());var loaded=SproutHarvestData.load(saved,level.registryAccess());
            h.assertTrue(loaded.locked(root,TideManager.getSavedData(level.getServer()).snapshot().totalCycles())&&level.getBlockState(root).getValue(TideSproutBlock.HARVESTED),"Cold-save marker or cycle lock is absent");
            h.assertTrue(Block.getDrops(level.getBlockState(root.above()),level,root.above(),null,player,shears).isEmpty(),"Breaking a stem clones the entire plant for free bud farming");
            level.destroyBlock(root.above(),false);h.assertTrue(level.getBlockState(root).getValue(TideSproutBlock.HARVESTED)&&level.getBlockState(root).getValue(TideSproutBlock.HEIGHT)==0,"Broken-stem collapse clears the harvest lock");
            TideManager.getSavedData(level.getServer()).setPhase(TidePhase.EBB,1000);block.tick(level.getBlockState(root),level,root,level.random);
            h.assertTrue(!level.getBlockState(root).getValue(TideSproutBlock.HARVESTED),"Actual tide closure does not rearm the next bloom");
            TideManager.getSavedData(level.getServer()).setPhase(TidePhase.SURGE,1000);block.tick(level.getBlockState(root),level,root,level.random);
            var restoredPos=root.above(level.getBlockState(root).getValue(TideSproutBlock.HEIGHT));var restored=level.getBlockState(restoredPos);
            h.assertTrue(restored.is(block)&&!restored.getValue(TideSproutBlock.HARVESTED)&&restored.getLightEmission(level,restoredPos)==14,"A fresh tide bloom did not restore its bud/light");
        }finally{TestPlayers.remove(player);TideManager.getSavedData(level.getServer()).setPhase(old.phase(),old.phaseDurationTicks());SproutHarvestData.get(level).clear(root);}
        h.succeed();
    }
}
