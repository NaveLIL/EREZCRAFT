package pro.erez.interstice.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;

/** Smoke-only read-only diagnostics. Normal server ticks do all lifecycle work; no counters are changed. */
final class NativeChunkSettler {
    private static Field updating,pending,tasks;
    static final class Session {
        private final String purpose;
        private final int limit;
        private long first=-1,last=-1;
        private volatile int stable;
        private volatile boolean timedOut,quiet;
        private volatile String error;
        private volatile JsonObject snapshot=new JsonObject();
        Session(String purpose,int limit){this.purpose=purpose;this.limit=limit;}
        boolean ready(){return quiet&&stable>=20&&error==null;}
        boolean failed(){return timedOut||error!=null;}
        String failure(){return error!=null?error:"Generation did not quiesce within "+limit+" actual server ticks";}
        JsonObject report(){return snapshot.deepCopy();}
        void tick(MinecraftServer server) {
            try {
                for(var level:server.getAllLevels()) {
                    long deadline=System.nanoTime()+2_000_000;
                    for(int calls=0;calls<32&&System.nanoTime()<deadline;calls++)if(!level.getChunkSource().pollTask())break;
                }
                if(updating==null){updating=field("updatingChunkMap");pending=field("pendingUnloads");tasks=field("pendingGenerationTasks");}
                int holderCount=0,refs=0,savePending=0,notReady=0,generationTasks=0,failedSaves=0;var examples=new JsonArray();
                for(var level:server.getAllLevels()) {
                    var map=level.getChunkSource().chunkMap;
                    Set<ChunkHolder> holders=Collections.newSetFromMap(new IdentityHashMap<>());
                    for(Object value:((Map<?,?>)updating.get(map)).values())holders.add((ChunkHolder)value);
                    for(Object value:((Map<?,?>)pending.get(map)).values())holders.add((ChunkHolder)value);
                    generationTasks+=((List<?>)tasks.get(map)).size();holderCount+=holders.size();
                    for(var holder:holders) {
                        int ref=holder.getGenerationRefCount();refs+=ref;
                        if(!holder.getSaveSyncFuture().isDone())savePending++;
                        if(holder.getSaveSyncFuture().isCompletedExceptionally())failedSaves++;
                        if(!holder.isReadyForSaving()) {
                            notReady++;
                            if(examples.size()<10){var e=new JsonObject();e.addProperty("dimension",level.dimension().location().toString());e.addProperty("chunk",holder.getPos().toString());e.addProperty("generation_refs",ref);e.addProperty("save_done",holder.getSaveSyncFuture().isDone());examples.add(e);}
                        }
                    }
                }
                long now=server.overworld().getGameTime();if(first<0)first=now;
                quiet=refs==0&&savePending==0&&notReady==0&&generationTasks==0&&failedSaves==0;
                if(last!=now){stable=quiet?stable+1:0;last=now;}
                if(now-first>limit)timedOut=true;
                var out=new JsonObject();out.addProperty("purpose",purpose);out.addProperty("server_tick",now);out.addProperty("elapsed_actual_server_ticks",now-first);
                out.addProperty("stable_actual_server_ticks",stable);out.addProperty("holders_checked",holderCount);out.addProperty("generation_ref_count",refs);
                out.addProperty("save_futures_pending",savePending);out.addProperty("holders_not_ready_for_saving",notReady);out.addProperty("pending_generation_tasks",generationTasks);
                out.addProperty("save_futures_failed",failedSaves);out.addProperty("timed_out",timedOut);out.add("non_quiet_examples",examples);snapshot=out;
                if(stable==20||(now-first)%20==0)System.out.println("NATIVE_MINING_SETTLE "+out);
            } catch(Throwable failure){error=failure.toString();var out=new JsonObject();out.addProperty("inspection_error",error);snapshot=out;failure.printStackTrace();}
        }
    }
    private NativeChunkSettler() {}
    private static Field field(String name)throws ReflectiveOperationException{var f=ChunkMap.class.getDeclaredField(name);f.setAccessible(true);return f;}
}
