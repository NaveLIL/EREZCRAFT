package pro.erez.interstice.test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.minerals.MineralEcology;
import pro.erez.interstice.minerals.PackOreDepositsV6;
import pro.erez.interstice.minerals.RiftOreBlock;
import pro.erez.interstice.worldgen.IslandWorld;

@GameTestHolder("interstice_mining")
@PrefixGameTestTemplate(false)
public final class PackOreV6GameTests {
    private static final GeometryProfile PROFILE=GeometryProfile.TALL;
    private static ProtoChunk empty(GameTestHelper h,ChunkPos position) {
        var chunk=new ProtoChunk(position,UpgradeData.EMPTY,LevelHeightAccessor.create(0,256),
                h.getLevel().registryAccess().registryOrThrow(Registries.BIOME),null);
        chunk.setPersistedStatus(ChunkStatus.NOISE);return chunk;
    }
    private static ProtoChunk rock(GameTestHelper h,ChunkPos position,int top,RiftOreBlock.Host host) {
        var chunk=empty(h,position);
        for(int x=position.getMinBlockX();x<=position.getMaxBlockX();x++)for(int z=position.getMinBlockZ();z<=position.getMaxBlockZ();z++)
            for(int y=1;y<=top;y++)chunk.setBlockState(new BlockPos(x,y,z),y==top?Interstice.ABYSSAL_TURF.get().defaultBlockState():host.stone(),false);
        return chunk;
    }
    private static List<PackOreDepositsV6.Resolved> vanilla() {
        return PackOreDepositsV6.resolve(id->id.getNamespace().equals("minecraft")?BuiltInRegistries.BLOCK.getOptional(id):Optional.empty());
    }
    private static PackOreDepositsV6.Counts generate(ProtoChunk chunk,long seed,List<PackOreDepositsV6.Resolved> ores) {
        return PackOreDepositsV6.generateResolved(PROFILE,chunk,seed,IslandWorld.TENSION_WORLD,6,ores);
    }
    private static void same(GameTestHelper h,ProtoChunk a,ProtoChunk b) {
        for(int x=a.getPos().getMinBlockX();x<=a.getPos().getMaxBlockX();x++)for(int z=a.getPos().getMinBlockZ();z<=a.getPos().getMaxBlockZ();z++)for(int y=0;y<256;y++)
            h.assertTrue(a.getBlockState(new BlockPos(x,y,z)).equals(b.getBlockState(new BlockPos(x,y,z))),"Seed or generation order changed a block at "+x+","+y+","+z);
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void optionalModsAreAbsentSafelyAndCommonResourcesChooseOnlyOneProvider(GameTestHelper h) {
        h.assertTrue(vanilla().size()==7,"Standalone V6 does not retain its seven vanilla resources");
        for(var resource:vanilla())h.assertTrue(pro.erez.interstice.worldgen.cave.CaveFeatures.rock(resource.block().defaultBlockState()),"Generated vanilla ore lost its cave support role");
        h.assertTrue(PackOreDepositsV6.resolve(id->Optional.empty()).isEmpty(),"Missing mod IDs fell back to registry AIR");
        var production=PackOreDepositsV6.generate(PROFILE,rock(h,new ChunkPos(0,0),80,RiftOreBlock.Host.RIFTSTONE),
                20261006,IslandWorld.TENSION_WORLD,6);
        for(var rule:PackOreDepositsV6.deposits())if(rule.alternatives().stream().noneMatch(BuiltInRegistries.BLOCK::containsKey))
            h.assertTrue(!production.byResource().containsKey(rule.name()),"Unregistered optional ore was generated as a default registry block");
        var mi=ResourceLocation.parse("modern_industrialization:tin_ore");var mek=ResourceLocation.parse("mekanism:tin_ore");
        Map<ResourceLocation,Block> both=Map.of(mi,Blocks.GOLD_ORE,mek,Blocks.IRON_ORE);
        var selected=PackOreDepositsV6.resolve(id->Optional.ofNullable(both.get(id)));
        h.assertTrue(selected.size()==1&&selected.getFirst().id().equals(mi),"MI and Mekanism duplicate the tin budget");
        var fallback=PackOreDepositsV6.resolve(id->id.equals(mek)?Optional.of(Blocks.IRON_ORE):Optional.empty());
        h.assertTrue(fallback.size()==1&&fallback.getFirst().id().equals(mek),"Missing MI cannot use Mekanism tin");
        for(String name:List.of("tin","lead","uranium")) {
            var rule=PackOreDepositsV6.deposits().stream().filter(d->d.name().equals(name)).findFirst().orElseThrow();
            h.assertTrue(rule.alternatives().size()==2&&rule.alternatives().getFirst().getNamespace().equals("modern_industrialization"),"Shared resource has no explicit ordered fallback");
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void wrongDimensionRevisionAndSavedSurfaceOrFullChunksAreUntouched(GameTestHelper h) {
        var a=rock(h,new ChunkPos(-1,2),80,RiftOreBlock.Host.RIFTSTONE);var original=rock(h,a.getPos(),80,RiftOreBlock.Host.RIFTSTONE);
        for(var dimension:List.of(Level.OVERWORLD,IslandWorld.VANILLA_WORLD,IslandWorld.TALL_WORLD))
            h.assertTrue(PackOreDepositsV6.generateResolved(PROFILE,a,20261006,dimension,6,vanilla()).total()==0,"Pack ores leaked to another dimension");
        for(int revision=1;revision<=5;revision++)h.assertTrue(PackOreDepositsV6.generateResolved(PROFILE,a,20261006,IslandWorld.TENSION_WORLD,revision,vanilla()).total()==0,"Pack ores changed a legacy revision");
        for(var status:List.of(ChunkStatus.SURFACE,ChunkStatus.FEATURES,ChunkStatus.FULL)) {
            a.setPersistedStatus(status);h.assertTrue(generate(a,20261006,vanilla()).total()==0,"Existing chunk received retrogen");
        }
        same(h,a,original);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void seedAndIndependentChunkOrderKeepBoundedConnectedDeposits(GameTestHelper h) {
        int all=0;
        for(var position:List.of(new ChunkPos(-1,-1),new ChunkPos(0,0),new ChunkPos(5,-3))) {
            var a=rock(h,position,110,RiftOreBlock.Host.PALESTONE);var b=rock(h,position,110,RiftOreBlock.Host.PALESTONE);
            var first=generate(a,4294967297L,vanilla());
            // Generate an unrelated chunk between repeats; there is no mutable/global random state.
            generate(rock(h,new ChunkPos(position.x+5,position.z+7),190,RiftOreBlock.Host.VAULTSTONE),1,vanilla());
            var second=generate(b,4294967297L,vanilla());same(h,a,b);
            h.assertTrue(first.equals(second),"Same seed changed its reported resource budgets");
            for(var resource:vanilla())h.assertTrue(first.byResource().get(resource.deposit().name())<=resource.deposit().maximumBlocks(),"A mountain multiplied its ore budget");
            all+=first.total();
        }
        h.assertTrue(all>0,"Fixed ore sample contained no actual placed ores");
        var connected=rock(h,new ChunkPos(0,0),80,RiftOreBlock.Host.RIFTSTONE);
        var id=ResourceLocation.parse("minecraft:iron_ore");
        var one=new PackOreDepositsV6.Deposit("connected_fixture",1,7,60,4,96,7,7,1,List.of(id));
        int placed=generate(connected,20261006,List.of(new PackOreDepositsV6.Resolved(one,id,Blocks.IRON_ORE))).total();
        var remaining=new java.util.HashSet<BlockPos>();
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=7;y<=60;y++) {
            var pos=new BlockPos(x,y,z);if(connected.getBlockState(pos).is(Blocks.IRON_ORE))remaining.add(pos);
        }
        h.assertTrue(placed>0&&remaining.size()==placed,"A single vein has no actual placed cells");
        var queue=new java.util.ArrayDeque<BlockPos>();var first=remaining.iterator().next();remaining.remove(first);queue.add(first);
        while(!queue.isEmpty()) {
            var pos=queue.removeFirst();
            for(var direction:net.minecraft.core.Direction.values()) {
                var neighbor=pos.relative(direction);if(remaining.remove(neighbor))queue.addLast(neighbor);
            }
        }
        h.assertTrue(remaining.isEmpty(),"A single vein jumped across air instead of sharing block faces");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void allFiveRockHostsWorkWithoutReplacingSoilFluidsAirOrNativeMinerals(GameTestHelper h) {
        for(var host:RiftOreBlock.Host.values()) {
            var chunk=rock(h,new ChunkPos(0,0),80,host);int placed=generate(chunk,20261006,vanilla()).total();
            h.assertTrue(placed>0,"Natural host cannot contain vanilla ores: "+host);
        }
        var chunk=rock(h,new ChunkPos(0,0),80,RiftOreBlock.Host.RIFTSTONE);
        var protectedStates=List.of(Blocks.AIR.defaultBlockState(),Interstice.HEAVY_BLOCK.get().defaultBlockState(),
                Interstice.LIGHT_SEA.get().defaultBlockState(),MineralEcology.ROOT_LOAM.get().defaultBlockState(),
                MineralEcology.TOXIC_SAND.get().defaultBlockState(),MineralEcology.RIFTSILVER_SEAM.get().defaultBlockState(),
                MineralEcology.UMBRAL_COAL_ORE.get().defaultBlockState(),MineralEcology.PHOSPHORITE_ORE.get().defaultBlockState(),
                MineralEcology.VITRIOLITE_ORE.get().defaultBlockState());
        for(int x=0;x<9;x++)for(int z=0;z<16;z++)for(int y=7;y<75;y++)chunk.setBlockState(new BlockPos(x,y,z),protectedStates.get(x),false);
        generate(chunk,20261006,vanilla());
        for(int x=0;x<9;x++)for(int z=0;z<16;z++)for(int y=7;y<75;y++)h.assertTrue(chunk.getBlockState(new BlockPos(x,y,z)).equals(protectedStates.get(x)),"Pack ore replaced protected material");
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)h.assertTrue(chunk.getBlockState(new BlockPos(x,80,z)).is(Interstice.ABYSSAL_TURF.get()),"Pack ore replaced accepted surface");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void eachSeparateIslandRoofKeepsAtLeastFourDryCoverBlocks(GameTestHelper h) {
        var chunk=rock(h,new ChunkPos(0,0),180,RiftOreBlock.Host.RIFTSTONE);
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=60;y<=110;y++)chunk.setBlockState(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),false);
        generate(chunk,20261006,vanilla());
        for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
            for(int y=56;y<=59;y++)h.assertTrue(chunk.getBlockState(new BlockPos(x,y,z)).is(Interstice.RIFTSTONE.get()),"Highest heightmap hid an exposed lower island roof");
            for(int y=177;y<180;y++)h.assertTrue(chunk.getBlockState(new BlockPos(x,y,z)).is(Interstice.RIFTSTONE.get()),"Ore changed the upper island surface layers");
            for(int y=60;y<=110;y++)h.assertTrue(chunk.getBlockState(new BlockPos(x,y,z)).isAir(),"Ore plugged the air gap");
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void lateMineralsStayRareAndDoNotAddFreeTechnologicalOrBuddingResources(GameTestHelper h) {
        var rules=PackOreDepositsV6.deposits();h.assertTrue(rules.size()==21,"Pack resource contract changed without review");
        var draconium=rules.stream().filter(d->d.name().equals("draconium")).findFirst().orElseThrow();
        h.assertTrue(draconium.attempts()==1&&draconium.chance()==32&&draconium.maxVein()==2&&draconium.maxY()<=24&&draconium.minDepth()>=20,"Draconium bypasses late deep expeditions");
        h.assertTrue(rules.stream().noneMatch(d->List.of("coal","platinum","iridium","titanium","certus").contains(d.name())),"Pack ores replaced native fuel or technological/meteorite branches");
        var chunk=rock(h,new ChunkPos(0,0),110,RiftOreBlock.Host.VAULTSTONE);
        h.assertTrue(generate(chunk,20261006,List.of()).total()==0,"Absent optional ore list modified the world");h.succeed();
    }
    @GameTest(template="empty",batch="pack_ore_native_v6",timeoutTicks=1200)
    public static void fixedActualNativeNoiseKeepsEverySurfaceOceanAndCaveCell(GameTestHelper h) {
        int oreCells=0,chunks=0;long began=System.nanoTime();
        for(long seed:new long[]{0,20261006}) {
            var fixture=NativeTerrainV6GameTests.fixture(h,seed);
            var positions=List.of(new ChunkPos(-1,-1),new ChunkPos(0,-1),new ChunkPos(-1,0),new ChunkPos(0,0));
            for(var chunk:NativeTerrainV6GameTests.await(h,NativeTerrainV6GameTests.serial(h,fixture,positions),90)) {
                h.assertTrue(chunk.getPersistedStatus()==ChunkStatus.NOISE,"Ore fixture is not actual native NOISE");
                var before=new BlockState[16][16][256];
                for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
                    int wx=chunk.getPos().getMinBlockX()+x,wz=chunk.getPos().getMinBlockZ()+z;
                    var uncarved=fixture.generator().terrainColumn(fixture.random(),wx,wz);
                    for(int y=0;y<256;y++) {
                        var pos=new BlockPos(wx,y,wz);
                        var mapped=fixture.generator().terrainMaterialAt(wx,y,wz,chunk.getBlockState(pos),uncarved);
                        chunk.setBlockState(pos,mapped,false);before[x][z][y]=mapped;
                    }
                }
                var counts=generate(chunk,seed,vanilla());int changed=0;
                for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=0;y<256;y++) {
                    var pos=new BlockPos(chunk.getPos().getMinBlockX()+x,y,chunk.getPos().getMinBlockZ()+z);
                    var old=before[x][z][y];var after=chunk.getBlockState(pos);
                    if(old.equals(after))continue;
                    changed++;h.assertTrue(RiftOreBlock.Host.from(old)!=null&&vanilla().stream().anyMatch(r->after.is(r.block())),"Native ore changed cave, ocean or non-rock cell");
                    h.assertTrue(old.canOcclude()==after.canOcclude()&&old.getFluidState().equals(after.getFluidState()),"Native ore changed terrain silhouette or fluid geometry");
                    for(int d=1;d<=4;d++)h.assertTrue(y+d<256&&before[x][z][y+d].canOcclude()&&before[x][z][y+d].getFluidState().isEmpty(),"Native ore reached exposed multilayer surface");
                }
                h.assertTrue(changed==counts.total(),"Actual native ore receipt differs from changed cells");oreCells+=changed;chunks++;
            }
        }
        h.assertTrue(oreCells>0,"Fixed real V6 native samples did not actually generate ores");
        System.out.println("V6_PACK_ORE_NATIVE dimension=interstice:islands_v6 seeds=2 noise_chunks="+chunks+" placed="+oreCells+" wall_ms="+(System.nanoTime()-began)/1_000_000);
        h.succeed();
    }
}
