package pro.erez.interstice.test;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.gametest.*;
import noobanidus.mods.lootr.common.api.LootrAPI;
import noobanidus.mods.lootr.common.block.entity.LootrBarrelBlockEntity;
import noobanidus.mods.lootr.common.data.LootrSavedData;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.*;

@GameTestHolder("interstice_ethereal")
@PrefixGameTestTemplate(false)
public final class EtherealComplexGameTests {
    private static final long[] SEEDS={0,1,-1,20261006,76198123,4294967297L};
    private static final BlockPos BASE=new BlockPos(-200005,80,200009);
    private static ServerLevel world(GameTestHelper h){return Objects.requireNonNull(h.getLevel().getServer().getLevel(IslandWorld.TENSION_WORLD));}
    private static RealmComplexStructuresV6.Site fixtureSite(GameTestHelper h,ResourceLocation template,int rotation){
        var level=world(h);var p=RealmComplexStructuresV6.plan(level.getStructureManager(),level.registryAccess(),template);
        return new RealmComplexStructuresV6.Site(new ChunkPos(BASE),template,BASE,rotation,rotation%2==0?p.width():p.depth(),p.height(),rotation%2==0?p.depth():p.width());
    }
    private static void clear(ServerLevel level,RealmComplexStructuresV6.Site site){
        for(var cp:site.chunks()){
            var chunk=level.getChunk(cp.x,cp.z);
            for(int x=cp.getMinBlockX();x<=cp.getMaxBlockX();x++)for(int z=cp.getMinBlockZ();z<=cp.getMaxBlockZ();z++)
                for(int y=74;y<=104;y++)chunk.setBlockState(new BlockPos(x,y,z),y==79?Interstice.RIFTSTONE.get().defaultBlockState():Blocks.AIR.defaultBlockState(),false);
        }
    }
    private static BoundingBox writable(ChunkPos p){return new BoundingBox(p.getMinBlockX(),1,p.getMinBlockZ(),p.getMaxBlockX(),254,p.getMaxBlockZ());}
    private static void slice(ServerLevel level,RealmComplexStructuresV6.ComplexPiece piece,ChunkPos cp){piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(1),writable(cp),cp,BASE);}
    @GameTest(template="empty",batch="complex_models",timeoutTicks=2400)
    public static void allSevenPrefabsCrossChunksInFourRotationsWithActualLootrCaches(GameTestHelper h){
        var level=world(h);h.assertTrue(RealmComplexStructuresV6.templates().size()==7,"Original temple plus two layouts for three camp sizes are required");
        for(var id:RealmComplexStructuresV6.templates())for(int turn=0;turn<4;turn++){
            var site=fixtureSite(h,id,turn);clear(level,site);var piece=new RealmComplexStructuresV6.ComplexPiece(site);
            var chunks=site.chunks();h.assertTrue(chunks.size()>1&&chunks.size()<=16,"Complex does not have bounded multi-chunk coverage");
            for(var cp:chunks){
                slice(level,piece,cp);
                for(var other:chunks)if(!other.equals(cp)){
                    // Unvisited slices remain untouched; no write to a neighbour from this feature chunk.
                    if(chunks.indexOf(other)>chunks.indexOf(cp))for(int x=other.getMinBlockX();x<=other.getMaxBlockX();x++)for(int z=other.getMinBlockZ();z<=other.getMaxBlockZ();z++)
                        h.assertTrue(level.getBlockState(new BlockPos(x,80,z)).isAir(),"A slice wrote a neighbour chunk early");
                }
            }
            var plan=RealmComplexStructuresV6.plan(level.getStructureManager(),level.registryAccess(),id);int caches=0;
            for(var cell:plan.cells()){
                var at=site.origin().offset(RealmComplexStructuresV6.rotated(cell.local(),turn,plan));var state=level.getBlockState(at);
                h.assertTrue(StructureLoot.matches(cell.state(),state),"Missing/cut rotated source cell "+id+" "+turn+" "+at);
                if(cell.data()!=null){
                    caches++;var entity=level.getBlockEntity(at);h.assertTrue(entity instanceof LootrBarrelBlockEntity,"Cache lost its upstream entity across a seam");
                    var barrel=(LootrBarrelBlockEntity)entity;h.assertTrue(barrel.getLootTable()!=null&&barrel.getLootTable().location().toString().equals(cell.data().getString("LootTable")),"Actual cache NBT/table did not load");
                }
                h.assertTrue(level.getBlockState(new BlockPos(at.getX(),79,at.getZ())).is(Interstice.RIFTSTONE.get()),"Construction excavated its prepared ground");
            }
            h.assertTrue(caches>0,"Complex has no loot");
            System.out.println("ETHEREAL_COMPLEX_PREPARED template="+id+" turn="+turn+" chunks="+chunks.size()+" cells="+plan.cells().size()+" caches="+caches+" scope=prepared_native_piece_not_natural_generation");
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="complex_replay",timeoutTicks=400)
    public static void completedSlicesSurvivePieceSerializationAndNeverRebuildOrRefill(GameTestHelper h){
        var level=world(h);var site=fixtureSite(h,RealmComplexStructuresV6.id("temple"),1);clear(level,site);var piece=new RealmComplexStructuresV6.ComplexPiece(site);var cp=site.chunks().getFirst();slice(level,piece,cp);
        var plan=RealmComplexStructuresV6.plan(level.getStructureManager(),level.registryAccess(),site.template());
        var cell=plan.cells().stream().filter(c->c.data()==null&&new ChunkPos(site.origin().offset(RealmComplexStructuresV6.rotated(c.local(),1,plan))).equals(cp)).findFirst().orElseThrow();
        var at=site.origin().offset(RealmComplexStructuresV6.rotated(cell.local(),1,plan));level.setBlock(at,Blocks.AIR.defaultBlockState(),2);
        var saved=piece.createTag(StructurePieceSerializationContext.fromLevel(level));var cold=new RealmComplexStructuresV6.ComplexPiece(saved);
        slice(level,cold,cp);h.assertTrue(level.getBlockState(at).isAir(),"Saved completed slice restored a removed source block");
        for(var remaining:site.chunks().subList(1,site.chunks().size()))slice(level,cold,remaining);
        h.assertTrue(cold.createTag(StructurePieceSerializationContext.fromLevel(level)).getLongArray("CompletedSlices").length==site.chunks().size(),"Partial-save recovery omitted remaining chunks");
        h.succeed();
    }
    @GameTest(template="empty",batch="complex_order",timeoutTicks=800)
    public static void reverseAndShuffledSliceOrdersPreserveAllCellsAndLootSeeds(GameTestHelper h){
        var level=world(h);var site=fixtureSite(h,RealmComplexStructuresV6.id("camp_large_0"),3);var expected=new HashMap<BlockPos,String>();
        for(int order=0;order<3;order++){
            clear(level,site);var piece=new RealmComplexStructuresV6.ComplexPiece(site);var chunks=new ArrayList<>(site.chunks());
            if(order==1)Collections.reverse(chunks);if(order==2)Collections.shuffle(chunks,new Random(3912));
            for(var cp:chunks)slice(level,piece,cp);
            var plan=RealmComplexStructuresV6.plan(level.getStructureManager(),level.registryAccess(),site.template());
            for(var cell:plan.cells()){
                var at=site.origin().offset(RealmComplexStructuresV6.rotated(cell.local(),3,plan));var state=level.getBlockState(at);String signature=state.toString();
                if(cell.data()!=null){var be=(LootrBarrelBlockEntity)level.getBlockEntity(at);signature+="/"+be.getLootTable()+"/"+be.getLootTableSeed();}
                if(order==0)expected.put(at,signature);else h.assertTrue(expected.get(at).equals(signature),"Chunk request order changed geometry/table/seed at "+at);
            }
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="complex_terrain",timeoutTicks=2400)
    public static void sixFixedSeedsHaveWholeSiteHostPlansWithoutTouchingNativeNoise(GameTestHelper h){
        var level=world(h);
        for(long seed:SEEDS){
            var f=NativeTerrainV6GameTests.fixture(h,seed);RealmComplexStructuresV6.Site found=null;int rejected=0;
            for(int x=-8;x<=8&&found==null;x++)for(int z=-8;z<=8&&found==null;z++){
                var slot=RealmStructuresV5.slot(seed,x,z);if(slot==null||!RealmComplexStructuresV6.scheduled(seed,slot.chunk()))continue;
                var site=RealmComplexStructuresV6.site(seed,slot.chunk(),f.generator(),f.random(),level.getStructureManager(),level.registryAccess(),level);
                if(site.isPresent())found=site.get();else rejected++;
            }
            h.assertTrue(found!=null,"No valid whole complex in fixed 17x17-cell window, seed="+seed);
            var chunks=NativeTerrainV6GameTests.await(h,NativeTerrainV6GameTests.serial(h,f,found.chunks()),180);var indexed=NativeTerrainV6GameTests.index(chunks);
            var plan=RealmComplexStructuresV6.plan(level.getStructureManager(),level.registryAccess(),found.template());
            for(var cell:plan.cells()){
                var at=found.origin().offset(RealmComplexStructuresV6.rotated(cell.local(),found.rotation(),plan));
                h.assertTrue(indexed.get(new ChunkPos(at).toLong()).getBlockState(at).isAir(),"Whole-site plan overlaps actual native NOISE rock");
                h.assertTrue(at.getY()>=GeometryProfile.TALL.minLand()&&at.getY()+1<=pro.erez.interstice.SeaSurface.cellMinimum(GeometryProfile.TALL,at.getX(),at.getZ(),true)-GeometryProfile.TALL.clearance(),"Complex cell breaches a sea envelope");
            }
            var state=f.generator().createState(level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).asLookup(),f.random(),seed);
            // STRUCTURE_STARTS is checked separately from actual NOISE, never labelled FULL.
            var anchor=new ProtoChunk(found.anchor(),UpgradeData.EMPTY,LevelHeightAccessor.create(0,256),level.registryAccess().registryOrThrow(Registries.BIOME),null);
            f.generator().createStructures(level.registryAccess(),state,level.structureManager(),anchor,level.getStructureManager());
            h.assertTrue(anchor.getAllStarts().values().stream().anyMatch(StructureStart::isValid),"Native generator did not create a real saved structure start");
            System.out.println("ETHEREAL_COMPLEX_NATIVE_NOISE seed="+seed+" template="+found.template()+" origin="+found.origin()+" chunks="+chunks.size()+" rejected_before_valid="+rejected);
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="complex_private_loot",timeoutTicks=400)
    public static void differentPlayersReceivePersonalLootFromEveryCacheInLargeCamp(GameTestHelper h){
        var level=world(h);var site=fixtureSite(h,RealmComplexStructuresV6.id("camp_large_1"),0);clear(level,site);var piece=new RealmComplexStructuresV6.ComplexPiece(site);for(var cp:site.chunks())slice(level,piece,cp);
        var a=TestPlayers.create(h,new BlockPos(2,2,3),GameType.SURVIVAL);var b=TestPlayers.create(h,new BlockPos(3,2,3),GameType.SURVIVAL);
        try{int checked=0;var plan=RealmComplexStructuresV6.plan(level.getStructureManager(),level.registryAccess(),site.template());
            for(var cell:plan.cells())if(cell.data()!=null){var at=site.origin().offset(cell.local());var barrel=(LootrBarrelBlockEntity)level.getBlockEntity(at);var first=LootrAPI.getInventory(barrel,a);var second=LootrAPI.getInventory(barrel,b);
                h.assertTrue(first!=second&&!first.isEmpty()&&!second.isEmpty(),"A large-camp cache is shared or empty");var before=second.saveToTag(level.registryAccess()).toString();first.clearContent();first.setChanged();
                h.assertTrue(second.saveToTag(level.registryAccess()).toString().equals(before),"A depleted B's cache");
                var saved=((LootrSavedData)LootrAPI.getData(barrel)).save(new CompoundTag(),level.registryAccess());var cold=LootrSavedData.load(saved,level.registryAccess());
                h.assertTrue(cold.getInventory(a.getUUID()).isEmpty()&&!cold.getInventory(b.getUUID()).isEmpty(),"Native save lost personal claims");checked++;
            }h.assertTrue(checked>=9,"Large camp lost its independent source caches");
        }finally{TestPlayers.remove(a);TestPlayers.remove(b);}h.succeed();
    }
}
