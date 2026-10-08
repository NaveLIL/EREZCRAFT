package pro.erez.interstice.worldgen;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;

/** Uses Minecraft's configurable trunk/foliage placers in an in-memory view before any world writes. */
public final class GardenTrees {
    private GardenTrees() {}
    private static final int MAX_RADIUS = 7, MAX_HEIGHT = 32, MAX_CELLS = 4096;
    public static Map<BlockPos, BlockState> plan(LevelReader level, Function<BlockPos, BlockState> blocks,
                                               BlockPos root, GardenTreeDefinitions.Variant variant, RandomSource random) {
        TreeConfiguration config=variant.tree();
        if (config.rootPlacer.isPresent() || !config.decorators.isEmpty()) return Map.of();
        int height = config.trunkPlacer.getTreeHeight(random);
        if (height < 3 || height > MAX_HEIGHT) return Map.of();
        Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
        boolean[] planning={false,false}; // overflow and foliage phase; private to this plan
        var view = new PlanReader(level, blocks, cells, root,planning);
        List<FoliagePlacer.FoliageAttachment> attachments;
        if(!variant.branchPath().isEmpty()||!variant.joCode().isEmpty()) {
            var skeleton=JoShape.draw(variant.code(),root,random.nextInt(4),random.nextInt(variant.extraHeight()+1));
            skeleton.logs().forEach((p,axis)->cells.put(p,GardenMaterials.PALEHEART_LOG.get().defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,axis)));
            if(variant.stemWidth()==2) {
                var core=new java.util.ArrayList<>(cells.keySet());
                for(var p:core)if(p.getX()==root.getX() && p.getZ()==root.getZ())for(int dx=0;dx<2;dx++)for(int dz=0;dz<2;dz++)
                    cells.putIfAbsent(p.offset(dx,0,dz),GardenMaterials.PALEHEART_LOG.get().defaultBlockState());
            }
            height=cells.keySet().stream().mapToInt(p->p.getY()-root.getY()+1).max().orElse(0);
            attachments=skeleton.ends().stream().map(p->new FoliagePlacer.FoliageAttachment(p.above(),0,false)).toList();
        } else attachments = config.trunkPlacer.placeTrunk(view, (pos, state) -> {
            // Vanilla's soil callback cannot replace our existing living ground.
            if (pos.getY() == root.getY() - 1) return;
            cells.put(pos.immutable(), state);
        }, random, height, root, config);
        int foliageHeight = config.foliagePlacer.foliageHeight(random, height, config);
        int foliageRadius = config.foliagePlacer.foliageRadius(random, height - foliageHeight);
        if(foliageHeight>6 || foliageRadius>4)return Map.of();
        FoliagePlacer.FoliageSetter setter = new FoliagePlacer.FoliageSetter() {
            public void set(BlockPos p, BlockState state) { cells.putIfAbsent(p.immutable(), state); }
            public boolean isSet(BlockPos p) { return cells.containsKey(p) && cells.get(p).is(GardenMaterials.PALEHEART_LEAVES.get()); }
        };
        planning[1]=true;
        for (var attachment : attachments) config.foliagePlacer.createFoliage(view, setter, random, config, height, attachment, foliageHeight, foliageRadius);
        if (planning[0] || cells.isEmpty() || cells.size() > MAX_CELLS || !cells.getOrDefault(root, Blocks.AIR.defaultBlockState()).is(GardenMaterials.PALEHEART_LOG.get())) return Map.of();
        for (var entry : cells.entrySet()) {
            var p = entry.getKey(); var state = entry.getValue();
            if (Math.abs(p.getX() - root.getX()) > MAX_RADIUS || Math.abs(p.getZ() - root.getZ()) > MAX_RADIUS
                    || p.getY() < root.getY() || p.getY() > root.getY() + MAX_HEIGHT + 4
                    || !(state.is(GardenMaterials.PALEHEART_LOG.get()) || state.is(GardenMaterials.PALEHEART_LEAVES.get()))) return Map.of();
        }
        // Native foliage recipes may contain leaves beyond distance 6. Keep only a living connected crown.
        Map<BlockPos, Integer> distances = new java.util.HashMap<>(); var queue = new ArrayDeque<BlockPos>();
        cells.forEach((p, state) -> { if (state.is(GardenMaterials.PALEHEART_LOG.get())) { distances.put(p, 0); queue.add(p); } });
        while (!queue.isEmpty()) {
            var p = queue.remove(); int distance = distances.get(p) + 1;
            if (distance > 6) continue;
            for (var direction : Direction.values()) {
                var next = p.relative(direction);
                if (cells.containsKey(next) && !distances.containsKey(next)) { distances.put(next, distance); queue.add(next); }
            }
        }
        cells.entrySet().removeIf(e -> e.getValue().is(GardenMaterials.PALEHEART_LEAVES.get()) && !distances.containsKey(e.getKey()));
        cells.replaceAll((p, state) -> state.is(GardenMaterials.PALEHEART_LEAVES.get())
                ? state.setValue(LeavesBlock.DISTANCE, distances.get(p)).setValue(LeavesBlock.PERSISTENT, false) : state);
        if(variant.fruitCount()>0){
            int top=cells.entrySet().stream().filter(e->e.getValue().is(GardenMaterials.PALEHEART_LEAVES.get())).mapToInt(e->e.getKey().getY()).max().orElse(root.getY());
            // Food-bearing crowns must remain genuinely high; no easy fruit from short garden shrubs.
            if(top-root.getY()<18)return Map.of();
            var sites=new java.util.ArrayList<>(cells.entrySet().stream().filter(e->e.getValue().is(GardenMaterials.PALEHEART_LEAVES.get())
                    &&e.getKey().getY()==top&&!cells.containsKey(e.getKey().above())).map(e->e.getKey().above()).toList());
            var chosen=new java.util.ArrayList<BlockPos>();
            while(!sites.isEmpty()&&chosen.size()<variant.fruitCount()){
                var site=sites.remove(random.nextInt(sites.size()));
                if(chosen.stream().anyMatch(p->p.distManhattan(site)<3))continue;
                cells.put(site,GardenMaterials.CROWN_FRUIT.get().defaultBlockState());chosen.add(site);
            }
            if(chosen.isEmpty())return Map.of();
        }
        // Optional chains stop at real ground/obstacles and never overwrite part of the tree.
        var anchors=cells.entrySet().stream().filter(e->e.getValue().is(GardenMaterials.PALEHEART_LEAVES.get())&&!cells.containsKey(e.getKey().below()))
                .map(Map.Entry::getKey).toList();
        for(int attempt=0;attempt<variant.vineAttempts()&&!anchors.isEmpty();attempt++){
            var anchor=anchors.get(random.nextInt(anchors.size()));var chain=new java.util.ArrayList<BlockPos>();
            int length=1+random.nextInt(variant.vineLength());
            for(int step=1;step<=length;step++){
                var p=anchor.below(step);
                if(p.getY()<root.getY()||cells.containsKey(p)||!blocks.apply(p).isAir())break;
                chain.add(p);
            }
            for(int i=0;i<chain.size();i++){
                boolean cap=i==chain.size()-1 || (i==chain.size()-2 && (i&1)==0);
                cells.put(chain.get(i),GardenMaterials.PALE_VINE.get().defaultBlockState().setValue(GardenVineBlock.SECTION,(i&1)+(cap?2:0)));
            }
        }
        return cells;
    }
    public static boolean fits(Map<BlockPos, BlockState> cells, Function<BlockPos, BlockState> blocks,
                               Predicate<BlockPos> allowed, BlockPos root) {
        if (cells.isEmpty() || !blocks.apply(root.below()).is(Interstice.ABYSSAL_TURF.get())) return false;
        // Every foot of a thick native trunk has real ground, not only the first sapling.
        for (var entry : cells.entrySet()) {
            var pos = entry.getKey(); var existing = blocks.apply(pos);
            if (!allowed.test(pos) || (!existing.isAir() && !(pos.equals(root) && (existing.is(GardenMaterials.PALEHEART_SAPLING.get())||existing.is(GardenMaterials.CROWN_SAPLING.get()))))) return false;
            if (entry.getValue().is(GardenMaterials.PALEHEART_LOG.get()) && pos.getY() == root.getY()
                    && !blocks.apply(pos.below()).is(Interstice.ABYSSAL_TURF.get())) return false;
        }
        return true;
    }
    public static boolean grow(ServerLevel level, BlockPos root, RandomSource random) {
        return grow(level,root,random,GardenTreeDefinitions.PALEHEART);
    }
    public static boolean grow(ServerLevel level,BlockPos root,RandomSource random,net.minecraft.resources.ResourceLocation definition){
        var config = GardenTreeDefinitions.get(definition).select(random);
        var cells = plan(level, level::getBlockState, root, config, random);
        var generator = level.getChunkSource().getGenerator();
        if (!fits(cells, level::getBlockState, p -> level.hasChunkAt(p) && !level.isOutsideBuildHeight(p)
                && (!(generator instanceof IslandChunkGenerator islands) || IslandChunkGenerator.landAllowed(islands.geometry(), p.getX(), p.getY(), p.getZ())), root)) return false;
        cells.forEach((p, state) -> level.setBlock(p, state, 3)); return true;
    }
    public static void generate(GeometryProfile profile, ChunkAccess chunk, long seed, LevelReader level) {
        generate(profile,chunk,seed,level,GardenTreeDefinitions.PALEHEART);
    }
    public static void generate(GeometryProfile profile,ChunkAccess chunk,long seed,LevelReader level,net.minecraft.resources.ResourceLocation id){
        var definition = GardenTreeDefinitions.get(id);
        boolean crown=id.equals(GardenTreeDefinitions.CROWN);
        var random = RandomSource.create(seed ^ chunk.getPos().toLong() ^ (crown?0xCA015L:0x9A1L));
        if (random.nextInt(definition.chance()) != 0) return;
        for (int attempt = 0; attempt < definition.attempts(); attempt++) {
            int x = chunk.getPos().getMinBlockX() + (crown?7:5+random.nextInt(6)), z = chunk.getPos().getMinBlockZ() + (crown?7:5+random.nextInt(6));
            for (int y = profile.maxLand(); y >= profile.minLand(); y--) {
                var ground = new BlockPos(x, y, z); var state = chunk.getBlockState(ground);
                if (state.isAir()) continue;
                if (state.is(Interstice.ABYSSAL_TURF.get()) && RealmBiomes.isGarden(chunk, ground)) {
                    var root = ground.above();
                    var cells = plan(level, p -> inside(chunk, p) ? chunk.getBlockState(p) : Blocks.BEDROCK.defaultBlockState(), root, definition.select(random), random);
                    if (fits(cells, chunk::getBlockState, p -> inside(chunk, p)
                            && IslandChunkGenerator.landAllowed(profile, p.getX(), p.getY(), p.getZ()), root)) cells.forEach((p, s) -> chunk.setBlockState(p, s, false));
                }
                break;
            }
        }
    }
    private static boolean inside(ChunkAccess chunk, BlockPos p) { return (p.getX() >> 4) == chunk.getPos().x && (p.getZ() >> 4) == chunk.getPos().z; }

    private record PlanReader(LevelReader delegate, Function<BlockPos, BlockState> blocks,
                              Map<BlockPos, BlockState> cells, BlockPos root,boolean[] planning) implements LevelReader, LevelSimulatedReader {
        public BlockState getBlockState(BlockPos p) {
            if (Math.abs(p.getX()-root.getX())>MAX_RADIUS || Math.abs(p.getZ()-root.getZ())>MAX_RADIUS || p.getY()>root.getY()+MAX_HEIGHT+4
                    || (planning[1] && p.getY()<root.getY())) {planning[0]=true;return Blocks.BEDROCK.defaultBlockState();}
            if (cells.containsKey(p)) return cells.get(p);
            // Plan the complete intended shape optimistically; final fits rejects any obstacle atomically.
            if(p.getY()>=root.getY())return Blocks.AIR.defaultBlockState();
            return blocks.apply(p);
        }
        public FluidState getFluidState(BlockPos p) { return getBlockState(p).getFluidState(); }
        public boolean isStateAtPosition(BlockPos p, Predicate<BlockState> predicate) { return predicate.test(getBlockState(p)); }
        public boolean isFluidAtPosition(BlockPos p, Predicate<FluidState> predicate) { return predicate.test(getFluidState(p)); }
        public BlockEntity getBlockEntity(BlockPos p) { return null; }
        public <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos p, BlockEntityType<T> type) { return Optional.empty(); }
        public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean create) { return delegate.getChunk(x,z,status,false); }
        public boolean hasChunk(int x, int z) { return delegate.hasChunk(x,z); }
        public int getHeight(Heightmap.Types type, int x, int z) { return delegate.getHeight(type,x,z); }
        public BlockPos getHeightmapPos(Heightmap.Types type, BlockPos p) { return LevelReader.super.getHeightmapPos(type,p); }
        public int getSkyDarken() { return delegate.getSkyDarken(); }
        public BiomeManager getBiomeManager() { return delegate.getBiomeManager(); }
        public Holder<Biome> getUncachedNoiseBiome(int x,int y,int z) { return delegate.getUncachedNoiseBiome(x,y,z); }
        public boolean isClientSide() { return false; }
        public int getSeaLevel() { return delegate.getSeaLevel(); }
        public DimensionType dimensionType() { return delegate.dimensionType(); }
        public RegistryAccess registryAccess() { return delegate.registryAccess(); }
        public FeatureFlagSet enabledFeatures() { return delegate.enabledFeatures(); }
        public float getShade(Direction d, boolean shade) { return delegate.getShade(d,shade); }
        public LevelLightEngine getLightEngine() { return delegate.getLightEngine(); }
        public WorldBorder getWorldBorder() { return delegate.getWorldBorder(); }
        public List<VoxelShape> getEntityCollisions(Entity entity,AABB box) { return List.of(); }
    }
}
