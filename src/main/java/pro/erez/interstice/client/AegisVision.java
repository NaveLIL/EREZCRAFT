package pro.erez.interstice.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import pro.erez.interstice.gear.AegisPower;

/** Local optics only: never gives other players' targets the shared Glowing effect. */
public final class AegisVision {
    public static final double RANGE=24;
    private AegisVision(){}
    public static boolean active(){var mc=Minecraft.getInstance();return mc.player!=null&&mc.getCameraEntity()==mc.player&&AegisPower.powered(mc.player,EquipmentSlot.HEAD)&&!mc.player.isSpectator();}
    public static boolean nightVision(LivingEntity entity){return entity==Minecraft.getInstance().player&&active();}
    public static boolean scanned(Entity entity){
        var mc=Minecraft.getInstance();return active()&&entity!=mc.player&&entity instanceof LivingEntity living
                &&living.isAlive()&&!(living instanceof ArmorStand)&&!entity.isSpectator()&&entity.level()==mc.player.level()
                &&entity.distanceToSqr(mc.player)<=RANGE*RANGE;
    }
    public static int color(Entity entity){return entity instanceof Enemy?0xff625f:entity instanceof Player?0xffd36e:0x6be0d8;}
}
