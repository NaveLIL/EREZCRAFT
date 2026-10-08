package pro.erez.interstice.test;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.food.*;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.*;

@GameTestHolder("interstice_food")
@PrefixGameTestTemplate(false)
public final class CrownFoodGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void giantPlansHaveDistinctWoodRoundedCrownsAndOnlyHighGlowingFruit(GameTestHelper h){
        var root=new BlockPos(7,80,7);var definition=GardenTreeDefinitions.get(GardenTreeDefinitions.CROWN);
        for(var variant:definition.variants()){
            var cells=GardenTrees.plan(h.getLevel(),p->p.getY()==79?Interstice.ABYSSAL_TURF.get().defaultBlockState():Blocks.AIR.defaultBlockState(),root,variant,RandomSource.create(8));
            h.assertTrue(!cells.isEmpty(),"Giant preset produces no complete tree");
            h.assertTrue(cells.values().stream().noneMatch(s->s.is(GardenMaterials.PALEHEART_LOG.get())||s.is(GardenMaterials.PALEHEART_LEAVES.get())),"New giant reused the small tree species");
            int top=cells.entrySet().stream().filter(e->e.getValue().is(GardenMaterials.CROWN_LEAVES.get())).mapToInt(e->e.getKey().getY()).max().orElse(0);
            long fruit=cells.entrySet().stream().filter(e->e.getValue().is(GardenMaterials.CROWN_FRUIT.get())).peek(e->{
                h.assertTrue(e.getKey().getY()>=top&&e.getKey().getY()-root.getY()>=18,"Fruit is available on a low branch instead of the crown summit");
                h.assertTrue(e.getValue().getLightEmission(h.getLevel(),e.getKey())==14,"Ripe crown food does not visibly emit light");
            }).count();
            int lowX=cells.keySet().stream().mapToInt(BlockPos::getX).min().orElse(0),highX=cells.keySet().stream().mapToInt(BlockPos::getX).max().orElse(0);
            h.assertTrue(top-root.getY()>=20&&highX-lowX>=12&&fruit>=2,"Giant lacks height, broad crown or distinct harvest sites");
            long layers=cells.entrySet().stream().filter(e->e.getValue().is(GardenMaterials.CROWN_LEAVES.get())).map(e->(e.getKey().getY()-root.getY())/5).distinct().count();
            h.assertTrue(layers>=3,"Multi-tier crown collapsed to one canopy");
            long summit=cells.entrySet().stream().filter(e->e.getValue().is(GardenMaterials.CROWN_LEAVES.get())&&e.getKey().getY()==top).count();
            long belly=cells.entrySet().stream().filter(e->e.getValue().is(GardenMaterials.CROWN_LEAVES.get())&&e.getKey().getY()==top-2).count();
            h.assertTrue(summit<belly,"Upper crown still has a flat platform instead of tapered rounded foliage");
            h.assertTrue(cells.entrySet().stream().filter(e->e.getValue().is(GardenMaterials.CROWN_LEAVES.get())).allMatch(e->e.getValue().getValue(LeavesBlock.DISTANCE)<7),"New species leaves will decay without branch support");
        }
        var grid=net.minecraft.world.item.crafting.CraftingInput.of(1,1,List.of(new ItemStack(GardenMaterials.CROWN_LOG.get())));
        var recipe=h.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,grid,h.getLevel()).orElseThrow().value();
        var planks=recipe.assemble(grid,h.getLevel().registryAccess());
        h.assertTrue(planks.is(GardenMaterials.CROWN_PLANKS.get().asItem())&&planks.getCount()==4,"Distinct crown wood has no usable plank recipe");
        h.assertTrue(GardenMaterials.CROWN_LOG.get().defaultBlockState().is(net.minecraft.tags.BlockTags.LOGS)&&GardenMaterials.CROWN_LEAVES.get().defaultBlockState().is(net.minecraft.tags.BlockTags.LEAVES),"Separate giant species is missing living log/leaf tags");
        var shears=new ItemStack(Items.SHEARS);
        h.assertTrue(Block.getDrops(GardenMaterials.CROWN_LEAVES.get().defaultBlockState(),h.getLevel(),h.absolutePos(BlockPos.ZERO),null,null,shears).stream().anyMatch(s->s.is(GardenMaterials.CROWN_LEAVES.get().asItem())),"Crown canopy drops the wrong species");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void unsuitableGiantDoesNotLeavePartialWoodOrPods(GameTestHelper h){
        var root=new BlockPos(7,80,7);var variant=GardenTreeDefinitions.get(GardenTreeDefinitions.CROWN).variants().get(0);
        var cells=GardenTrees.plan(h.getLevel(),p->p.getY()==79?Interstice.ABYSSAL_TURF.get().defaultBlockState():Blocks.AIR.defaultBlockState(),root,variant,RandomSource.create(8));
        var blocker=cells.keySet().iterator().next();
        h.assertTrue(!GardenTrees.fits(cells,p->p.equals(blocker)?Blocks.CHEST.defaultBlockState():p.getY()==79?Interstice.ABYSSAL_TURF.get().defaultBlockState():Blocks.AIR.defaultBlockState(),p->true,root),"Giant overwrites an obstacle");
        var old=variant.tree();var bad=new net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration.TreeConfigurationBuilder(
                net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.simple(Blocks.STONE),old.trunkPlacer,old.foliageProvider,old.foliagePlacer,old.minimumSize).build();
        var malformed=new GardenTreeDefinitions.Variant(variant.weight(),bad,variant.branchPath(),variant.stemWidth(),variant.extraHeight(),0,1,variant.joCode(),variant.fruitCount());
        h.assertTrue(GardenTrees.plan(h.getLevel(),p->p.getY()==79?Interstice.ABYSSAL_TURF.get().defaultBlockState():Blocks.AIR.defaultBlockState(),root,malformed,RandomSource.create(8)).isEmpty(),"Bad trunk provider crashes or produces partial giant wood");
        h.assertTrue(!GardenTrees.fits(cells,p->p.getY()==79?Interstice.ABYSSAL_TURF.get().defaultBlockState():Blocks.AIR.defaultBlockState(),p->IslandChunkGenerator.landAllowed(GeometryProfile.LEGACY,p.getX(),p.getY(),p.getZ()),root),"Giant ignores sea/build limits");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=600)
    public static void actualGardenGenerationIncludesRareFruitingGiants(GameTestHelper h){
        var level=java.util.Objects.requireNonNull(h.getLevel().getServer().getLevel(IslandWorld.TALL_WORLD));var g=(IslandChunkGenerator)level.getChunkSource().getGenerator();var sampler=level.getChunkSource().randomState().sampler();boolean found=false;
        for(int radius=0;radius<=28&&!found;radius++)for(int cx=-radius;cx<=radius&&!found;cx++)for(int cz=-radius;cz<=radius&&!found;cz++){
            if(Math.max(Math.abs(cx),Math.abs(cz))!=radius||!g.getBiomeSource().getNoiseBiome(cx*4+2,25,cz*4+2,sampler).is(RealmBiomes.PALE_GARDENS))continue;
            var random=RandomSource.create(level.getSeed()^new net.minecraft.world.level.ChunkPos(cx,cz).toLong()^0xCA015L);if(random.nextInt(8)!=0)continue;
            var chunk=level.getChunk(cx,cz);int fruit=0;
            for(int x=cx*16;x<cx*16+16;x++)for(int z=cz*16;z<cz*16+16;z++)for(int y=41;y<=205;y++){
                var p=new BlockPos(x,y,z);if(chunk.getBlockState(p).is(GardenMaterials.CROWN_FRUIT.get())){
                    h.assertTrue(IslandChunkGenerator.landAllowed(GeometryProfile.TALL,x,y,z),"Generated fruit intrudes into a sea clearance");fruit++;
                }
            }
            if(fruit>=2){found=true;System.out.println("NATURAL_CROWN_FOOD chunk="+chunk.getPos()+" fruit="+fruit);}
        }h.assertTrue(found,"No naturally generated high food crown within the bounded search");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void breakingOrFellingCannotHarvestTheFood(GameTestHelper h){
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(4,6,4));var fruit=GardenMaterials.CROWN_FRUIT.get();
        level.setBlock(p.below(),GardenMaterials.PALEHEART_LEAVES.get().defaultBlockState().setValue(LeavesBlock.DISTANCE,1).setValue(LeavesBlock.PERSISTENT,true),3);
        level.setBlock(p,fruit.defaultBlockState(),3);
        h.assertTrue(Block.getDrops(fruit.defaultBlockState(),level,p,null,null,new ItemStack(Items.IRON_AXE)).isEmpty(),"Breaking the fruit bypasses difficult crown harvesting");
        level.destroyBlock(p.below(),false);h.assertTrue(level.getBlockState(p).isAir(),"A severed crown retained a floating fruit");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void ripeFoodRequiresCloseHandHarvestAndBecomesUnripe(GameTestHelper h){
        var player=TestPlayers.create(h,new BlockPos(4,2,4),GameType.SURVIVAL);var level=h.getLevel();var p=h.absolutePos(new BlockPos(4,3,5));
        try{
            level.setBlock(p.below(),GardenMaterials.PALEHEART_LEAVES.get().defaultBlockState().setValue(LeavesBlock.DISTANCE,1).setValue(LeavesBlock.PERSISTENT,true),3);
            level.setBlock(p,GardenMaterials.CROWN_FRUIT.get().defaultBlockState(),3);
            var hit=new BlockHitResult(Vec3.atCenterOf(p),Direction.NORTH,p,false);
            var result=level.getBlockState(p).useWithoutItem(level,player,hit);h.assertTrue(result.consumesAction()&&player.getInventory().contains(new ItemStack(TideHeart.FRUIT.get())),"Close hand harvest did not yield actual food");
            h.assertTrue(level.getBlockState(p).getValue(CrownFruitBlock.AGE)==0,"Harvested pod remained ripe");
            int count=player.getInventory().countItem(TideHeart.FRUIT.get());level.getBlockState(p).useWithoutItem(level,player,hit);
            h.assertTrue(player.getInventory().countItem(TideHeart.FRUIT.get())==count,"Unripe pod gives unlimited immediate food");
        }finally{TestPlayers.remove(player);}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void actualConsumptionFeedsPlayerGivesSeedAndDoesNotRefreshTheTimer(GameTestHelper h){
        var player=TestPlayers.create(h,new BlockPos(4,2,4),GameType.SURVIVAL);
        try{
            player.getFoodData().setFoodLevel(8);var stack=new ItemStack(TideHeart.FRUIT.get());var seed=stack.finishUsingItem(h.getLevel(),player);
            h.assertTrue(player.getFoodData().getFoodLevel()==14&&player.hasEffect(MobEffects.NIGHT_VISION)&&player.hasEffect(TideHeart.REACTION),"Food does not provide the chosen benefit");
            h.assertTrue(seed.is(GardenMaterials.CROWN_SAPLING.get().asItem()),"The remaining seed cannot grow another crown");
            int before=player.getEffect(TideHeart.REACTION).getDuration();new ItemStack(TideHeart.FRUIT.get()).finishUsingItem(h.getLevel(),player);
            h.assertTrue(player.getEffect(TideHeart.REACTION).getDuration()==before&&!player.hasEffect(MobEffects.WEAKNESS),"Repeat serving bypasses the timer or starts the penalty too early");
        }finally{TestPlayers.remove(player);}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=140)
    public static void delayedWeaknessAndVisibilityAreRealEffectsAndMilkClearsThem(GameTestHelper h){
        var player=TestPlayers.create(h,new BlockPos(4,2,4),GameType.SURVIVAL);TideHeart.beginReaction(player);
        // Controlled boundary fixture: the native client separately proves the full real countdown.
        player.removeEffect(TideHeart.REACTION);player.addEffect(new MobEffectInstance(TideHeart.REACTION,TideHeart.AFTERTASTE_TICKS));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
        h.runAtTickTime(8,()->{
            try{
                h.assertTrue(player.hasEffect(MobEffects.WEAKNESS)&&player.hasEffect(MobEffects.GLOWING)&&TideHeart.isMarkedPrey(player),"Chosen delayed cost was not activated server-side");
                new ItemStack(Items.MILK_BUCKET).finishUsingItem(h.getLevel(),player);
                h.assertTrue(!player.hasEffect(TideHeart.REACTION)&&!player.hasEffect(MobEffects.GLOWING),"Milk cleansing did not clear the reaction");
                h.succeed();
            }finally{TestPlayers.remove(player);}
        });
    }
}
