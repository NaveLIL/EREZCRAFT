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
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.DensityFunction;
import pro.erez.interstice.worldgen.terrain.TerrainV2;
import pro.erez.interstice.worldgen.terrain.TerrainV3;
import pro.erez.interstice.worldgen.terrain.TerrainColumn;
import pro.erez.interstice.worldgen.cave.CaveDensity;
import pro.erez.interstice.worldgen.cave.CaveFeatures;
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
    private record Definition(BiomeSource biome,Holder<NoiseGeneratorSettings> settings,GeometryProfile geometry,int revision,HolderGetter<Biome> biomes) {
        DataResult<IslandChunkGenerator> decode() {
            try { return DataResult.success(new IslandChunkGenerator(RealmBiomes.upgradeLegacy(biome,biomes),settings,geometry,revision)); }
            catch(IllegalArgumentException error) { return DataResult.error(error::getMessage); }
        }
    }
    public static final MapCodec<IslandChunkGenerator> CODEC=RecordCodecBuilder.<Definition>mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(Definition::biome),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(Definition::settings),
            GeometryProfile.CODEC.optionalFieldOf("geometry")
                    .xmap(value -> value.orElse(GeometryProfile.LEGACY),java.util.Optional::of).forGetter(Definition::geometry),
            com.mojang.serialization.Codec.intRange(1,4).optionalFieldOf("terrain_revision",1).forGetter(Definition::revision),
            RegistryOps.retrieveGetter(Registries.BIOME)
    ).apply(instance,Definition::new)).flatXmap(Definition::decode,generator -> DataResult.success(
            new Definition(generator.getBiomeSource(),generator.generatorSettings(),generator.geometry,generator.terrainRevision,null)));
    private final GeometryProfile geometry;
    private final int terrainRevision;
    private volatile Long terrainSeed;
    private volatile pro.erez.interstice.worldgen.terrain.HydrologyV4.Context hydrology;

    /** Missing geometry in old world data means the original 128-block profile. */
    public IslandChunkGenerator(BiomeSource biome,Holder<NoiseGeneratorSettings> settings) {this(biome,settings,GeometryProfile.LEGACY);}
    public IslandChunkGenerator(BiomeSource biome,Holder<NoiseGeneratorSettings> settings,GeometryProfile geometry) {
        this(biome,settings,geometry,1);
    }
    public IslandChunkGenerator(BiomeSource biome,Holder<NoiseGeneratorSettings> settings,GeometryProfile geometry,int revision) {
        super(biome,settings);
        this.geometry=java.util.Objects.requireNonNull(geometry);
        if(revision<1||revision>4||revision>=2&&!geometry.equals(GeometryProfile.TALL))throw new IllegalArgumentException("Unsupported terrain revision/profile");
        this.terrainRevision=revision;
        var noise=settings.value().noiseSettings();
        if(noise.minY()!=geometry.minY() || noise.height()!=geometry.height())
            throw new IllegalArgumentException("Island noise settings and geometry bounds must match");
    }
    public GeometryProfile geometry() {return geometry;}
    public int terrainRevision(){return terrainRevision;}
    public boolean isLivingRealm(){return terrainRevision>=2;}
    public boolean isRevisedRealm(){return terrainRevision>=3;}
    public static int featureMinimum(net.minecraft.world.level.LevelReader level,GeometryProfile profile){
        if(level instanceof WorldGenRegion region)return featureMinimum(region.getLevel(),profile);
        if(level instanceof net.minecraft.server.level.ServerLevel server&&server.getChunkSource().getGenerator() instanceof IslandChunkGenerator g&&g.isRevisedRealm())return profile.lowerSeaTop();
        return profile.minLand();
    }
    public static boolean featureAllowed(net.minecraft.world.level.LevelReader level,GeometryProfile profile,int x,int y,int z){
        int floor=featureMinimum(level,profile);
        return featureAllowed(profile,x,y,z,floor==profile.minLand()?floor:floor+1);
    }
    public static boolean featureAllowed(GeometryProfile profile,int x,int y,int z,int minimum){
        return y>=minimum&&y<=profile.maxLand()&&y+1<=SeaSurface.cellMinimum(profile,x,z,true)-profile.clearance();
    }
    @Override public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures,RandomState random,long seed){
        synchronized(this){if(terrainSeed!=null&&terrainSeed!=seed)throw new IllegalStateException("Generator reused for different world seeds");terrainSeed=seed;}
        return super.createState(structures,random,seed);
    }
    private long terrainSeed(){if(terrainSeed==null)throw new IllegalStateException("Terrain world seed has not been bound");return terrainSeed;}
    @Override public CompletableFuture<ChunkAccess> createBiomes(RandomState random,Blender blender,StructureManager structures,ChunkAccess chunk){
        if(!isLivingRealm())return super.createBiomes(random,blender,structures,chunk);
        return CompletableFuture.supplyAsync(()->{chunk.fillBiomesFromNoise(getBiomeSource(),random.sampler());return chunk;},net.minecraft.Util.backgroundExecutor());
    }
    public TerrainColumn terrainColumn(RandomState random,int x,int z){
        if(terrainRevision>=4)return hydrology(random).column(x,z);
        var point=new DensityFunction.SinglePointContext(x,0,z);
        double c=random.router().continents().compute(point),h=random.router().vegetation().compute(point);
        return isRevisedRealm()?TerrainV3.column(terrainSeed(),geometry,x,z,c,h):TerrainV2.column(terrainSeed(),geometry,x,z,c,h);
    }
    private pro.erez.interstice.worldgen.terrain.TerrainV4.Column baseV4(RandomState random,int x,int z){
        var point=new DensityFunction.SinglePointContext(x,0,z);
        return pro.erez.interstice.worldgen.terrain.TerrainV4.column(terrainSeed(),geometry,x,z,
                random.router().continents().compute(point),random.router().vegetation().compute(point));
    }
    private pro.erez.interstice.worldgen.terrain.HydrologyV4.Context hydrology(RandomState random){
        var value=hydrology;if(value!=null)return value;
        synchronized(this){if(hydrology==null)hydrology=new pro.erez.interstice.worldgen.terrain.HydrologyV4.Context(terrainSeed(),geometry,(x,z)->baseV4(random,x,z));return hydrology;}
    }
    private BlockState[] rawLivingColumn(RandomState random,int x,int z){
        return rawLivingColumn(random,x,z,CaveDensity.context(terrainSeed(),geometry,(cx,cz)->terrainColumn(random,cx,cz),terrainRevision));
    }
    private BlockState[] rawLivingColumn(RandomState random,int x,int z,CaveDensity.Context context){
        var point=new DensityFunction.SinglePointContext(x,0,z);
        double c=random.router().continents().compute(point),h=random.router().vegetation().compute(point);
        var terrain=terrainColumn(random,x,z);
        var caves=context.column(x,z,c,h,terrain::density);
        var states=new BlockState[geometry.height()];double upper=SeaSurface.cellMinimum(geometry,x,z,true);
        for(int y=geometry.minY();y<geometry.maxYExclusive();y++){
            boolean permitted=y>geometry.minY()&&y<=geometry.maxLand()&&y+1<=upper-geometry.clearance();
            states[y-geometry.minY()]=permitted&&caves.carve(y,terrain.density(y))>0?Interstice.RIFTSTONE.get().defaultBlockState():Blocks.AIR.defaultBlockState();
        }
        return states;
    }
    public static BlockState livingMaterialAt(GeometryProfile profile,int x,int y,int z,BlockState terrain){
        return livingMaterialAt(profile,y,terrain,SeaSurface.cellMinimum(profile,x,z,true));
    }
    private static BlockState livingMaterialAt(GeometryProfile profile,int y,BlockState terrain,double upper){
        if(y<profile.minY()||y>=profile.maxYExclusive())return Blocks.AIR.defaultBlockState();
        if(y==profile.minY()||y==profile.roof())return Blocks.BEDROCK.defaultBlockState();
        if(y<profile.roof()&&y>=Math.floor(upper))return Interstice.LIGHT_SEA.get().defaultBlockState().setValue(OceanLiquidBlock.CHAOTIC,true);
        if(y>profile.maxLand()||y+1>upper-profile.clearance())return Blocks.AIR.defaultBlockState();
        if(y<=profile.lowerSeaTop()&&terrain.isAir())return Interstice.HEAVY_BLOCK.get().defaultBlockState();
        return terrain;
    }
    /** Sealed subtractive caves remain dry; exterior ocean voids retain the lower toxic sea. */
    public BlockState terrainMaterialAt(int x,int y,int z,BlockState raw,TerrainColumn uncarved){
        return terrainMaterialAt(y,raw,SeaSurface.cellMinimum(geometry,x,z,true),uncarved);
    }
    private BlockState terrainMaterialAt(int y,BlockState raw,double upper,TerrainColumn uncarved){
        if(terrainRevision>=4&&raw.isAir()&&uncarved instanceof pro.erez.interstice.worldgen.terrain.HydrologyV4.Column hydro&&hydro.water().contains(y)){
            if(hydro.water().falling()&&y<hydro.water().water())return Interstice.HEAVY_FLOW.get().defaultFluidState()
                    .setValue(net.minecraft.world.level.material.FlowingFluid.LEVEL,8).setValue(net.minecraft.world.level.material.FlowingFluid.FALLING,true).createLegacyBlock();
            return Interstice.HEAVY_BLOCK.get().defaultBlockState();
        }
        if(isRevisedRealm()&&y>geometry.minY()+5&&y<=geometry.lowerSeaTop()&&raw.isAir()&&uncarved.density(y)>0)return raw;
        return livingMaterialAt(geometry,y,raw,upper);
    }
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
        if(isLivingRealm())return CompletableFuture.supplyAsync(()->{
            var sections=new java.util.ArrayList<net.minecraft.world.level.chunk.LevelChunkSection>();
            for(var section:chunk.getSections()){section.acquire();sections.add(section);}
            try{
                var pos=new BlockPos.MutableBlockPos();
                var caves=CaveDensity.context(terrainSeed(),geometry,(cx,cz)->terrainColumn(random,cx,cz),terrainRevision);
                for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++){
                    var states=rawLivingColumn(random,x,z,caves);
                    // Sections are already exclusively held above; the unchecked setter
                    // matches vanilla doFill and avoids acquiring the palette lock twice.
                    for(int y=geometry.minY();y<geometry.maxYExclusive();y++)if(!states[y-geometry.minY()].isAir())
                        chunk.getSection(chunk.getSectionIndex(y)).setBlockState(x&15,y&15,z&15,states[y-geometry.minY()],false);
                }
            }finally{sections.forEach(net.minecraft.world.level.chunk.LevelChunkSection::release);}
            Heightmap.primeHeightmaps(chunk,EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG,Heightmap.Types.OCEAN_FLOOR_WG));return chunk;
        },net.minecraft.Util.backgroundExecutor());
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
        if(isLivingRealm()){
            livingSurface(chunk);fillLivingSeas(chunk,random);
        }else{super.buildSurface(region,structures,random,chunk);fillSeas(geometry,chunk);}
        int minimum=featureMinimum(region,geometry);
        StoneVaults.geologicalSurface(geometry,chunk,region.getSeed(),minimum);
        PaleGardens.geology(geometry,chunk,region.getSeed(),minimum);
        if(terrainRevision>=4)pro.erez.interstice.minerals.RealmSurfaces.decorate(geometry,chunk,region.getSeed(),(x,z)->terrainColumn(random,x,z));
        if(isRevisedRealm()){
            pro.erez.interstice.minerals.GroundStrata.generate(geometry,chunk,region.getSeed());
            pro.erez.interstice.minerals.MineralDeposits.generate(geometry,chunk,region.getSeed(),terrainRevision);
        }else generateOres(geometry,chunk,region.getSeed());
        boolean garden=RealmBiomes.isGarden(chunk,new BlockPos(chunk.getPos().getMinBlockX()+7,40,chunk.getPos().getMinBlockZ()+7));
        if(FeatureDistribution.watchpostAllowed(terrainRevision,region.getSeed(),chunk.getPos().x,chunk.getPos().z,garden))
            WatchpostRuins.generate(geometry, chunk, region.getSeed(), region.getLevel().getStructureManager(), region.registryAccess(),minimum);
        if(FeatureDistribution.stoneVaultAllowed(terrainRevision,region.getSeed(),chunk.getPos().x,chunk.getPos().z,garden))
            StoneVaults.generate(geometry,chunk,region.getSeed(),minimum);
        if(isLivingRealm())CaveFeatures.decorate(geometry,chunk,region.getSeed(),region,terrainRevision);
        GardenTrees.generate(geometry,chunk,region.getSeed(),region,GardenTreeDefinitions.CROWN);
        if(isRevisedRealm())GloomcrownTree.generate(geometry,chunk,region.getSeed(),region);else GloomcrownTree.generate(geometry,chunk,region.getSeed());
        GardenTrees.generate(geometry,chunk,region.getSeed(),region);
        PaleGardens.undergrowth(geometry,chunk,region.getSeed(),minimum);
        if(FeatureDistribution.tideSproutsAllowed(terrainRevision,region.getSeed(),chunk.getPos().x,chunk.getPos().z))
            generateTideSprouts(geometry,chunk,region.getSeed(),minimum);
        NativeCropPatches.generate(geometry,chunk,region.getSeed(),terrainRevision);
    }
    private void livingSurface(ChunkAccess chunk){
        for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++){
            for(int y=geometry.maxLand();y>=(isRevisedRealm()?geometry.lowerSeaTop():geometry.lowerSeaTop()+2);y--){
                var p=new BlockPos(x,y,z);var s=chunk.getBlockState(p);
                if(s.isAir())continue;
                if(s.is(Interstice.RIFTSTONE.get()))chunk.setBlockState(p,Interstice.ABYSSAL_TURF.get().defaultBlockState(),false);
                break;
            }
        }
    }
    private void fillLivingSeas(ChunkAccess chunk,RandomState random){
        var p=new BlockPos.MutableBlockPos();
        for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++){
            double upper=SeaSurface.cellMinimum(geometry,x,z,true);
            var uncarved=terrainColumn(random,x,z);
            for(int y=geometry.minY();y<geometry.maxYExclusive();y++){
            p.set(x,y,z);var before=chunk.getBlockState(p);var after=terrainMaterialAt(y,before,upper,uncarved);
            if(!before.equals(after))chunk.setBlockState(p,after,false);
            }
        }
        Heightmap.primeHeightmaps(chunk,EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG,Heightmap.Types.OCEAN_FLOOR_WG));
    }
    public static void generateTideSprouts(GeometryProfile profile, ChunkAccess chunk, long seed) {
        generateTideSprouts(profile,chunk,seed,profile.minLand());
    }
    public static void generateTideSprouts(GeometryProfile profile,ChunkAccess chunk,long seed,int minimum) {
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
            for (int y = profile.maxLand(); y >= minimum; y--) {
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
        if(isLivingRealm()){
            var column=rawLivingColumn(random,x,z);double upper=SeaSurface.cellMinimum(geometry,x,z,true);
            var uncarved=terrainColumn(random,x,z);
            for(int y=geometry.minY();y<geometry.maxYExclusive();y++)column[y-geometry.minY()]=terrainMaterialAt(y,column[y-geometry.minY()],upper,uncarved);
            return new NoiseColumn(geometry.minY(),column);
        }
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
        lines.add("Interstice terrain revision "+terrainRevision+": land "+(isLivingRealm()?geometry.minY()+1:geometry.minLand())+".."+geometry.maxLand()+"; upper-sea clearance >= "+geometry.clearance());
    }
}
