package pro.erez.interstice.navigation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.worldgen.IslandChunkGenerator;
import pro.erez.interstice.worldgen.RealmBiomes;
import pro.erez.interstice.worldgen.RealmStructuresV5;

/** Server-owned bounded searches. Verification reads authored blocks/container metadata and never opens or restores loot. */
@EventBusSubscriber(modid=Interstice.ID)
public final class StructureSearchJobs {
    public static final int RADIUS=1024,SOURCE_CELL_BUDGET=64,PROPOSAL_BUDGET=32,FULL_BUDGET=8,COOLDOWN_TICKS=1200;
    private static final String COOLDOWN="interstice_survey_cooldown_until";
    private static final TicketType<UUID> TICKET=TicketType.create("interstice_survey",Comparator.comparing(UUID::toString),100);
    public record Result(BlockPos position,RealmStructuresV5.Family family,ResourceLocation template,boolean unopenedLoot) {}
    public record ScanReport(UUID id,ResourceLocation dimension,BlockPos origin,int radius,int sourceCells,int proposals,
                             int numericChecked,int fullCandidates,int asyncRequests,int verifiedFull,Result found,boolean completed,String reason,double maxStepMillis) {}
    private record TemplateCell(BlockPos local,BlockState state,ResourceLocation loot) {}
    private record Plan(int width,int height,int depth,List<TemplateCell> cells,List<TemplateCell> containers) {}
    private record Placement(int x,int z,Plan plan,int width,int depth,RealmStructuresV5.Slot slot) {}
    private static final Map<StructureTemplate,Plan> PLANS=new WeakHashMap<>();
    private static final Map<MinecraftServer,State> SERVERS=new WeakHashMap<>();
    private static final class State {
        final Map<UUID,Job> jobs=new LinkedHashMap<>();final LinkedHashMap<UUID,ScanReport> reports=new LinkedHashMap<>();
        UUID pendingOwner;int next;
    }
    private static final class Job {
        final UUID id=UUID.randomUUID(),owner;final ServerLevel level;final InteractionHand hand;final ItemStack stack;
        final BlockPos origin;final List<RealmStructuresV5.Slot> proposals;final long deadline;
        int cursor,numeric,full,async,verified;long refresh;double maxStep;Result best;
        CompletableFuture<ChunkResult<ChunkAccess>> pending;RealmStructuresV5.Slot pendingSlot;ChunkPos ticket;
        Job(ServerPlayer player,InteractionHand hand,List<RealmStructuresV5.Slot> proposals){
            owner=player.getUUID();level=player.serverLevel();this.hand=hand;stack=player.getItemInHand(hand);origin=player.blockPosition().immutable();this.proposals=proposals;
            deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);
        }
    }
    private StructureSearchJobs() {}
    private static State state(MinecraftServer server){return SERVERS.computeIfAbsent(server,key->new State());}
    public static boolean isActive(MinecraftServer server,UUID owner){return state(server).jobs.containsKey(owner);}
    public static ScanReport lastReport(MinecraftServer server,UUID owner){return state(server).reports.get(owner);}
    public static int pendingTickets(MinecraftServer server){var state=SERVERS.get(server);return state==null?0:(int)state.jobs.values().stream().filter(job->job.ticket!=null).count();}
    private static long now(ServerPlayer player){return player.server.overworld().getGameTime();}
    private static boolean supported(ServerLevel level){return level.getChunkSource().getGenerator() instanceof IslandChunkGenerator g&&(g.terrainRevision()==5||g.terrainRevision()==6);}
    public static boolean begin(ServerPlayer player,InteractionHand hand){
        if(!player.isAlive()||player.isSpectator()||!supported(player.serverLevel())||!(player.getItemInHand(hand).getItem() instanceof StructureSurveyorItem)){
            player.displayClientMessage(Component.translatable("message.interstice.survey.realm_only"),true);return false;
        }
        var state=state(player.server);var stack=player.getItemInHand(hand);
        if(state.jobs.containsKey(player.getUUID())){player.displayClientMessage(Component.translatable("message.interstice.survey.busy"),true);return false;}
        long until=player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getLong(COOLDOWN);
        if(player.getCooldowns().isOnCooldown(stack.getItem())||until>now(player)&&until-now(player)<=COOLDOWN_TICKS){
            player.displayClientMessage(Component.translatable("message.interstice.survey.cooldown"),true);return false;
        }
        if(state.jobs.size()>=8){player.displayClientMessage(Component.translatable("message.interstice.survey.server_busy"),true);return false;}
        var job=new Job(player,hand,proposalWindow(player.serverLevel(),player.blockPosition()));
        var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();tag.putUUID(StructureSurveyorItem.JOB,job.id);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        state.jobs.put(player.getUUID(),job);player.getInventory().setChanged();
        player.displayClientMessage(Component.translatable("message.interstice.survey.started",RADIUS,FULL_BUDGET),true);return true;
    }
    /** Declared source window: nearest64 region cells, at most32 deterministic proposals, never expanded after a miss. */
    public static List<RealmStructuresV5.Slot> proposalWindow(ServerLevel level,BlockPos origin){
        int cell=RealmStructuresV5.CELL_CHUNKS*16;
        var cells=new ArrayList<ChunkPos>();
        for(int x=Math.floorDiv(origin.getX()-RADIUS,cell);x<=Math.floorDiv(origin.getX()+RADIUS,cell);x++)
            for(int z=Math.floorDiv(origin.getZ()-RADIUS,cell);z<=Math.floorDiv(origin.getZ()+RADIUS,cell);z++)cells.add(new ChunkPos(x,z));
        cells.sort(Comparator.comparingDouble(p->distance(origin,p.x*cell+cell*.5,p.z*cell+cell*.5)));
        var proposals=new ArrayList<RealmStructuresV5.Slot>();
        for(int i=0;i<Math.min(SOURCE_CELL_BUDGET,cells.size());i++){
            var cellPos=cells.get(i);var slot=RealmStructuresV5.slot(level.getSeed(),cellPos.x,cellPos.z);
            if(slot!=null&&distance(origin,slot.chunk().getMinBlockX()+8,slot.chunk().getMinBlockZ()+8)<=RADIUS*(double)RADIUS)proposals.add(slot);
        }
        proposals.sort(Comparator.<RealmStructuresV5.Slot>comparingInt(s->s.family()==RealmStructuresV5.Family.OBSERVATION_POST?0:1)
                .thenComparingDouble(s->distance(origin,s.chunk().getMinBlockX()+8,s.chunk().getMinBlockZ()+8)).thenComparingLong(s->s.chunk().toLong()));
        return List.copyOf(proposals.subList(0,Math.min(PROPOSAL_BUDGET,proposals.size())));
    }
    private static double distance(BlockPos origin,double x,double z){double dx=x-origin.getX(),dz=z-origin.getZ();return dx*dx+dz*dz;}
    private static long mix(long n){n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;n=(n^(n>>>27))*0x94d049bb133111ebL;return n^(n>>>31);}
    private static synchronized Plan plan(ServerLevel level,RealmStructuresV5.Slot slot){
        var template=level.getStructureManager().get(slot.template()).orElseThrow(()->new IllegalStateException("Missing authored survey template "+slot.template()));
        var cached=PLANS.get(template);if(cached!=null)return cached;
        var tag=template.save(new CompoundTag());var size=tag.getList("size",Tag.TAG_INT);var palette=new ArrayList<BlockState>();
        for(var entry:tag.getList("palette",Tag.TAG_COMPOUND))palette.add(NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(Registries.BLOCK),(CompoundTag)entry));
        var cells=new ArrayList<TemplateCell>();var containers=new ArrayList<TemplateCell>();
        for(var entry:tag.getList("blocks",Tag.TAG_COMPOUND)){
            var cell=(CompoundTag)entry;var pos=cell.getList("pos",Tag.TAG_INT);var nbt=cell.getCompound("nbt");
            var loot=ResourceLocation.tryParse(nbt.getString("LootTable"));var item=new TemplateCell(new BlockPos(pos.getInt(0),pos.getInt(1),pos.getInt(2)),palette.get(cell.getInt("state")),loot);
            cells.add(item);if(loot!=null)containers.add(item);
        }
        if(containers.isEmpty()||cells.size()<8||size.getInt(0)>13||size.getInt(2)>13)throw new IllegalStateException("Survey template lacks bounded authored geometry/container");
        var result=new Plan(size.getInt(0),size.getInt(1),size.getInt(2),List.copyOf(cells),List.copyOf(containers));PLANS.put(template,result);return result;
    }
    private static Placement placement(ServerLevel level,RealmStructuresV5.Slot slot){
        var plan=plan(level,slot);int width=slot.rotation()%2==0?plan.width:plan.depth,depth=slot.rotation()%2==0?plan.depth:plan.width;
        long key=mix(level.getSeed()^slot.chunk().toLong()^slot.template().toString().hashCode());
        return new Placement(slot.chunk().getMinBlockX()+1+Math.floorMod(mix(key^0x58L),15-width),
                slot.chunk().getMinBlockZ()+1+Math.floorMod(mix(key^0x5AL),15-depth),plan,width,depth,slot);
    }
    private static BlockPos rotated(BlockPos p,Placement at){return switch(at.slot.rotation()){
        case 1->new BlockPos(at.plan.depth-1-p.getZ(),p.getY(),p.getX());
        case 2->new BlockPos(at.plan.width-1-p.getX(),p.getY(),at.plan.depth-1-p.getZ());
        case 3->new BlockPos(p.getZ(),p.getY(),at.plan.width-1-p.getX());default->p;
    };}
    private static boolean plausible(ServerLevel level,RealmStructuresV5.Slot slot){
        var at=placement(level,slot);var g=(IslandChunkGenerator)level.getChunkSource().getGenerator();var random=level.getChunkSource().randomState();int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
        // A conservative veneer allowance avoids rejecting a real surface shifted by geology.
        for(int dx=0;dx<at.width;dx++)for(int dz=0;dz<at.depth;dz++){
            var column=g.getBaseColumn(at.x+dx,at.z+dz,level,random);int top=-1;
            for(int y=g.geometry().maxLand();y>=g.geometry().lowerSeaTop()+3;y--)if(!column.getBlock(y).isAir()&&column.getBlock(y).getFluidState().isEmpty()){top=y;break;}
            if(top<g.geometry().lowerSeaTop()+3)return false;low=Math.min(low,top);high=Math.max(high,top);
        }
        var biome=g.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(at.x+at.width/2),QuartPos.fromBlock(high),QuartPos.fromBlock(at.z+at.depth/2),random.sampler());
        boolean realmBiome=biome.is(RealmBiomes.ASH_ISLANDS)||biome.is(RealmBiomes.PALE_GARDENS)||biome.is(RealmBiomes.STONE_VAULTS)||biome.is(RealmBiomes.CRIMSON_THICKETS);
        return realmBiome&&high-low<=RealmStructuresV5.MAX_SLOPE+2&&high+at.plan.height<=g.geometry().maxLand();
    }
    /** Actual loaded FULL inspection only. getLootTable is metadata; inventory access would unpack and is deliberately absent. */
    public static Result inspectLoaded(ServerLevel level,RealmStructuresV5.Slot slot){
        var chunk=level.getChunkSource().getChunkNow(slot.chunk().x,slot.chunk().z);
        return chunk==null||!chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL)?null:inspect(level,chunk,slot);
    }
    private static Result inspect(ServerLevel level,LevelChunk chunk,RealmStructuresV5.Slot slot){
        var at=placement(level,slot);var profile=((IslandChunkGenerator)level.getChunkSource().getGenerator()).geometry();
        for(var container:at.plan.containers){
            var local=rotated(container.local,at);int x=at.x+local.getX(),z=at.z+local.getZ();
            for(int base=profile.lowerSeaTop()+5;base+at.plan.height<=profile.maxLand();base++){
                var position=new BlockPos(x,base+local.getY(),z);
                if(!chunk.getBlockState(position).is(container.state.getBlock())||!(chunk.getBlockEntity(position) instanceof RandomizableContainerBlockEntity entity))continue;
                var table=entity.getLootTable();
                if(table!=null&&(!table.location().equals(container.loot)||entity.getLootTableSeed()!=mix(level.getSeed()^position.asLong()^slot.template().toString().hashCode())))continue;
                boolean matches=true;
                for(var cell:at.plan.cells){var p=rotated(cell.local,at).offset(at.x,base,at.z);
                    // Opening a door/barrel or using its inventory does not remove the authored block silhouette.
                    if(!chunk.getBlockState(p).is(cell.state.getBlock())){matches=false;break;}
                }
                if(matches)return new Result(new BlockPos(at.x+at.width/2,base,at.z+at.depth/2),slot.family(),slot.template(),table!=null);
            }
        }return null;
    }
    private static boolean leased(Job job,ServerPlayer player){
        if(player==null||!player.isAlive()||player.isSpectator()||player.serverLevel()!=job.level||player.getItemInHand(job.hand)!=job.stack)return false;
        var tag=job.stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();return tag.hasUUID(StructureSurveyorItem.JOB)&&tag.getUUID(StructureSurveyorItem.JOB).equals(job.id);
    }
    private static void release(State state,Job job){
        if(job.pending!=null&&!job.pending.isDone())job.pending.cancel(false);
        if(job.ticket!=null){job.level.getChunkSource().removeRegionTicket(TICKET,job.ticket,0,job.id);job.ticket=null;}
        if(job.owner.equals(state.pendingOwner))state.pendingOwner=null;
    }
    private static void consider(Job job,LevelChunk chunk,RealmStructuresV5.Slot slot){
        job.verified++;var found=inspect(job.level,chunk,slot);if(found==null||distance(job.origin,found.position().getX(),found.position().getZ())>RADIUS*(double)RADIUS)return;
        if(job.best==null||found.family()==RealmStructuresV5.Family.OBSERVATION_POST&&job.best.family()!=RealmStructuresV5.Family.OBSERVATION_POST
                ||(found.family()==RealmStructuresV5.Family.OBSERVATION_POST)==(job.best.family()==RealmStructuresV5.Family.OBSERVATION_POST)
                &&distance(job.origin,found.position().getX(),found.position().getZ())<distance(job.origin,job.best.position().getX(),job.best.position().getZ()))job.best=found;
    }
    private static void finish(State state,Job job,ServerPlayer player,boolean completed,String reason){
        release(state,job);state.jobs.remove(job.owner);
        var tag=job.stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(tag.hasUUID(StructureSurveyorItem.JOB)&&tag.getUUID(StructureSurveyorItem.JOB).equals(job.id)){tag.remove(StructureSurveyorItem.JOB);job.stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}
        var report=new ScanReport(job.id,job.level.dimension().location(),job.origin,RADIUS,SOURCE_CELL_BUDGET,job.proposals.size(),job.numeric,job.full,job.async,job.verified,job.best,completed,reason,job.maxStep/1_000_000.);
        state.reports.put(job.owner,report);if(state.reports.size()>128)state.reports.remove(state.reports.keySet().iterator().next());
        if(completed&&player!=null){
            long now=now(player);if(job.best!=null)StructureSurveyorItem.store(job.stack,job.best,job.level.dimension().location(),now);
            var persistent=player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);persistent.putLong(COOLDOWN,now+COOLDOWN_TICKS);player.getPersistentData().put(Player.PERSISTED_NBT_TAG,persistent);
            player.getCooldowns().addCooldown(job.stack.getItem(),COOLDOWN_TICKS);
            if(!player.isCreative())job.stack.hurtAndBreak(1,player,LivingEntity.getSlotForHand(job.hand));
            if(job.best==null)player.displayClientMessage(Component.translatable("message.interstice.survey.none",RADIUS,job.full),false);
            else{var p=job.best.position();player.displayClientMessage(Component.translatable("message.interstice.survey.found",StructureSurveyorItem.family(job.best.family()),p.getX(),p.getY(),p.getZ(),StructureSurveyorItem.direction(player.blockPosition(),p),job.full),false);}
            player.getInventory().setChanged();player.containerMenu.broadcastChanges();
        }else if(player!=null)player.displayClientMessage(Component.translatable("message.interstice.survey.cancelled"),true);
        System.out.println("REALM_SURVEY_SCAN "+report);
    }
    private static void step(State state,Job job,MinecraftServer server){
        var player=server.getPlayerList().getPlayer(job.owner);
        if(!leased(job,player)){finish(state,job,player,false,"owner_or_item_changed");return;}
        if(System.nanoTime()>job.deadline){finish(state,job,player,false,"bounded_wall_deadline");return;}
        if(job.pending!=null){
            if(!job.pending.isDone()){
                if(job.level.getGameTime()-job.refresh>=20){job.level.getChunkSource().addRegionTicket(TICKET,job.ticket,0,job.id);job.refresh=job.level.getGameTime();}return;
            }
            try{var result=job.pending.join().orElse(null);if(result instanceof LevelChunk chunk&&chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL))consider(job,chunk,job.pendingSlot);}
            catch(RuntimeException unavailable){/* An unavailable/failed chunk is a measured miss, never invented structure evidence. */}
            release(state,job);job.pending=null;job.pendingSlot=null;return;
        }
        if(job.cursor>=job.proposals.size()||job.full>=FULL_BUDGET){finish(state,job,player,true,job.full>=FULL_BUDGET?"full_budget_exhausted":"declared_proposals_complete");return;}
        if(state.pendingOwner!=null)return;
        var slot=job.proposals.get(job.cursor++);var loaded=job.level.getChunkSource().getChunkNow(slot.chunk().x,slot.chunk().z);
        // Existing real blocks remain authoritative even if a player later changed the
        // terrain underneath them; raw density is only a filter for new chunk requests.
        if(loaded!=null&&loaded.getPersistedStatus().isOrAfter(ChunkStatus.FULL)){job.full++;consider(job,loaded,slot);return;}
        job.numeric++;if(!plausible(job.level,slot))return;
        job.full++;
        state.pendingOwner=job.owner;job.pendingSlot=slot;job.ticket=slot.chunk();job.async++;job.refresh=job.level.getGameTime();
        job.level.getChunkSource().addRegionTicket(TICKET,job.ticket,0,job.id);
        // Public getChunkFuture blocks its caller when called ON the server thread. Invoke
        // its supported off-thread branch: Minecraft enqueues setup on its own main processor.
        job.pending=CompletableFuture.supplyAsync(()->job.level.getChunkSource().getChunkFuture(slot.chunk().x,slot.chunk().z,ChunkStatus.FULL,true),Util.backgroundExecutor()).thenCompose(future->future);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        var state=SERVERS.get(event.getServer());if(state==null||state.jobs.isEmpty())return;
        var jobs=List.copyOf(state.jobs.values());var job=jobs.get(Math.floorMod(state.next++,jobs.size()));long began=System.nanoTime();
        try{step(state,job,event.getServer());}catch(RuntimeException failure){finish(state,job,event.getServer().getPlayerList().getPlayer(job.owner),false,"inspection_error:"+failure.getClass().getSimpleName());}
        finally{job.maxStep=Math.max(job.maxStep,System.nanoTime()-began);}
    }
    public static void cancel(ServerPlayer player){var state=SERVERS.get(player.server);if(state==null)return;var job=state.jobs.get(player.getUUID());if(job!=null)finish(state,job,player,false,"cancelled");}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer player)cancel(player);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player)cancel(player);}
    @SubscribeEvent public static void death(LivingDeathEvent event){if(event.getEntity() instanceof ServerPlayer player)cancel(player);}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level){
        var state=SERVERS.get(level.getServer());if(state!=null)for(var job:List.copyOf(state.jobs.values()))if(job.level==level)
            finish(state,job,level.getServer().getPlayerList().getPlayer(job.owner),false,"dimension_unloaded");
    }}
    @SubscribeEvent public static void stop(ServerStoppingEvent event){var state=SERVERS.get(event.getServer());
        if(state!=null)for(var job:List.copyOf(state.jobs.values()))finish(state,job,event.getServer().getPlayerList().getPlayer(job.owner),false,"server_stopping");
        SERVERS.remove(event.getServer());
    }
}
