package pro.erez.interstice.tether;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** At most two saved hooks; absent players never receive force or cause chunk loads. */
public final class WinchBlockEntity extends BlockEntity {
    public record Hook(UUID player,int length,int airTicks,long lastSeen,double paidLength,int descentTicks,boolean descending){
        public Hook(UUID player,int length,int airTicks,long lastSeen){this(player,length,airTicks,lastSeen,length,0,false);}
    }
    private UUID anchorId=UUID.randomUUID();
    private final Map<UUID,Hook> hooks=new LinkedHashMap<>();
    public WinchBlockEntity(BlockPos pos,BlockState state){super(RiftTethers.WINCH_ENTITY.get(),pos,state);}
    public UUID anchorId(){return anchorId;}
    public List<Hook> hooks(){return List.copyOf(hooks.values());}
    public Hook hook(UUID player){return hooks.get(player);}
    public boolean hasRoom(UUID player){return hooks.containsKey(player)||hooks.size()<2;}
    public boolean add(ServerPlayer player,int length){if(!hasRoom(player.getUUID()))return false;hooks.put(player.getUUID(),new Hook(player.getUUID(),length,0,player.server.overworld().getGameTime()));setChanged();return true;}
    public void remove(UUID player){if(hooks.remove(player)!=null)setChanged();}
    void control(UUID player,double paidLength,boolean descending){
        var previous=hooks.get(player);if(previous==null)return;
        hooks.put(player,new Hook(player,(int)Math.ceil(paidLength),previous.airTicks(),previous.lastSeen(),paidLength,
                previous.descentTicks()+(descending?1:0),descending));setChanged();
    }
    public void process(){
        if(!(level instanceof ServerLevel server))return;long now=server.getServer().overworld().getGameTime();
        for(var hook:hooks()){
            var player=server.getServer().getPlayerList().getPlayer(hook.player());
            if(player==null){if(now-hook.lastSeen()>600)remove(hook.player());continue;}
            int air=WinchLinks.apply(this,player,hook);
            var updated=hooks.get(hook.player());
            if(air>=0&&updated!=null)hooks.put(hook.player(),new Hook(hook.player(),updated.length(),air,now,updated.paidLength(),updated.descentTicks(),updated.descending()));
        }if(now%20==0&&!hooks.isEmpty())setChanged();
    }
    public void release(){if(!(level instanceof ServerLevel server))return;
        for(var hook:hooks()){var player=server.getServer().getPlayerList().getPlayer(hook.player());if(player!=null&&WinchLinks.matches(player,this))WinchLinks.detach(player);}
        hooks.clear();setChanged();
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);tag.putUUID("AnchorId",anchorId);var list=new ListTag();
        for(var hook:hooks()){var saved=new CompoundTag();saved.putUUID("Player",hook.player());saved.putInt("Length",hook.length());saved.putInt("AirTicks",hook.airTicks());saved.putLong("LastSeen",hook.lastSeen());saved.putDouble("PaidLength",hook.paidLength());saved.putInt("DescentTicks",hook.descentTicks());list.add(saved);}tag.put("Hooks",list);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);if(tag.hasUUID("AnchorId"))anchorId=tag.getUUID("AnchorId");hooks.clear();
        var list=tag.getList("Hooks",Tag.TAG_COMPOUND);for(int i=0;i<list.size()&&hooks.size()<2;i++){var value=list.getCompound(i);int length=value.getInt("Length");if(value.hasUUID("Player")&&length>=8&&length<=WinchLinks.MAX_ROPE_LENGTH){var player=value.getUUID("Player");double paid=value.contains("PaidLength",Tag.TAG_ANY_NUMERIC)?value.getDouble("PaidLength"):length;
            if(!Double.isFinite(paid)||paid<8||paid>WinchLinks.MAX_ROPE_LENGTH||Math.ceil(paid)!=length)continue;
            hooks.putIfAbsent(player,new Hook(player,length,Math.max(0,Math.min(60,value.getInt("AirTicks"))),value.getLong("LastSeen"),paid,Math.max(0,value.getInt("DescentTicks")),false));}}
    }
}
