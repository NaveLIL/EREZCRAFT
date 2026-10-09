package pro.erez.interstice.test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.navigation.RealmNavigation;
import pro.erez.interstice.navigation.StructureSearchJobs;
import pro.erez.interstice.navigation.StructureSurveyorItem;
import pro.erez.interstice.worldgen.IslandWorld;
import pro.erez.interstice.worldgen.RealmStructuresV5;

/** Prepared authored FULL fixtures prove locator/loot ownership; they are not natural-placement or Survival evidence. */
@GameTestHolder("interstice_gear")
@PrefixGameTestTemplate(false)
public final class StructureSurveyGameTests {
    private static final BlockPos ORIGIN=new BlockPos(0,81,0);
    private record Fixture(ServerLevel level,RealmStructuresV5.Slot slot,LevelChunk chunk,RandomizableContainerBlockEntity container) {}
    private StructureSurveyGameTests() {}
    private static Fixture fixture(GameTestHelper h){
        var level=h.getLevel().getServer().getLevel(IslandWorld.VANILLA_WORLD);
        h.assertTrue(level!=null,"V5 fixture dimension absent");
        var window=StructureSearchJobs.proposalWindow(level,ORIGIN);
        var slot=window.stream().filter(s->s.family()==RealmStructuresV5.Family.OBSERVATION_POST).findFirst().orElseThrow(()->new IllegalStateException("No observation-post proposal in fixed1024/64cell fixture window"));
        // Preload only this declared eight-site window. The fixture is explicitly prepared.
        for(var candidate:window.subList(0,Math.min(8,window.size())))level.getChunk(candidate.chunk().x,candidate.chunk().z);
        var chunk=level.getChunk(slot.chunk().x,slot.chunk().z);h.assertTrue(chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL),"Fixture is not FULL");
        var pos=new BlockPos.MutableBlockPos();
        for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++)
            for(int y=80;y<=GeometryProfile.TALL.maxLand();y++)chunk.setBlockState(pos.set(x,y,z),(y==80?Interstice.RIFTSTONE.get():Blocks.AIR).defaultBlockState(),false);
        h.assertTrue(RealmStructuresV5.generate(GeometryProfile.TALL,chunk,level.getSeed(),level.getStructureManager(),level.registryAccess(),35),"Prepared authored template failed atomic placement");
        RandomizableContainerBlockEntity found=null;
        for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++)for(int y=81;y<=95;y++){
            var at=new BlockPos(x,y,z);var packed=chunk.getBlockEntityNbt(at);
            if(packed!=null&&packed.contains("LootTable")&&chunk.getBlockEntity(at) instanceof RandomizableContainerBlockEntity entity){
                // A LevelChunk fixture creates its BE before setBlockEntityNbt; apply the authored
                // packed data once during preparation. The locator itself never does this.
                entity.loadWithComponents(packed,level.registryAccess());found=entity;
            }
        }
        h.assertTrue(found!=null&&found.getLootTable()!=null,"Prepared template lacks its actual unopened loot container");
        h.assertTrue(StructureSearchJobs.inspectLoaded(level,slot)!=null,"Actual FULL authored geometry was not recognized before locator use");
        System.out.println("PREPARED_SURVEY_FULL template="+slot.template()+" chunk="+chunk.getPos()+" scope=prepared_authored_fixture_not_natural_placement");
        return new Fixture(level,slot,chunk,found);
    }
    private static ServerPlayer player(GameTestHelper h,ServerLevel level){
        var p=TestPlayers.create(h,new BlockPos(3,2,3),GameType.SURVIVAL);
        p.teleportTo(level,.5,81,.5,Set.of(),0,0);p.hasChangedDimension();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(RealmNavigation.SURVEYOR.get()));return p;
    }
    private static void await(GameTestHelper h,ServerLevel level,ServerPlayer... players){
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);
        h.getLevel().getServer().managedBlock(()->{
            level.getChunkSource().pollTask();StructureSearchJobs.tick(new ServerTickEvent.Post(()->true,h.getLevel().getServer()));
            if(System.nanoTime()>deadline)throw new IllegalStateException("Bounded asynchronous survey fixture did not complete");
            return java.util.Arrays.stream(players).noneMatch(p->StructureSearchJobs.isActive(p.server,p.getUUID()));
        });
    }
    @GameTest(template="empty",timeoutTicks=2400)
    public static void twoServerPlayersCompleteIndependentFiniteScansWithActualCoordinatesAndCooldown(GameTestHelper h){
        var f=fixture(h);var a=player(h,f.level);var b=player(h,f.level);
        try{
            var table=f.container.getLootTable();long seed=f.container.getLootTableSeed();
            h.assertTrue(a.getMainHandItem().use(f.level,a,InteractionHand.MAIN_HAND).getResult().consumesAction()
                    &&b.getMainHandItem().use(f.level,b,InteractionHand.MAIN_HAND).getResult().consumesAction(),"Native item use did not start independent scans");
            await(h,f.level,a,b);
            for(var p:List.of(a,b)){
                var report=StructureSearchJobs.lastReport(p.server,p.getUUID());var mark=StructureSurveyorItem.mark(p.getMainHandItem());
                h.assertTrue(report!=null&&report.completed()&&report.fullCandidates()<=8&&report.proposals()<=32&&report.sourceCells()<=64,"Survey did not complete within its declared source/FULL budgets: "+report);
                h.assertTrue(report.found()!=null&&report.found().family()==RealmStructuresV5.Family.OBSERVATION_POST&&mark!=null,"Survey reported only a potential hash or failed to store its actual post coordinate: "+report);
                h.assertTrue(p.getMainHandItem().getDamageValue()==1&&p.getCooldowns().isOnCooldown(RealmNavigation.SURVEYOR.get()),"Completed scan did not spend exactly one finite charge and cooldown");
                h.assertTrue(!StructureSearchJobs.begin(p,InteractionHand.MAIN_HAND),"Immediate repeat bypassed completed-scan cooldown");
            }
            h.assertTrue(f.container.getLootTable().equals(table)&&f.container.getLootTableSeed()==seed,"Survey opened or rewrote the actual loot source");
            h.assertTrue(StructureSearchJobs.pendingTickets(a.server)==0,"Completed searches retain owned generation tickets");h.succeed();
        }finally{StructureSearchJobs.cancel(a);StructureSearchJobs.cancel(b);TestPlayers.remove(a);TestPlayers.remove(b);}
    }
    @GameTest(template="empty",timeoutTicks=2400)
    public static void unopenedUsedAndRemovedContainersNeverGetTheirLootRecreatedByInspection(GameTestHelper h){
        var f=fixture(h);var before=f.container.saveWithoutMetadata(f.level.registryAccess());
        h.assertTrue(StructureSearchJobs.inspectLoaded(f.level,f.slot)!=null&&before.equals(f.container.saveWithoutMetadata(f.level.registryAccess())),"Read-only recognition changes unopened container metadata");
        f.container.getItem(0);h.assertTrue(f.container.getLootTable()==null,"Fixture did not actually unpack its loot for the used-container case");
        var used=f.container.saveWithoutMetadata(f.level.registryAccess());
        var found=StructureSearchJobs.inspectLoaded(f.level,f.slot);
        h.assertTrue(found!=null&&!found.unopenedLoot()&&used.equals(f.container.saveWithoutMetadata(f.level.registryAccess())),"Used-container recognition regenerates contents or reinstalls its table");
        f.level.setBlock(f.container.getBlockPos(),Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(StructureSearchJobs.inspectLoaded(f.level,f.slot)==null,"A potential source hash remains a found structure after its actual authored container was removed");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=2400)
    public static void copiedHandAndDimensionUnloadCancelWithoutChargeOrOwnedTickets(GameTestHelper h){
        var f=fixture(h);var p=player(h,f.level);
        try{
            var original=p.getMainHandItem();h.assertTrue(StructureSearchJobs.begin(p,InteractionHand.MAIN_HAND),"Fixture cannot start its owned search");
            p.setItemInHand(InteractionHand.MAIN_HAND,original.copy());StructureSearchJobs.tick(new ServerTickEvent.Post(()->true,p.server));
            var report=StructureSearchJobs.lastReport(p.server,p.getUUID());
            h.assertTrue(report!=null&&!report.completed()&&!StructureSearchJobs.isActive(p.server,p.getUUID())&&original.getDamageValue()==0&&p.getMainHandItem().getDamageValue()==0,"A copied item takes over or pays for another object's cancelled job");
            h.assertTrue(StructureSearchJobs.begin(p,InteractionHand.MAIN_HAND),"An aborted uncompleted search imposes completed-scan cost/cooldown");
            StructureSearchJobs.unload(new LevelEvent.Unload(f.level));
            h.assertTrue(!StructureSearchJobs.isActive(p.server,p.getUUID())&&StructureSearchJobs.pendingTickets(p.server)==0
                    &&!StructureSearchJobs.lastReport(p.server,p.getUUID()).completed()&&p.getMainHandItem().getDamageValue()==0,"Dimension-unload handler retains a job/ticket or spends a completion charge");h.succeed();
        }finally{StructureSearchJobs.cancel(p);TestPlayers.remove(p);}
    }
}
