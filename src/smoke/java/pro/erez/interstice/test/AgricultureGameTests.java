package pro.erez.interstice.test;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import pro.erez.interstice.*;
import pro.erez.interstice.agriculture.*;
import pro.erez.interstice.minerals.MineralEcology;
import pro.erez.interstice.worldgen.*;

@GameTestHolder("interstice_agriculture") @PrefixGameTestTemplate(false)
public final class AgricultureGameTests {
    private static final BlockPos SOIL=new BlockPos(6,1,6),CROP=SOIL.above(),MACHINE=new BlockPos(3,1,3);
    private static RetortBlockEntity retort(GameTestHelper h){h.setBlock(MACHINE,RealmAgriculture.RETORT.get());return (RetortBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(MACHINE));}
    private static void grainBatch(RetortBlockEntity entity){entity.setItem(0,new ItemStack(RealmAgriculture.GRAIN.get(),2));entity.setItem(6,new ItemStack(MineralEcology.UMBRAL_COAL.get()));}
    private static void chamber(GameTestHelper h){for(int x=0;x<=14;x++)for(int z=0;z<=14;z++)for(int y=0;y<=8;y++)h.setBlock(new BlockPos(x,y,z),x==0||x==14||z==0||z==14||y==0||y==8?Blocks.STONE:Blocks.AIR);}
    @GameTest(template="empty",timeoutTicks=160)
    public static void ownCropsGrowInZeroLightOnRedToxinAndRejectBothVanillaDirections(GameTestHelper h){
        chamber(h);h.setBlock(SOIL,RealmAgriculture.FARMLAND.get());h.setBlock(CROP,RealmAgriculture.GRAIN_CROP.get());h.setBlock(SOIL.east(3),Interstice.HEAVY_BLOCK.get());
        h.runAfterDelay(15,()->{
            var level=h.getLevel();var pos=h.absolutePos(CROP);var crop=RealmAgriculture.GRAIN_CROP.get();
            h.assertTrue(level.getRawBrightness(pos,0)==0,"Fixture does not prove genuinely dark growth");
            for(int i=0;i<100&&!crop.isMaxAge(level.getBlockState(pos));i++)level.getBlockState(pos).randomTick(level,pos,level.random);
            h.assertTrue(crop.isMaxAge(level.getBlockState(pos)),"Native chemical growth still requires sunlight");
            h.setBlock(SOIL,Blocks.FARMLAND);h.assertTrue(!crop.defaultBlockState().canSurvive(level,pos),"Native crop accepts vanilla farmland");
            h.setBlock(SOIL,MineralEcology.ROOT_LOAM.get());h.assertTrue(!crop.defaultBlockState().canSurvive(level,pos),"Unploughed own loam accepts a cultivated crop");
            h.setBlock(SOIL,RealmAgriculture.FARMLAND.get());h.assertTrue(!Blocks.WHEAT.defaultBlockState().canSurvive(level,pos),"Vanilla wheat accepts chemical farmland");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void staleMoistureWaterUpperToxinAndBonemealCannotBypassNutrition(GameTestHelper h){
        h.setBlock(SOIL,RealmAgriculture.FARMLAND.get().defaultBlockState().setValue(ToxicFarmlandBlock.MOISTURE,7));h.setBlock(CROP,RealmAgriculture.ROOT_CROP.get());
        var level=h.getLevel();var pos=h.absolutePos(CROP);var crop=RealmAgriculture.ROOT_CROP.get();
        h.setBlock(SOIL.east(3),Blocks.WATER);h.assertTrue(!crop.canGrow(level,pos,level.getBlockState(pos)),"Water hydrates native crops");
        h.setBlock(SOIL.east(3),Interstice.LIGHT_BLOCK.get());h.assertTrue(!crop.canGrow(level,pos,level.getBlockState(pos)),"Upper toxin hydrates native crops");
        h.setBlock(SOIL.east(3),Blocks.AIR);h.assertTrue(!crop.canGrow(level,pos,level.getBlockState(pos)),"Stale wet state bypasses missing liquid");
        h.assertTrue(!crop.isValidBonemealTarget(level,pos,level.getBlockState(pos)),"Bone meal bypasses mineral nutrition");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void ordinaryHoeAndOwnFertilizerPerformRealInteractions(GameTestHelper h){
        h.setBlock(SOIL,MineralEcology.ROOT_LOAM.get());var player=TestPlayers.create(h,new BlockPos(6,2,5),GameType.SURVIVAL);
        try{player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_HOE));var pos=h.absolutePos(SOIL);
            player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)));
            h.assertTrue(level(h).getBlockState(pos).is(RealmAgriculture.FARMLAND.get())&&player.getMainHandItem().getDamageValue()==1,"Native hoe integration did not create own farmland");
            h.setBlock(CROP,RealmAgriculture.GRAIN_CROP.get());var c=h.absolutePos(CROP);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(RealmAgriculture.FERTILIZER.get(),2));
            var use=new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(c),Direction.UP,c,false));player.getMainHandItem().useOn(use);
            h.assertTrue(player.getMainHandItem().getCount()==2&&RealmAgriculture.GRAIN_CROP.get().getAge(level(h).getBlockState(c))==0,"Dry fertilizer consumes or advances");
            h.setBlock(SOIL.east(3),Interstice.HEAVY_BLOCK.get());player.getMainHandItem().useOn(use);
            h.assertTrue(player.getMainHandItem().getCount()==1&&RealmAgriculture.GRAIN_CROP.get().getAge(level(h).getBlockState(c))==1,"Fertilizer did not use exactly one item for one real stage");h.succeed();
        }finally{TestPlayers.remove(player);}
    }
    private static net.minecraft.server.level.ServerLevel level(GameTestHelper h){return h.getLevel();}
    @GameTest(template="empty",timeoutTicks=100)
    public static void countedRecipesAllocateOverlappingIngredientsAndLoadRealData(GameTestHelper h){
        var plank=pro.erez.interstice.worldgen.GardenMaterials.PALEHEART_PLANKS.get().asItem();var other=Interstice.GLOOMCROWN_PLANKS.get().asItem();
        var tag=TagKey.create(Registries.ITEM,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Interstice.ID,"world_planks"));
        var recipe=new RetortRecipe(List.of(new RetortRecipe.Input(Ingredient.of(tag),1),new RetortRecipe.Input(Ingredient.of(plank),1)),List.of(new ItemStack(RealmAgriculture.WIRE.get())),20);
        var input=new RetortInput(List.of(new ItemStack(plank),new ItemStack(other),ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY));
        int[] take=recipe.allocation(input);h.assertTrue(take!=null&&take[0]==1&&take[1]==1,"Overlapping tags fail or duplicate ingredient counts");
        h.assertTrue(level(h).getRecipeManager().getAllRecipesFor(RealmAgriculture.RETORT_RECIPE_TYPE.get()).size()==8,"Missing actual data-driven retort operations");
        var ops=net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,level(h).registryAccess());
        var decoded=RetortRecipe.CODEC.codec().parse(ops,RetortRecipe.CODEC.codec().encodeStart(ops,recipe).getOrThrow()).getOrThrow();
        h.assertTrue(decoded.matches(input,level(h)),"Native recipe codec lost counted inputs");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=460)
    public static void realTicksReserveInputsSaveComponentsAndFinishExactlyOneBatch(GameTestHelper h){
        var entity=retort(h);var grain=new ItemStack(RealmAgriculture.GRAIN.get(),2);grain.set(DataComponents.CUSTOM_NAME,Component.literal("saved native grain"));entity.setItem(0,grain);entity.setItem(6,new ItemStack(MineralEcology.UMBRAL_COAL.get()));
        h.runAtTickTime(40,()->{
            h.assertTrue(entity.getItem(0).isEmpty()&&entity.data.get(0)>0&&entity.data.get(1)==200,"Real server ticker did not reserve a batch");
            var tag=entity.saveWithoutMetadata(level(h).registryAccess());var clone=new RetortBlockEntity(h.absolutePos(MACHINE),level(h).getBlockState(h.absolutePos(MACHINE)));clone.loadWithComponents(tag,level(h).registryAccess());
            level(h).setBlockEntity(clone);h.assertTrue(clone.data.get(0)==entity.data.get(0),"Block-entity reload reset pending progress");
        });
        h.runAtTickTime(270,()->{var reloaded=(RetortBlockEntity)level(h).getBlockEntity(h.absolutePos(MACHINE));
            h.assertTrue(reloaded.getItem(7).is(RealmAgriculture.FLOUR.get())&&reloaded.getItem(7).getCount()==2&&reloaded.getItem(8).is(RealmAgriculture.FIBER.get())&&reloaded.getItem(8).getCount()==1,"Saved batch did not finish once with both outputs");
            h.assertTrue(reloaded.data.get(2)==3000&&reloaded.getItem(6).isEmpty(),"Incorrect actual heat cost or coal duplication");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=460)
    public static void outputBlockingIsAtomicAndDoesNotBurnWaitingHeat(GameTestHelper h){
        var entity=retort(h);grainBatch(entity);
        h.runAtTickTime(30,()->{entity.setItem(8,new ItemStack(Items.COBBLESTONE,64));entity.setItem(9,new ItemStack(Items.COBBLESTONE,64));});
        int[] heat={-1};h.runAtTickTime(240,()->{h.assertTrue(entity.data.get(3)==3&&entity.getItem(7).isEmpty(),"Partial batch escaped blocked output");heat[0]=entity.data.get(2);});
        h.runAtTickTime(280,()->{h.assertTrue(entity.data.get(2)==heat[0],"Completed blocked batch still burns fuel");entity.removeItem(8,64);});
        h.runAtTickTime(310,()->{h.assertTrue(entity.getItem(7).getCount()==2&&entity.getItem(8).getCount()==1&&entity.data.get(2)==heat[0],"Atomic output recovery failed or spent more heat");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=130)
    public static void destroyingPendingRetortReturnsReservedComponentsOnlyOnce(GameTestHelper h){
        var entity=retort(h);var grain=new ItemStack(RealmAgriculture.GRAIN.get(),2);grain.set(DataComponents.CUSTOM_NAME,Component.literal("reserved proof"));entity.setItem(0,grain);entity.setItem(6,new ItemStack(MineralEcology.UMBRAL_COAL.get()));
        h.runAtTickTime(30,()->{level(h).setBlock(h.absolutePos(MACHINE),Blocks.AIR.defaultBlockState(),3);entity.release();
            var drops=level(h).getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(MACHINE)).inflate(3),e->e.getItem().is(RealmAgriculture.GRAIN.get()));
            h.assertTrue(drops.stream().mapToInt(e->e.getItem().getCount()).sum()==2&&drops.stream().allMatch(e->e.getItem().getHoverName().getString().equals("reserved proof")),"Pending removal lost components or duplicated reserved grain");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void actualCultivatorLootReplantsUsingOneSeedWithoutFortuneDuplication(GameTestHelper h){
        h.setBlock(SOIL,RealmAgriculture.FARMLAND.get());h.setBlock(CROP,RealmAgriculture.GRAIN_CROP.get().getStateForAge(4));var player=TestPlayers.create(h,new BlockPos(6,2,5),GameType.SURVIVAL);
        try{player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(RealmAgriculture.CULTIVATOR.get()));var pos=h.absolutePos(CROP);
            player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)));
            int dropped=level(h).getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2),e->e.getItem().is(RealmAgriculture.GRAIN.get())).stream().mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(level(h).getBlockState(pos).is(RealmAgriculture.GRAIN_CROP.get())&&RealmAgriculture.GRAIN_CROP.get().getAge(level(h).getBlockState(pos))==0&&dropped>=1&&dropped<=3&&player.getMainHandItem().getDamageValue()==1,"Cultivator failed ordinary loot/one-seed/one-durability rules");h.succeed();
        }finally{TestPlayers.remove(player);}
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void rawHumanFoodPoisonsButRetortFoodAndPriorFruitKeepSeparateRoles(GameTestHelper h){
        var player=TestPlayers.create(h,new BlockPos(6,2,6),GameType.SURVIVAL);
        try{player.getFoodData().setFoodLevel(0);new ItemStack(RealmAgriculture.ROOT.get()).finishUsingItem(level(h),player);
            h.assertTrue(player.hasEffect(MobEffects.POISON)&&player.getEffect(MobEffects.POISON).getDuration()==160&&player.hasEffect(MobEffects.WEAKNESS),"Human raw root does not apply its exact food cost");
            player.removeAllEffects();new ItemStack(RealmAgriculture.BREAD.get()).finishUsingItem(level(h),player);
            h.assertTrue(!player.hasEffect(MobEffects.POISON)&&player.getFoodData().getFoodLevel()==9,"Purified food still poisons or lost nutrition");
            h.assertTrue(!NativeFoodTolerance.canDigest(player,NativeFoodTolerance.FoodKind.ROOT)&&pro.erez.interstice.food.TideHeart.VISION_TICKS==800,"Future race is already enabled or accepted fruit was changed");h.succeed();
        }finally{TestPlayers.remove(player);}
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void reservoirKeepsOneRealBucketAcrossBreakAndNativeBlockItemPlacement(GameTestHelper h){
        var pos=h.absolutePos(new BlockPos(8,1,6));h.setBlock(new BlockPos(8,1,6),RealmAgriculture.RESERVOIR.get());h.setBlock(SOIL,RealmAgriculture.FARMLAND.get());
        var player=TestPlayers.create(h,new BlockPos(8,2,5),GameType.SURVIVAL);
        try{player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Interstice.RIFTSILVER_HEAVY_BUCKET.get()));var state=level(h).getBlockState(pos);
            state.useItemOn(player.getMainHandItem(),level(h),player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
            h.assertTrue(player.getMainHandItem().is(Interstice.RIFTSILVER_BUCKET.get())&&FarmHydration.scan(level(h),h.absolutePos(SOIL))==FarmHydration.State.WET,"Reservoir duplicated/lost the real container or lacks nutrition");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));player.gameMode.destroyBlock(pos);
            var drops=level(h).getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3),e->e.getItem().is(RealmAgriculture.RESERVOIR_ITEM.get()));
            h.assertTrue(drops.size()==1&&drops.getFirst().getItem().getCount()==1&&AgricultureItems.ReservoirItem.full(drops.getFirst().getItem()),"Breaking full tank lost/doubled the reservoir or manufactured another silver bucket");
            var saved=drops.getFirst().getItem().copy();drops.getFirst().discard();var next=h.absolutePos(new BlockPos(10,1,6));h.setBlock(new BlockPos(10,0,6),Blocks.STONE);player.setItemInHand(InteractionHand.MAIN_HAND,saved);
            player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(next.below()),Direction.UP,next.below(),false)));
            h.assertTrue(level(h).getBlockEntity(next) instanceof NutrientReservoirEntity restored&&restored.amount()==1000&&level(h).getBlockState(next).getValue(NutrientReservoirBlock.FILLED),"Native BlockItem placement did not restore serialized liquid");h.succeed();
        }finally{TestPlayers.remove(player);}
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void rareSeedPatchesStayBoundedAndOlderTerrainHasNoNewDecoration(GameTestHelper h){
        int found=0;
        for(long seed=0;seed<80&&found==0;seed++){
            var chunk=new ProtoChunk(new ChunkPos(0,0),UpgradeData.EMPTY,LevelHeightAccessor.create(0,256),level(h).registryAccess().registryOrThrow(Registries.BIOME),null);
            var garden=level(h).registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(RealmBiomes.PALE_GARDENS);chunk.fillBiomesFromNoise((x,y,z,s)->garden,Climate.empty());chunk.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.BIOMES);
            for(int x=0;x<16;x++)for(int z=0;z<16;z++)chunk.setBlockState(new BlockPos(x,34,z),z==8?Interstice.HEAVY_BLOCK.get().defaultBlockState():MineralEcology.ROOT_LOAM.get().defaultBlockState(),false);
            h.assertTrue(NativeCropPatches.generate(pro.erez.interstice.geometry.GeometryProfile.TALL,chunk,seed,3)==0,"Archived terrain receives new seed colonies");
            found=NativeCropPatches.generate(pro.erez.interstice.geometry.GeometryProfile.TALL,chunk,seed,4);
            if(found>0){int counted=0;for(int x=0;x<16;x++)for(int z=0;z<16;z++){var p=new BlockPos(x,35,z);if(chunk.getBlockState(p).getBlock() instanceof NativeCropBlock){h.assertTrue(chunk.getBlockState(p.below()).is(RealmAgriculture.FARMLAND.get()),"Wild crop bypasses own cultivated soil");counted++;}}
                h.assertTrue(found>=2&&found<=5&&counted==found,"Seed patch creates a carpet or writes outside its declared chunk");}
        }h.assertTrue(found>0,"Bounded native colony fixture found no planting material");h.succeed();
    }
}
