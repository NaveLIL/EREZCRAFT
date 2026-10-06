package pro.erez.interstice.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.OceanLiquidBlock;
import pro.erez.interstice.SeaSurface;

/** Vanilla blended-noise islands, followed by a deterministic material layer for the two toxic seas. */
public final class IslandChunkGenerator extends NoiseBasedChunkGenerator {
    public static final int LOWER_SEA_TOP=34;
    public static final int CLEARANCE=6;
    public static final int MIN_LAND=LOWER_SEA_TOP+1+CLEARANCE;
    public static final int MAX_LAND=SeaSurface.MINIMUM-CLEARANCE-1;
    public static final int ROOF=127;
    public static final MapCodec<IslandChunkGenerator> CODEC=RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(IslandChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(IslandChunkGenerator::generatorSettings)
    ).apply(instance,IslandChunkGenerator::new));

    public IslandChunkGenerator(BiomeSource biome,Holder<NoiseGeneratorSettings> settings) {super(biome,settings);}
    @Override protected MapCodec<? extends ChunkGenerator> codec() {return CODEC;}

    public static boolean landAllowed(int x,int y,int z) {
        return landAllowed(y,SeaSurface.cellMinimum(x,z));
    }
    private static boolean landAllowed(int y,double upperSurface) {
        return y>=MIN_LAND && y<=MAX_LAND && y+1<=upperSurface-CLEARANCE;
    }
    public static BlockState materialAt(int x,int y,int z,BlockState terrain) {
        return materialAt(y,terrain,SeaSurface.cellMinimum(x,z));
    }
    private static BlockState materialAt(int y,BlockState terrain,double upperSurface) {
        if(y==0 || y==ROOF) return Blocks.BEDROCK.defaultBlockState();
        if(y>0 && y<=LOWER_SEA_TOP) return Interstice.HEAVY_BLOCK.get().defaultBlockState();
        if(y<ROOF && y>=Math.floor(upperSurface))
            return Interstice.LIGHT_SEA.get().defaultBlockState().setValue(OceanLiquidBlock.CHAOTIC,true);
        return landAllowed(y,upperSurface) ? terrain : Blocks.AIR.defaultBlockState();
    }
    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender,RandomState random,StructureManager structures,ChunkAccess chunk) {
        return super.fillFromNoise(blender,random,structures,chunk).thenApply(result -> {
            var pos=new BlockPos.MutableBlockPos();
            int startX=result.getPos().getMinBlockX(),startZ=result.getPos().getMinBlockZ();
            for(int x=startX;x<startX+16;x++) for(int z=startZ;z<startZ+16;z++) {
                double upperSurface=SeaSurface.cellMinimum(x,z);
                for(int y=0;y<128;y++) if(!landAllowed(y,upperSurface)) {
                    pos.set(x,y,z);if(!result.getBlockState(pos).isAir()) result.setBlockState(pos,Blocks.AIR.defaultBlockState(),false);
                }
            }
            Heightmap.primeHeightmaps(result,EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG,Heightmap.Types.OCEAN_FLOOR_WG));
            return result;
        });
    }
    @Override public void buildSurface(WorldGenRegion region,StructureManager structures,RandomState random,ChunkAccess chunk) {
        // Surface rules run while heightmaps still describe land, before the upper sea hides it.
        super.buildSurface(region,structures,random,chunk);
        fillSeas(chunk);
    }
    public static void fillSeas(ChunkAccess chunk) {
        var pos=new BlockPos.MutableBlockPos();
        int startX=chunk.getPos().getMinBlockX(),startZ=chunk.getPos().getMinBlockZ();
        for(int x=startX;x<startX+16;x++) for(int z=startZ;z<startZ+16;z++) {
            double upperSurface=SeaSurface.cellMinimum(x,z);
            for(int y=0;y<128;y++) {
                pos.set(x,y,z);
                BlockState before=chunk.getBlockState(pos),after=materialAt(y,before,upperSurface);
                if(!before.equals(after)) chunk.setBlockState(pos,after,false);
            }
        }
        Heightmap.primeHeightmaps(chunk,EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG,Heightmap.Types.OCEAN_FLOOR_WG));
    }
    @Override public NoiseColumn getBaseColumn(int x,int z,LevelHeightAccessor height,RandomState random) {
        NoiseColumn base=super.getBaseColumn(x,z,height,random);
        BlockState[] column=new BlockState[128];
        double upperSurface=SeaSurface.cellMinimum(x,z);
        for(int y=0;y<128;y++) column[y]=materialAt(y,base.getBlock(y),upperSurface);
        return new NoiseColumn(0,column);
    }
    @Override public int getBaseHeight(int x,int z,Heightmap.Types type,LevelHeightAccessor height,RandomState random) {
        NoiseColumn column=getBaseColumn(x,z,height,random);
        for(int y=127;y>=0;y--) if(type.isOpaque().test(column.getBlock(y))) return y+1;
        return 0;
    }
    // Uncontrolled carvers, lakes and structures could breach the sea-clearance contract.
    @Override public void applyCarvers(WorldGenRegion region,long seed,RandomState random,BiomeManager biomes,StructureManager structures,ChunkAccess chunk,GenerationStep.Carving step) {}
    @Override public void applyBiomeDecoration(WorldGenLevel region,ChunkAccess chunk,StructureManager structures) {}
    @Override public void spawnOriginalMobs(WorldGenRegion region) {}
    @Override public void createStructures(RegistryAccess registries,ChunkGeneratorStructureState state,StructureManager structures,ChunkAccess chunk,StructureTemplateManager templates) {}
    @Override public void addDebugScreenInfo(List<String> lines,RandomState random,BlockPos pos) {
        super.addDebugScreenInfo(lines,random,pos);
        lines.add("Interstice islands: native 3D blended noise; land 41..77; toxic-sea clearance >= 6");
    }
}
