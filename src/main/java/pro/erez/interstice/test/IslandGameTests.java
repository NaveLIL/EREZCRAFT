package pro.erez.interstice.test;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.OceanLiquidBlock;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.worldgen.IslandChunkGenerator;

@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class IslandGameTests {
    private static final LevelHeightAccessor HEIGHT=LevelHeightAccessor.create(0,128);
    private static final ResourceKey<NoiseGeneratorSettings> SETTINGS=ResourceKey.create(Registries.NOISE_SETTINGS,ResourceLocation.fromNamespaceAndPath(Interstice.ID,"islands"));
    private record Fixture(IslandChunkGenerator generator,RandomState random) {}
    private static Fixture fixture(GameTestHelper h,long seed) {
        var registries=h.getLevel().registryAccess();
        var settings=registries.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(SETTINGS);
        var biome=registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.THE_END);
        return new Fixture(new IslandChunkGenerator(new FixedBiomeSource(biome),settings),RandomState.create(settings.value(),registries.registryOrThrow(Registries.NOISE).asLookup(),seed));
    }
    private static java.util.concurrent.CompletableFuture<ProtoChunk> generate(GameTestHelper h,Fixture f,int cx,int cz) {
        var chunk=new ProtoChunk(new ChunkPos(cx,cz),UpgradeData.EMPTY,HEIGHT,h.getLevel().registryAccess().registryOrThrow(Registries.BIOME),null);
        return f.generator.createBiomes(f.random,Blender.empty(),h.getLevel().structureManager(),chunk)
                .thenCompose(c->f.generator.fillFromNoise(Blender.empty(),f.random,h.getLevel().structureManager(),c))
                .thenApply(c->{IslandChunkGenerator.fillSeas(c);return (ProtoChunk)c;});
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void noiseProducesSeededLandAndRealGaps(GameTestHelper h) {
        Fixture a=fixture(h,20261006),b=fixture(h,76198123);
        int landColumns=0,voidColumns=0,different=0;
        for(int x=-96;x<=96;x+=16) for(int z=-96;z<=96;z+=16) {
            var first=a.generator.getBaseColumn(x,z,HEIGHT,a.random);
            var second=b.generator.getBaseColumn(x,z,HEIGHT,b.random);
            boolean land=false;
            for(int y=41;y<=77;y++) {
                if(!first.getBlock(y).isAir()) land=true;
                if(!first.getBlock(y).equals(second.getBlock(y))) different++;
            }
            if(land) landColumns++;else voidColumns++;
        }
        h.assertTrue(landColumns>0,"Native density must produce islands");
        h.assertTrue(voidColumns>0,"Native density must leave genuine void between islands; land="+landColumns);
        h.assertTrue(different>0,"Changing world seed must change island terrain");
        System.out.println("ISLAND_SAMPLE land_columns="+landColumns+" void_columns="+voidColumns+" seed_differences="+different);
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void chunksMatchGlobalColumnsAndRespectBothSeas(GameTestHelper h) {
        Fixture f=fixture(h,20261006);
        var pending=generate(h,f,0,0);
        h.succeedWhen(()->{
        h.assertTrue(pending.isDone(),"Waiting for native chunk generation");
        ProtoChunk chunk=pending.join();
        var pos=new BlockPos.MutableBlockPos();
        for(int x=0;x<16;x++) for(int z=0;z<16;z++) {
            var column=f.generator.getBaseColumn(x,z,HEIGHT,f.random);
            for(int y=0;y<128;y++) {
                pos.set(x,y,z);var block=chunk.getBlockState(pos);
                h.assertTrue(block.equals(column.getBlock(y)),"Chunk/base-column disagreement at "+pos);
                if(!block.getFluidState().isEmpty()) {
                    if(y<=34) h.assertTrue(block.is(Interstice.HEAVY_BLOCK.get()),"Bottom sea must be heavy toxin");
                    else h.assertTrue(block.getBlock() instanceof OceanLiquidBlock,"Upper sea must use stable shaped toxin");
                } else if(!block.isAir() && !block.is(Blocks.BEDROCK)) {
                    h.assertTrue(y>=41,"Island must leave at least 6 blocks above lower-sea voxel top");
                    h.assertTrue(y+1<=SeaSurface.cellMinimum(x,z)-6,"Island must leave at least 6 blocks below the real upper-sea surface");
                }
            }
            h.assertTrue(chunk.getBlockState(new BlockPos(x,0,z)).is(Blocks.BEDROCK),"Bottom world boundary missing");
            h.assertTrue(chunk.getBlockState(new BlockPos(x,127,z)).is(Blocks.BEDROCK),"Upper world boundary missing");
        }
        });
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void adjacentChunksAreIndependentOfGenerationOrder(GameTestHelper h) {
        Fixture f=fixture(h,20261006);
        var first=generate(h,f,-1,0).thenCombine(generate(h,f,0,0),(w,e)->new ProtoChunk[]{w,e});
        var pending=first.thenCompose(original->generate(h,f,0,0).thenCombine(generate(h,f,-1,0),(e,w)->new ProtoChunk[]{original[0],original[1],w,e}));
        h.succeedWhen(()->{
        h.assertTrue(pending.isDone(),"Waiting for reordered chunk generation");
        ProtoChunk[] chunks=pending.join();
        var west=chunks[0];var east=chunks[1];var westAgain=chunks[2];var eastAgain=chunks[3];
        var pos=new BlockPos.MutableBlockPos();
        for(int x=-16;x<16;x++) for(int z=0;z<16;z++) for(int y=0;y<128;y++) {
            pos.set(x,y,z);
            var original=x<0 ? west : east;var repeated=x<0 ? westAgain : eastAgain;
            h.assertTrue(original.getBlockState(pos).equals(repeated.getBlockState(pos)),"Chunk order changed terrain at "+pos);
        }
        // Both sides of the chunk border must agree with the same global-coordinate density field.
        for(int x:new int[]{-1,0}) for(int z=0;z<16;z++) {
            var column=f.generator.getBaseColumn(x,z,HEIGHT,f.random);
            for(int y=41;y<=77;y++) {
                pos.set(x,y,z);h.assertTrue((x<0?west:east).getBlockState(pos).equals(column.getBlock(y)),"Chunk-border density seam at "+pos);
            }
        }
        });
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void generatorCodecRoundTripPreservesSeededTerrain(GameTestHelper h) {
        Fixture f=fixture(h,20261006);
        var ops=RegistryOps.create(JsonOps.INSTANCE,h.getLevel().registryAccess());
        var json=IslandChunkGenerator.CODEC.codec().encodeStart(ops,f.generator).getOrThrow();
        var restored=IslandChunkGenerator.CODEC.codec().parse(ops,json).getOrThrow();
        for(int x:new int[]{-33,0,17,100}) {
            var a=f.generator.getBaseColumn(x,7,HEIGHT,f.random);
            var b=restored.getBaseColumn(x,7,HEIGHT,f.random);
            for(int y=0;y<128;y++) h.assertTrue(a.getBlock(y).equals(b.getBlock(y)),"Saved/reloaded generator changed terrain");
        }
        h.succeed();
    }
}
