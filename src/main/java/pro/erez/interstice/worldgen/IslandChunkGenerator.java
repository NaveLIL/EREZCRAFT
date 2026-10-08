package pro.erez.interstice.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biome;
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
import pro.erez.interstice.geometry.GeometryProfile;

/** Vanilla blended-noise islands, followed by a deterministic material layer for the two toxic seas. */
public final class IslandChunkGenerator extends NoiseBasedChunkGenerator {
    public static final int LOWER_SEA_TOP=34;
    public static final int CLEARANCE=6;
    public static final int MIN_LAND=LOWER_SEA_TOP+1+CLEARANCE;
    public static final int MAX_LAND=SeaSurface.MINIMUM-CLEARANCE-1;
    public static final int ROOF=127;
    private record Definition(BiomeSource biome,Holder<NoiseGeneratorSettings> settings,GeometryProfile geometry,HolderGetter<Biome> biomes) {
        DataResult<IslandChunkGenerator> decode() {
            try { return DataResult.success(new IslandChunkGenerator(RealmBiomes.upgradeLegacy(biome,biomes),settings,geometry)); }
            catch(IllegalArgumentException error) { return DataResult.error(error::getMessage); }
        }
    }
    public static final MapCodec<IslandChunkGenerator> CODEC=RecordCodecBuilder.<Definition>mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(Definition::biome),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(Definition::settings),
            GeometryProfile.CODEC.optionalFieldOf("geometry")
                    .xmap(value -> value.orElse(GeometryProfile.LEGACY),java.util.Optional::of).forGetter(Definition::geometry),
            RegistryOps.retrieveGetter(Registries.BIOME)
    ).apply(instance,Definition::new)).flatXmap(Definition::decode,generator -> DataResult.success(
            new Definition(generator.getBiomeSource(),generator.generatorSettings(),generator.geometry,null)));
    private final GeometryProfile geometry;

    /** Missing geometry in old world data means the original 128-block profile. */
    public IslandChunkGenerator(BiomeSource biome,Holder<NoiseGeneratorSettings> settings) {this(biome,settings,GeometryProfile.LEGACY);}
    public IslandChunkGenerator(BiomeSource biome,Holder<NoiseGeneratorSettings> settings,GeometryProfile geometry) {
        super(biome,settings);
        this.geometry=java.util.Objects.requireNonNull(geometry);
        var noise=settings.value().noiseSettings();
        if(noise.minY()!=geometry.minY() || noise.height()!=geometry.height())
            throw new IllegalArgumentException("Island noise settings and geometry bounds must match");
    }
    public GeometryProfile geometry() {return geometry;}
    @Override protected MapCodec<? extends ChunkGenerator> codec() {return CODEC;}

    public static boolean landAllowed(int x,int y,int z) {
        return landAllowed(GeometryProfile.LEGACY,x,y,z);
    }
    public static boolean landAllowed(GeometryProfile profile,int x,int y,int z) {
        return landAllowed(profile,y,SeaSurface.cellMinimum(profile,x,z,true));
    }
    private static boolean landAllowed(GeometryProfile profile,int y,double upperSurface) {
        return y>=profile.minLand() && y<=profile.maxLand() && y+1<=upperSurface-profile.clearance();
    }
    public static BlockState materialAt(int x,int y,int z,BlockState terrain) {
        return materialAt(GeometryProfile.LEGACY,x,y,z,terrain);
    }
    public static BlockState materialAt(GeometryProfile profile,int x,int y,int z,BlockState terrain) {
        return materialAt(profile,y,terrain,SeaSurface.cellMinimum(profile,x,z,true));
    }
    private static BlockState materialAt(GeometryProfile profile,int y,BlockState terrain,double upperSurface) {
        if(y<profile.minY() || y>=profile.maxYExclusive()) return Blocks.AIR.defaultBlockState();
        if(y==profile.minY() || y==profile.roof()) return Blocks.BEDROCK.defaultBlockState();
        if(y>profile.minY() && y<=profile.lowerSeaTop()) return Interstice.HEAVY_BLOCK.get().defaultBlockState();
        if(y<profile.roof() && y>=Math.floor(upperSurface))
            return Interstice.LIGHT_SEA.get().defaultBlockState().setValue(OceanLiquidBlock.CHAOTIC,true);
        if(!landAllowed(profile,y,upperSurface)) return Blocks.AIR.defaultBlockState();
        // Land is allowed at this position — replace vanilla stone/dirt with riftstone, grass/moss with abyssal turf
        if(terrain.is(Blocks.GRASS_BLOCK) || terrain.is(Blocks.MOSS_BLOCK) || terrain.is(Interstice.ABYSSAL_TURF.get())) return Interstice.ABYSSAL_TURF.get().defaultBlockState();
        if(terrain.is(Blocks.STONE) || terrain.is(Blocks.DIRT) || terrain.is(Interstice.RIFTSTONE.get())) return Interstice.RIFTSTONE.get().defaultBlockState();
        return terrain;
    }
    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender,RandomState random,StructureManager structures,ChunkAccess chunk) {
        geometry.checkHeight(chunk);
        return super.fillFromNoise(blender,random,structures,chunk).thenApply(result -> {
            var pos=new BlockPos.MutableBlockPos();
            int startX=result.getPos().getMinBlockX(),startZ=result.getPos().getMinBlockZ();
            for(int x=startX;x<startX+16;x++) for(int z=startZ;z<startZ+16;z++) {
                double upperSurface=SeaSurface.cellMinimum(geometry,x,z,true);
                for(int y=geometry.minY();y<geometry.maxYExclusive();y++) if(!landAllowed(geometry,y,upperSurface)) {
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
        fillSeas(geometry,chunk);
        StoneVaults.geologicalSurface(geometry,chunk,region.getSeed());
        PaleGardens.geology(geometry,chunk,region.getSeed());
        generateOres(geometry,chunk,region.getSeed());
        WatchpostRuins.generate(geometry, chunk, region.getSeed(), region.getLevel().getStructureManager(), region.registryAccess());
        StoneVaults.generate(geometry,chunk,region.getSeed());
        GloomcrownTree.generate(geometry,chunk,region.getSeed());
        GardenTrees.generate(geometry,chunk,region.getSeed(),region);
        PaleGardens.undergrowth(geometry,chunk,region.getSeed());
        generateTideSprouts(geometry,chunk,region.getSeed());
    }
    public static void generateTideSprouts(GeometryProfile profile, ChunkAccess chunk, long seed) {
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;
        long chunkSeed = (seed ^ (cx * 987654321L + cz * 123456789L)) + 777L;
        java.util.Random rnd = new java.util.Random(chunkSeed);

        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState sproutState = Interstice.TIDE_SPROUT.get().defaultBlockState();

        // 4..7 attempts per chunk
        int count = 4 + rnd.nextInt(4);
        for (int i = 0; i < count; i++) {
            int x = startX + rnd.nextInt(16);
            int z = startZ + rnd.nextInt(16);

            // Scan downward from top of land zone to find abyssal turf surface
            for (int y = profile.maxLand(); y >= profile.minLand(); y--) {
                pos.set(x, y, z);
                BlockState at = chunk.getBlockState(pos);
                if (at.is(Interstice.ABYSSAL_TURF.get())) {
                    pos.set(x, y + 1, z);
                    if (chunk.getBlockState(pos).isAir()) {
                        chunk.setBlockState(pos, sproutState, false);
                    }
                    break;
                }
                if (!at.isAir()) break; // hit solid non-turf block — stop
            }
        }
    }
    public static void generateOres(GeometryProfile profile, ChunkAccess chunk, long seed) {
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;
        long chunkSeed = seed ^ (cx * 341873128712L + cz * 132897987541L);
        java.util.Random rnd = new java.util.Random(chunkSeed);

        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState oreState = Interstice.RIFTSILVER_ORE.get().defaultBlockState();

        int minLand = profile.minLand();
        int maxLand = profile.maxLand();
        if (maxLand - minLand <= 6) return;

        for (int v = 0; v < 4; v++) {
            int vx = startX + rnd.nextInt(16);
            int vz = startZ + rnd.nextInt(16);
            int vy = minLand + 2 + rnd.nextInt(maxLand - minLand - 4);
            int veinSize = 4 + rnd.nextInt(5);

            for (int i = 0; i < veinSize; i++) {
                int ox = vx + rnd.nextInt(3) - 1;
                int oy = vy + rnd.nextInt(3) - 1;
                int oz = vz + rnd.nextInt(3) - 1;
                if (ox < startX || ox >= startX + 16 || oz < startZ || oz >= startZ + 16) continue;
                pos.set(ox, oy, oz);

                if (chunk.getBlockState(pos).is(Blocks.STONE) || chunk.getBlockState(pos).is(Interstice.RIFTSTONE.get())
                        || chunk.getBlockState(pos).is(VaultMaterials.VAULTSTONE.get())
                        || chunk.getBlockState(pos).is(VaultMaterials.WEATHERED_VAULTSTONE.get())
                        || chunk.getBlockState(pos).is(GardenMaterials.PALESTONE.get())) {
                    chunk.setBlockState(pos, oreState, false);
                }
            }
        }
    }
    public static void fillSeas(ChunkAccess chunk) {
        fillSeas(GeometryProfile.LEGACY,chunk);
    }
    public static void fillSeas(GeometryProfile profile,ChunkAccess chunk) {
        profile.checkHeight(chunk);
        var pos=new BlockPos.MutableBlockPos();
        int startX=chunk.getPos().getMinBlockX(),startZ=chunk.getPos().getMinBlockZ();
        for(int x=startX;x<startX+16;x++) for(int z=startZ;z<startZ+16;z++) {
            double upperSurface=SeaSurface.cellMinimum(profile,x,z,true);
            for(int y=profile.minY();y<profile.maxYExclusive();y++) {
                pos.set(x,y,z);
                BlockState before=chunk.getBlockState(pos),after=materialAt(profile,y,before,upperSurface);
                if(!before.equals(after)) chunk.setBlockState(pos,after,false);
            }
        }
        Heightmap.primeHeightmaps(chunk,EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG,Heightmap.Types.OCEAN_FLOOR_WG));
    }
    @Override public NoiseColumn getBaseColumn(int x,int z,LevelHeightAccessor height,RandomState random) {
        geometry.checkHeight(height);
        NoiseColumn base=super.getBaseColumn(x,z,height,random);
        BlockState[] column=new BlockState[geometry.height()];
        double upperSurface=SeaSurface.cellMinimum(geometry,x,z,true);
        for(int y=geometry.minY();y<geometry.maxYExclusive();y++) column[y-geometry.minY()]=materialAt(geometry,y,base.getBlock(y),upperSurface);
        return new NoiseColumn(geometry.minY(),column);
    }
    @Override public int getBaseHeight(int x,int z,Heightmap.Types type,LevelHeightAccessor height,RandomState random) {
        NoiseColumn column=getBaseColumn(x,z,height,random);
        for(int y=geometry.roof();y>=geometry.minY();y--) if(type.isOpaque().test(column.getBlock(y))) return y+1;
        return geometry.minY();
    }
    // Uncontrolled carvers, lakes and structures could breach the sea-clearance contract.
    @Override public void applyCarvers(WorldGenRegion region,long seed,RandomState random,BiomeManager biomes,StructureManager structures,ChunkAccess chunk,GenerationStep.Carving step) {}
    @Override public void applyBiomeDecoration(WorldGenLevel region,ChunkAccess chunk,StructureManager structures) {}
    @Override public void spawnOriginalMobs(WorldGenRegion region) {}
    @Override public void createStructures(RegistryAccess registries,ChunkGeneratorStructureState state,StructureManager structures,ChunkAccess chunk,StructureTemplateManager templates) {}
    @Override public void addDebugScreenInfo(List<String> lines,RandomState random,BlockPos pos) {
        super.addDebugScreenInfo(lines,random,pos);
        lines.add("Interstice islands: native 3D blended noise; land "+geometry.minLand()+".."+geometry.maxLand()+"; toxic-sea clearance >= "+geometry.clearance());
    }
}
