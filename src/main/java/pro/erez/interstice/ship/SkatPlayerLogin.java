package pro.erez.interstice.ship;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;
/** Sable's Entity.load hook runs before vanilla moves a player out of the overworld. */
public final class SkatPlayerLogin {
    public static int restores;
    public static void restore(Entity entity,CompoundTag tag){
        if(!(entity instanceof ServerPlayer player)||!tag.hasUUID("LoginPoint"))return;
        var id=ResourceLocation.tryParse(tag.getString("Dimension"));if(id==null)return;
        var target=player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,id));
        if(target==null||!pro.erez.interstice.ocean.LowerToxinSurface.active(target))return;
        var point=SubLevelTrackingPointSavedData.getOrLoad(target).take(tag.getUUID("LoginPoint"),true);if(point==null)return;
        var p=point.position();player.setPosRaw(p.x(),p.y(),p.z());
        if(point.subLevelId()!=null&&point.localAnchor()!=null)((PlayerFreezeExtension)player).sable$freezeTo(point.subLevelId(),new Vector3d(point.localAnchor()).add(0,.2,0));
        if(!tag.getCompound("RootVehicle").getCompound("Entity").getString("id").equals("interstice:eitan_seat"))tag.remove("RootVehicle");restores++;
        if(System.getProperty("interstice.studioSmoke","").equals("skat"))System.out.println("SKAT_LOGIN_TRACKING restored "+player.level().dimension().location()+" -> "+id+" ship="+point.subLevelId()+" anchor="+point.localAnchor());
    }
    private SkatPlayerLogin(){}
}
