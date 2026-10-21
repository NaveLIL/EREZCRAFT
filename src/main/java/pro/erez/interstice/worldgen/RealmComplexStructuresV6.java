package pro.erez.interstice.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import java.util.function.BiPredicate;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.*;
import net.minecraft.world.level.levelgen.structure.placement.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.terrain.NativeColumnSamplerV6;

/** Native structure starts/references/pieces, with immutable whole-site terrain validation.
 * Placement uses the SAME sparse V6 grid as small ruins; each piece writes only its feature chunk.
 */
public final class RealmComplexStructuresV6 {
    public static final int MAX_SUPPORT = 6;
    private static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Interstice.ID);
    private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Interstice.ID);
    private static final DeferredRegister<StructurePlacementType<?>> PLACEMENTS = DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, Interstice.ID);
    public static final DeferredHolder<StructureType<?>, StructureType<ComplexStructure>> TYPE = TYPES.register("ethereal_complex", () -> () -> ComplexStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> PIECE = PIECES.register("ethereal_complex", () -> (StructurePieceType.ContextlessType)ComplexPiece::new);
    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<Placement>> PLACEMENT = PLACEMENTS.register("ethereal_complex", () -> () -> Placement.CODEC);
    private static final List<ResourceLocation> TEMPLATES = List.of(id("temple"), id("camp_small_0"), id("camp_small_1"), id("camp_medium_0"), id("camp_medium_1"), id("camp_large_0"), id("camp_large_1"));
    private static final Map<StructureTemplate, TemplatePlan> PLANS = new WeakHashMap<>();
    private static final Map<RandomState, LinkedHashMap<Long, Optional<Site>>> SITES = new WeakHashMap<>();
    public record Cell(BlockPos local, BlockState state, CompoundTag data) {}
    public record TemplatePlan(int width, int height, int depth, List<Cell> cells) {}
    public record Site(ChunkPos anchor, ResourceLocation template, BlockPos origin, int rotation, int width, int height, int depth) {
        public BoundingBox bounds() { return new BoundingBox(origin.getX(), origin.getY() - MAX_SUPPORT, origin.getZ(), origin.getX()+width-1, origin.getY()+height-1, origin.getZ()+depth-1); }
        public boolean containsColumn(int x,int z,int margin) {return x>=origin.getX()-margin&&x<origin.getX()+width+margin&&z>=origin.getZ()-margin&&z<origin.getZ()+depth+margin;}
        public List<ChunkPos> chunks() {var result=new ArrayList<ChunkPos>();for(int x=Math.floorDiv(origin.getX(),16);x<=Math.floorDiv(origin.getX()+width-1,16);x++)for(int z=Math.floorDiv(origin.getZ(),16);z<=Math.floorDiv(origin.getZ()+depth-1,16);z++)result.add(new ChunkPos(x,z));return List.copyOf(result);}
    }
    private RealmComplexStructuresV6() {}
    public static void register(IEventBus bus) { TYPES.register(bus); PIECES.register(bus); PLACEMENTS.register(bus); }
    public static ResourceLocation id(String path) {return ResourceLocation.fromNamespaceAndPath(Interstice.ID,"ethereal/complex/"+path);}
    public static List<ResourceLocation> templates() {return TEMPLATES;}
    private static long mix(long n) {n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;n=(n^(n>>>27))*0x94d049bb133111ebL;return n^(n>>>31);}
    public static boolean scheduled(long seed, ChunkPos anchor) {
        var slot=RealmStructuresV5.candidate(seed,anchor.x,anchor.z);
        return slot!=null&&(mix(seed^anchor.toLong()^0x434F4D504C455836L)&3)==0;
    }
    public static ResourceLocation selected(long seed, ChunkPos anchor) {
        if(!scheduled(seed,anchor))return null;
        long key=mix(seed^anchor.toLong()^0x434F4D5054454D50L);
        return (key&1)==0?TEMPLATES.getFirst():TEMPLATES.get(1+Math.floorMod(key>>>1,6));
    }
    /** Loaded immutable NBT geometry; all marker/air cells have already been removed offline. */
    public static synchronized TemplatePlan plan(StructureTemplateManager manager, HolderLookup.Provider registries, ResourceLocation id) {
        if(!TEMPLATES.contains(id))throw new IllegalArgumentException("Unknown complex template "+id);
        var template=manager.get(id).orElseThrow(()->new IllegalStateException("Missing complex "+id));var old=PLANS.get(template);if(old!=null)return old;
        var tag=template.save(new CompoundTag());var size=tag.getList("size",Tag.TAG_INT);int w=size.getInt(0),h=size.getInt(1),d=size.getInt(2);
        if(w<15||w>48||d<15||d>48||h<1||h>24)throw new IllegalStateException("Complex exceeds approved source bounds");
        var palette=new ArrayList<BlockState>();
        for(var entry:tag.getList("palette",Tag.TAG_COMPOUND)){
            var state=NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK),(CompoundTag)entry);
            if(state.isAir()||state.is(Blocks.JIGSAW)||state.is(Blocks.STRUCTURE_BLOCK)||!state.getFluidState().isEmpty())throw new IllegalStateException("Unresolved/unsafe complex block "+id);
            palette.add(state);
        }
        var cells=new ArrayList<Cell>();var positions=new HashSet<BlockPos>();
        for(var entry:tag.getList("blocks",Tag.TAG_COMPOUND)){
            var b=(CompoundTag)entry;var p=b.getList("pos",Tag.TAG_INT);var pos=new BlockPos(p.getInt(0),p.getInt(1),p.getInt(2));int index=b.getInt("state");
            if(pos.getX()<0||pos.getX()>=w||pos.getY()<0||pos.getY()>=h||pos.getZ()<0||pos.getZ()>=d||!positions.add(pos)||index<0||index>=palette.size())throw new IllegalStateException("Invalid complex cell");
            cells.add(new Cell(pos,palette.get(index),b.contains("nbt")?b.getCompound("nbt").copy():null));
        }
        if(cells.stream().noneMatch(c->c.data!=null&&c.data.contains("LootTable")))throw new IllegalStateException("Complex has no real loot");
        var result=new TemplatePlan(w,h,d,List.copyOf(cells));PLANS.put(template,result);return result;
    }
    public static BlockPos rotated(BlockPos p,int rotation,TemplatePlan plan) {return switch(rotation){case 1->new BlockPos(plan.depth-1-p.getZ(),p.getY(),p.getX());case 2->new BlockPos(plan.width-1-p.getX(),p.getY(),plan.depth-1-p.getZ());case 3->new BlockPos(p.getZ(),p.getY(),plan.width-1-p.getX());default->p;};}
    /** Four bounded terrain attempts; only numeric columns, never loading/reading neighbour chunks. */
    public static Optional<Site> site(long seed, ChunkPos anchor, IslandChunkGenerator generator, RandomState random,
                                     StructureTemplateManager manager, HolderLookup.Provider registries, LevelHeightAccessor height) {
        if(generator.terrainRevision()!=6||!generator.geometry().equals(GeometryProfile.TALL)||!scheduled(seed,anchor))return Optional.empty();
        LinkedHashMap<Long,Optional<Site>> cache;
        synchronized(SITES){cache=SITES.computeIfAbsent(random,r->new LinkedHashMap<>(32,.75F,true));}
        synchronized(cache){
            var old=cache.get(anchor.toLong());if(old!=null)return old;
            var template=selected(seed,anchor);var plan=plan(manager,registries,template);int rotation=RealmStructuresV5.candidate(seed,anchor.x,anchor.z).rotation();
            int width=rotation%2==0?plan.width:plan.depth,depth=rotation%2==0?plan.depth:plan.width;
            int x0=anchor.getMinBlockX()+8-width/2,z0=anchor.getMinBlockZ()+8-depth/2;
            int[][] offsets={{0,0},{32,0},{0,32},{-32,-32}};Optional<Site> result=Optional.empty();
            for(var offset:offsets){int x=x0+offset[0],z=z0+offset[1];int base=host(generator,random,height,x,z,width,depth,plan.height);
                if(base>=0){result=Optional.of(new Site(anchor,template,new BlockPos(x,base,z),rotation,width,plan.height,depth));break;}}
            cache.put(anchor.toLong(),result);if(cache.size()>256)cache.remove(cache.keySet().iterator().next());return result;
        }
    }
    private static int top(IslandChunkGenerator g,RandomState random,LevelHeightAccessor height,int x,int z) {
        var c=g.getBaseColumn(x,z,height,random);
        for(int y=g.geometry().maxLand();y>=g.geometry().minLand();y--)if(!c.getBlock(y).isAir())return StoneVaults.isGround(c.getBlock(y))&&c.getBlock(y).getFluidState().isEmpty()?y:-1;
        return -1;
    }
    private static int host(IslandChunkGenerator g,RandomState random,LevelHeightAccessor height,int x,int z,int width,int depth,int tall) {
        int low=999,high=-1;
        for(int[] p:new int[][]{{0,0},{width-1,0},{0,depth-1},{width-1,depth-1},{width/2,depth/2}}){int y=top(g,random,height,x+p[0],z+p[1]);if(y<0||y>110)return -1;low=Math.min(low,y);high=Math.max(high,y);}
        if(high-low>MAX_SUPPORT)return -1;
        var caves=NativeColumnSamplerV6.of(random).forestCaves();
        for(int dx=0;dx<width;dx++)for(int dz=0;dz<depth;dz++){
            int xx=x+dx,zz=z+dz,y=top(g,random,height,xx,zz);if(y<0||y>110||caves.entranceColumn(xx,zz))return -1;
            var c=g.getBaseColumn(xx,zz,height,random);if(!StoneVaults.isGround(c.getBlock(y-1))||!StoneVaults.isGround(c.getBlock(y-2)))return -1;
            low=Math.min(low,y);high=Math.max(high,y);if(high-low>MAX_SUPPORT)return -1;
        }
        int base=high+1;
        for(int dx=0;dx<width;dx++)for(int dz=0;dz<depth;dz++){
            int xx=x+dx,zz=z+dz;if(base+tall>g.geometry().maxLand()||base+tall>SeaSurface.cellMinimum(g.geometry(),xx,zz,true)-g.geometry().clearance())return -1;
            var c=g.getBaseColumn(xx,zz,height,random);for(int y=base;y<base+tall;y++)if(!c.getBlock(y).isAir())return -1;
        }
        return base;
    }
    public static BiPredicate<Integer,Integer> reservation(long seed,IslandChunkGenerator g,RandomState random,StructureTemplateManager manager,HolderLookup.Provider registries,LevelHeightAccessor height) {
        var cache=new HashMap<Long,List<Site>>();int cell=RealmStructuresV5.CELL_CHUNKS*16;
        return (x,z)->{
            int cx=Math.floorDiv(x,cell),cz=Math.floorDiv(z,cell);long key=((long)cx<<32)^(cz&0xffffffffL);
            var list=cache.computeIfAbsent(key,k->{var sites=new ArrayList<Site>();for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                var slot=RealmStructuresV5.slot(seed,cx+dx,cz+dz);if(slot!=null)site(seed,slot.chunk(),g,random,manager,registries,height).ifPresent(sites::add);
            }return List.copyOf(sites);});
            return list.stream().anyMatch(s->s.containsColumn(x,z,2));
        };
    }
    public static final class Placement extends RandomSpreadStructurePlacement {
        public static final MapCodec<Placement> CODEC=MapCodec.unit(Placement::new);
        public Placement(){super(RealmStructuresV5.CELL_CHUNKS,4,RandomSpreadType.LINEAR,0x36455448);}
        @Override public ChunkPos getPotentialStructureChunk(long seed,int x,int z){var s=RealmStructuresV5.slot(seed,Math.floorDiv(x,spacing()),Math.floorDiv(z,spacing()));return s==null?new ChunkPos(Math.floorDiv(x,spacing())*spacing()+2,Math.floorDiv(z,spacing())*spacing()+2):s.chunk();}
        @Override protected boolean isPlacementChunk(ChunkGeneratorStructureState state,int x,int z){return scheduled(state.getLevelSeed(),new ChunkPos(x,z));}
        @Override public StructurePlacementType<?> type(){return PLACEMENT.get();}
    }
    public static final class ComplexStructure extends Structure {
        public static final MapCodec<ComplexStructure> CODEC=RecordCodecBuilder.mapCodec(i->i.group(settingsCodec(i),Codec.STRING.fieldOf("kind").forGetter(s->s.kind)).apply(i,ComplexStructure::new));
        private final String kind;
        public ComplexStructure(StructureSettings settings,String kind){super(settings);if(!kind.equals("temple")&&!kind.equals("camp"))throw new IllegalArgumentException("Unknown complex kind");this.kind=kind;}
        @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context){
            if(!(context.chunkGenerator() instanceof IslandChunkGenerator g)||g.terrainRevision()!=6)return Optional.empty();
            if(context.heightAccessor() instanceof ServerLevel level&&!level.dimension().equals(IslandWorld.TENSION_WORLD))return Optional.empty();
            var chosen=selected(context.seed(),context.chunkPos());if(chosen==null||!chosen.getPath().substring("ethereal/complex/".length()).startsWith(kind))return Optional.empty();
            return site(context.seed(),context.chunkPos(),g,context.randomState(),context.structureTemplateManager(),context.registryAccess(),context.heightAccessor())
                    .map(s->new GenerationStub(s.origin,builder->builder.addPiece(new ComplexPiece(s))));
        }
        @Override public StructureType<?> type(){return TYPE.get();}
    }
    public static final class ComplexPiece extends StructurePiece {
        private final ResourceLocation template;private final BlockPos origin;private final ChunkPos anchor;private final int rotation;private final Set<Long> completedSlices=new HashSet<>();
        public ComplexPiece(Site site){super(PIECE.get(),0,site.bounds());template=site.template;origin=site.origin;anchor=site.anchor;rotation=site.rotation;setOrientation(null);}
        public ComplexPiece(CompoundTag tag){super(PIECE.get(),tag);template=ResourceLocation.parse(tag.getString("Template"));origin=BlockPos.of(tag.getLong("Origin"));anchor=new ChunkPos(tag.getLong("Anchor"));rotation=tag.getInt("Turn");if(!TEMPLATES.contains(template)||rotation<0||rotation>3||tag.getLongArray("CompletedSlices").length>16)throw new IllegalArgumentException("Invalid saved complex");for(long key:tag.getLongArray("CompletedSlices"))completedSlices.add(key);}
        @Override protected synchronized void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag){tag.putString("Template",template.toString());tag.putLong("Origin",origin.asLong());tag.putLong("Anchor",anchor.toLong());tag.putInt("Turn",rotation);tag.putLongArray("CompletedSlices",completedSlices.stream().mapToLong(Long::longValue).sorted().toArray());}
        public ResourceLocation template(){return template;}
        public BlockPos origin(){return origin;}
        @Override public synchronized void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,BoundingBox writable,ChunkPos chunk,BlockPos pivot){
            if(!level.getLevel().dimension().equals(IslandWorld.TENSION_WORLD)||!(generator instanceof IslandChunkGenerator g)||g.terrainRevision()!=6)return;
            if(completedSlices.contains(chunk.toLong())||!boundingBox.intersects(writable))return;
            var data=plan(level.getLevel().getStructureManager(),level.registryAccess(),template);var turn=Rotation.values()[rotation];
            var current=level.getChunk(chunk.x,chunk.z);var writes=new LinkedHashMap<BlockPos,Cell>();
            for(var cell:data.cells){var at=origin.offset(rotated(cell.local,rotation,data));if(!writable.isInside(at))continue;
                var state=StructureLoot.personal(cell.state.rotate(turn));var nbt=StructureLoot.personalNbt(state,cell.data);writes.put(at,new Cell(at,state,nbt));
                if(cell.local.getY()<=1){
                    var column=g.getBaseColumn(at.getX(),at.getZ(),level,level.getLevel().getChunkSource().randomState());
                    for(int y=at.getY()-1;y>=origin.getY()-MAX_SUPPORT&&column.getBlock(y).isAir();y--){var foot=new BlockPos(at.getX(),y,at.getZ());if(writable.isInside(foot))writes.putIfAbsent(foot,new Cell(foot,VaultMaterials.WEATHERED_VAULTSTONE.get().defaultBlockState(),null));}
                }
            }
            for(var cell:writes.values()){
                var state=current.getBlockState(cell.local);
                // A replay preserves all existing contents/changes; native FEATURES never revisits FULL chunks.
                if(!state.isAir()&&!state.canBeReplaced())continue;
                current.setBlockState(cell.local,cell.state,false);
                if(cell.data!=null){var nbt=cell.data.copy();nbt.putInt("x",cell.local.getX());nbt.putInt("y",cell.local.getY());nbt.putInt("z",cell.local.getZ());nbt.putLong("LootTableSeed",mix(level.getSeed()^cell.local.asLong()^template.toString().hashCode()));
                    if(current instanceof LevelChunk live){var be=live.getBlockEntity(cell.local);if(be!=null){be.loadWithComponents(nbt,level.registryAccess());be.setChanged();}}
                    else current.setBlockEntityNbt(nbt);
                }
            }
            completedSlices.add(chunk.toLong());
            // References may place a slice after its anchor finished; persist the piece's replay guard too.
            var owner=level.getChunk(anchor.x,anchor.z,net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS,false);
            if(owner!=null)owner.setUnsaved(true);
        }
    }
}
