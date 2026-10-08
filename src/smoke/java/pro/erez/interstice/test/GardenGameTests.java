package pro.erez.interstice.test;

import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.*;

@GameTestHolder("interstice_gardens")
@PrefixGameTestTemplate(false)
public final class GardenGameTests {
    private static net.minecraft.server.level.ServerLevel world(GameTestHelper h,GeometryProfile profile){return java.util.Objects.requireNonNull(h.getLevel().getServer().getLevel(profile.equals(GeometryProfile.TALL)?IslandWorld.TALL_WORLD:IslandWorld.WORLD));}
    private static ProtoChunk platform(GameTestHelper h,GeometryProfile profile){
        var p=new ProtoChunk(new ChunkPos(0,0),UpgradeData.EMPTY,LevelHeightAccessor.create(profile.minY(),profile.height()),h.getLevel().registryAccess().registryOrThrow(Registries.BIOME),null);
        var biome=h.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(RealmBiomes.PALE_GARDENS);
        p.fillBiomesFromNoise((x,y,z,s)->biome,Climate.empty());p.setPersistedStatus(ChunkStatus.BIOMES);
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)p.setBlockState(new BlockPos(x,55,z),Interstice.ABYSSAL_TURF.get().defaultBlockState(),false);
        return p;
    }
    private static com.google.gson.JsonObject definitionJson(){
        try(var in=GardenGameTests.class.getClassLoader().getResourceAsStream("data/interstice/interstice_trees/paleheart.json")){
            return JsonParser.parseString(new String(java.util.Objects.requireNonNull(in).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void catalogIsReloadedAndRejectsInvalidPathsAndUnboundedCounts(GameTestHelper h){
        var d=GardenTreeDefinitions.get(GardenTreeDefinitions.PALEHEART);h.assertTrue(d.variants().size()==3,"Three alien presets must be loaded by the real resource listener");
        var imported=definitionJson();var entry=imported.getAsJsonArray("variants").get(0).getAsJsonObject();
        entry.addProperty("jo_code",JoShape.encodePath(entry.get("branch_path").getAsString()));entry.remove("branch_path");
        var converted=GardenTreeDefinitions.Definition.CODEC.parse(JsonOps.INSTANCE,imported).getOrThrow();
        h.assertTrue(converted.variants().get(0).code().equals(d.variants().get(0).code()),"Imported Dynamic Trees JoCode changed the prescribed branch shape");
        var bad=definitionJson();bad.addProperty("attempts",100);h.assertTrue(GardenTreeDefinitions.Definition.CODEC.parse(JsonOps.INSTANCE,bad).error().isPresent(),"Unbounded generation attempts accepted");
        for(String path:List.of("UU[EE","UU]","UU?","U".repeat(40))) {
            bad=definitionJson();bad.getAsJsonArray("variants").get(0).getAsJsonObject().addProperty("branch_path",path);
            h.assertTrue(GardenTreeDefinitions.Definition.CODEC.parse(JsonOps.INSTANCE,bad).error().isPresent(),"Invalid or excessive branch path accepted: "+path);
        }
        h.assertTrue(GardenTreeDefinitions.get(GardenTreeDefinitions.PALEHEART)==d,"Invalid parsing modified the live catalog");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void joCodeRotationAndForkReturnProduceConnectedAlienSkeletons(GameTestHelper h){
        var root=new BlockPos(7,56,7);var forms=new HashSet<java.util.Set<BlockPos>>();
        for(var variant:GardenTreeDefinitions.get(GardenTreeDefinitions.PALEHEART).variants())for(int turn=0;turn<4;turn++){
            var s=JoShape.draw(JoShape.encodePath(variant.branchPath()),root,turn,0);
            var reached=new HashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();reached.add(root);queue.add(root);
            while(!queue.isEmpty()){var p=queue.remove();for(var dir:Direction.values()){var n=p.relative(dir);if(s.logs().containsKey(n)&&reached.add(n))queue.add(n);}}
            h.assertTrue(s.logs().containsKey(root)&&reached.size()==s.logs().size(),"Tree has unsupported or disconnected logs");
            h.assertTrue(s.ends().size()>=2,"Alien branch network needs separate pendant ends");forms.add(s.logs().keySet());
        }
        h.assertTrue(forms.size()>=6,"Forms and rotations collapsed to a familiar single silhouette");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void completePlansAreDeterministicAndHaveLivingHangingCrowns(GameTestHelper h){
        var p=platform(h,GeometryProfile.TALL);var root=new BlockPos(7,56,7);int forms=0;
        for(var variant:GardenTreeDefinitions.get(GardenTreeDefinitions.PALEHEART).variants()){
            var a=GardenTrees.plan(h.getLevel(),p::getBlockState,root,variant,RandomSource.create(20261006));
            var b=GardenTrees.plan(h.getLevel(),p::getBlockState,root,variant,RandomSource.create(20261006));
            h.assertTrue(!a.isEmpty()&&a.equals(b),"Tree plan is empty or seed-unstable");int leaves=0,logs=0;
            for(var state:a.values()){
                if(state.is(GardenMaterials.PALEHEART_LEAVES.get())){leaves++;h.assertTrue(state.getValue(LeavesBlock.DISTANCE)<7&&!state.getValue(LeavesBlock.PERSISTENT),"Crown relies on forced persistent leaves");}
                if(state.is(GardenMaterials.PALEHEART_LOG.get()))logs++;
            }
            h.assertTrue(leaves>20&&logs>12,"Pendant tree lacks a substantial crown or branch network");
            h.assertTrue(GardenTrees.fits(a,p::getBlockState,pos->IslandChunkGenerator.landAllowed(GeometryProfile.TALL,pos.getX(),pos.getY(),pos.getZ()),root),"A valid full plan was rejected");forms++;
        }
        System.out.println("GARDEN_PLANS forms="+forms);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void obstaclesVoidAndSeaBandsRejectTheWholeTreeBeforeWriting(GameTestHelper h){
        var profile=GeometryProfile.LEGACY;var root=new BlockPos(7,56,7);var p=platform(h,profile);
        var variant=GardenTreeDefinitions.get(GardenTreeDefinitions.PALEHEART).variants().get(0);
        var cells=GardenTrees.plan(h.getLevel(),p::getBlockState,root,variant,RandomSource.create(40));
        h.assertTrue(!cells.isEmpty(),"Test has no valid tree");
        var blocked=cells.keySet().stream().filter(pos->!pos.equals(root)).findFirst().orElseThrow();p.setBlockState(blocked,Blocks.CHEST.defaultBlockState(),false);
        h.assertTrue(!GardenTrees.fits(cells,p::getBlockState,pos->true,root)&&p.getBlockState(root).isAir(),"Obstruction left a defective stem or got overwritten");
        p.setBlockState(blocked,Blocks.AIR.defaultBlockState(),false);p.setBlockState(root.below(),Blocks.AIR.defaultBlockState(),false);
        h.assertTrue(!GardenTrees.fits(cells,p::getBlockState,pos->true,root),"Tree accepted an unsupported root");
        p.setBlockState(root.below(),Interstice.ABYSSAL_TURF.get().defaultBlockState(),false);
        h.assertTrue(!GardenTrees.fits(cells,p::getBlockState,pos->pos.getX()>=7,root),"Partial boundary-clipped crown accepted");
        for(var geometry:List.of(GeometryProfile.LEGACY,GeometryProfile.TALL)){
            var highRoot=root.atY(geometry.maxLand()-1);
            var high=GardenTrees.plan(h.getLevel(),pos->pos.equals(highRoot.below())?Interstice.ABYSSAL_TURF.get().defaultBlockState():Blocks.AIR.defaultBlockState(),highRoot,variant,RandomSource.create(40));
            h.assertTrue(!GardenTrees.fits(high,pos->pos.equals(highRoot.below())?Interstice.ABYSSAL_TURF.get().defaultBlockState():Blocks.AIR.defaultBlockState(),pos->IslandChunkGenerator.landAllowed(geometry,pos.getX(),pos.getY(),pos.getZ()),highRoot),"Tree breached the upper sea band");
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void saplingGrowsWithoutSkyLightAndBlockedGrowthPreservesIt(GameTestHelper h){
        var level=world(h,GeometryProfile.TALL);var root=new BlockPos(8199,56,8199);
        for(int dx=-7;dx<=7;dx++)for(int dz=-7;dz<=7;dz++){
            level.setBlock(root.offset(dx,-1,dz),Interstice.ABYSSAL_TURF.get().defaultBlockState(),3);
            for(int dy=0;dy<20;dy++)level.setBlock(root.offset(dx,dy,dz),Blocks.AIR.defaultBlockState(),3);
        }
        level.setBlock(root,GardenMaterials.PALEHEART_SAPLING.get().defaultBlockState(),3);
        level.setBlock(root.above(),Blocks.GLASS.defaultBlockState(),3);
        h.assertTrue(!GardenTrees.grow(level,root,RandomSource.create(77))&&level.getBlockState(root).is(GardenMaterials.PALEHEART_SAPLING.get()),"Blocked growth destroyed the sapling or partially wrote a stem");
        level.setBlock(root.above(),Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(level.getBrightness(LightLayer.SKY,root)==0,"Growth fixture accidentally has sunlight");
        h.assertTrue(GardenTrees.grow(level,root,RandomSource.create(77))&&level.getBlockState(root).is(GardenMaterials.PALEHEART_LOG.get()),"Sapling cannot grow in its dark dimension");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void oldTwoBiomeLayoutUpgradesWithoutChangingTheIslands(GameTestHelper h){
        var level=world(h,GeometryProfile.TALL);var registry=level.registryAccess().registryOrThrow(Registries.BIOME);var all=Climate.Parameter.span(-2,2);
        var old=MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(List.of(
                Pair.of(Climate.parameters(all,all,Climate.Parameter.span(-2,.08F),all,all,all,0),registry.getHolderOrThrow(RealmBiomes.ASH_ISLANDS)),
                Pair.of(Climate.parameters(all,all,Climate.Parameter.span(.08F,2),all,all,all,0),registry.getHolderOrThrow(RealmBiomes.STONE_VAULTS)))));
        var upgraded=RealmBiomes.upgradeLegacy(old,registry.asLookup());
        h.assertTrue(upgraded.possibleBiomes().stream().anyMatch(b->b.is(RealmBiomes.PALE_GARDENS)),"Saved M13 generator does not upgrade to gardens");
        for(float humidity:new float[]{-1,0,1})h.assertTrue(upgraded.getNoiseBiome(0,0,0,Climate.empty())!=null,"Missing biome source");
        var generator=(IslandChunkGenerator)level.getChunkSource().getGenerator();var ops=RegistryOps.create(JsonOps.INSTANCE,level.registryAccess());
        var restored=IslandChunkGenerator.CODEC.codec().parse(ops,IslandChunkGenerator.CODEC.codec().encodeStart(ops,generator).getOrThrow()).getOrThrow();
        h.assertTrue(restored.geometry().equals(GeometryProfile.TALL),"Biome extension changed the geometry profile");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=600)
    public static void actualNewChunksContainGardensTreesAndTheOriginalGreyTurf(GameTestHelper h){
        for(var profile:List.of(GeometryProfile.LEGACY,GeometryProfile.TALL)){
            var level=world(h,profile);var generator=(IslandChunkGenerator)level.getChunkSource().getGenerator();var sampler=level.getChunkSource().randomState().sampler();boolean found=false;
            for(int radius=0;radius<=20&&!found;radius++)for(int cx=-radius;cx<=radius&&!found;cx++)for(int cz=-radius;cz<=radius&&!found;cz++){
                if(Math.max(Math.abs(cx),Math.abs(cz))!=radius||!generator.getBiomeSource().getNoiseBiome(cx*4+2,16,cz*4+2,sampler).is(RealmBiomes.PALE_GARDENS))continue;
                var chunk=level.getChunk(cx,cz);int logs=0,leaves=0,turf=0,rock=0;
                for(int x=cx*16;x<cx*16+16;x++)for(int z=cz*16;z<cz*16+16;z++)for(int y=profile.minLand();y<=profile.maxLand();y++){
                    var p=new BlockPos(x,y,z);var s=chunk.getBlockState(p);
                    if(s.is(GardenMaterials.PALEHEART_LOG.get()))logs++;
                    if(s.is(GardenMaterials.PALEHEART_LEAVES.get()))leaves++;
                    if(s.is(Interstice.ABYSSAL_TURF.get()))turf++;
                    if(s.is(GardenMaterials.PALESTONE.get()))rock++;
                    if(s.is(GardenMaterials.PALEHEART_LOG.get())||s.is(GardenMaterials.PALEHEART_LEAVES.get())||s.is(GardenMaterials.PALE_FERN.get())||s.is(GardenMaterials.PALE_LITTER.get()))
                        h.assertTrue(IslandChunkGenerator.landAllowed(profile,x,y,z),"Garden feature breached a sea clearance");
                }
                if(logs>12&&leaves>20&&turf>40){found=true;System.out.println("NATURAL_PALE_GARDEN height="+profile.height()+" chunk="+chunk.getPos()+" logs="+logs+" leaves="+leaves+" grey_turf="+turf+" pale_rock="+rock);}
            }
            h.assertTrue(found,"No naturally planted grove with preserved grey turf, height="+profile.height());
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void materialLootToolsAndWoodRecipesWork(GameTestHelper h){
        var tool=new ItemStack(Items.IRON_PICKAXE);var point=h.absolutePos(new BlockPos(2,1,2));
        for(var block:List.of(GardenMaterials.PALESTONE.get(),GardenMaterials.PALESTONE_BRICKS.get(),GardenMaterials.PALESTONE_SLAB.get(),GardenMaterials.PALESTONE_STAIRS.get(),GardenMaterials.PALESTONE_WALL.get())){
            h.assertTrue(block.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE)&&tool.isCorrectToolForDrops(block.defaultBlockState()),"Material has no usable mining tag");
            h.assertTrue(Block.getDrops(block.defaultBlockState(),h.getLevel(),point,null,null,tool).stream().anyMatch(s->s.is(block.asItem())),"Material lacks actual loot");
        }
        var grid=CraftingInput.of(1,1,List.of(new ItemStack(GardenMaterials.PALEHEART_LOG.get())));
        var recipe=h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,h.getLevel()).orElseThrow().value();
        var result=recipe.assemble(grid,h.getLevel().registryAccess());h.assertTrue(result.is(GardenMaterials.PALEHEART_PLANKS.get().asItem())&&result.getCount()==4,"Log cannot become four planks");
        h.assertTrue(GardenMaterials.PALEHEART_LEAVES.get().defaultBlockState().is(BlockTags.LEAVES)&&GardenMaterials.PALEHEART_LOG.get().defaultBlockState().is(BlockTags.LOGS),"Living leaf/log tags missing");
        var shears=new ItemStack(Items.SHEARS);h.assertTrue(Block.getDrops(GardenMaterials.PALEHEART_LEAVES.get().defaultBlockState(),h.getLevel(),point,null,null,shears).stream().anyMatch(s->s.is(GardenMaterials.PALEHEART_LEAVES.get().asItem())),"Shears cannot collect the canopy");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void hangingVinesClimbGrowAndCollapseBelowABrokenLink(GameTestHelper h){
        var level=h.getLevel();var top=h.absolutePos(new BlockPos(4,9,4));var vine=GardenMaterials.PALE_VINE.get();
        level.setBlock(top.above(),GardenMaterials.PALEHEART_LOG.get().defaultBlockState(),3);
        for(int i=0;i<4;i++)level.setBlock(top.below(i),vine.defaultBlockState().setValue(GardenVineBlock.SECTION,(i&1)+(i>=2?2:0)),3);
        h.assertTrue(level.getBlockState(top).is(BlockTags.CLIMBABLE),"Vine is decorative instead of climbable");
        var actor=net.minecraft.world.entity.EntityType.ARMOR_STAND.create(level);java.util.Objects.requireNonNull(actor).moveTo(top.getX()+.5,top.getY(),top.getZ()+.5,0,0);
        h.assertTrue(actor.onClimbable(),"A real living entity does not recognise our climbing vine");
        vine.performBonemeal(level,RandomSource.create(1),top.below(3),level.getBlockState(top.below(3)));
        h.assertTrue(level.getBlockState(top.below(4)).is(vine),"Bone meal did not extend a supported hanging tip");
        var shears=new ItemStack(Items.SHEARS);h.assertTrue(Block.getDrops(vine.defaultBlockState(),level,top,null,null,shears).stream().anyMatch(s->s.is(vine.asItem())),"Shears did not collect vine material");
        level.destroyBlock(top.below(1),false);
        h.assertTrue(level.getBlockState(top.below(2)).isAir()&&level.getBlockState(top.below(4)).isAir(),"Broken chain left hanging unsupported segments");
        h.assertTrue(level.getBlockState(top).is(vine),"Breaking a lower link deleted the supported top");h.succeed();
    }
}
