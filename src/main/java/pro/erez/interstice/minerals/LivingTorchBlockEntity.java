package pro.erez.interstice.minerals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Bounded local benefit. It does not alter toxic contact, gas, tide damage or other effects. */
public final class LivingTorchBlockEntity extends BlockEntity {
    public static final int RADIUS=4,EFFECT_TICKS=60,INTERVAL=40;
    public LivingTorchBlockEntity(BlockPos pos,BlockState state){super(MineralEcology.LIVING_TORCH_ENTITY.get(),pos,state);}
    public static <T extends BlockEntity> BlockEntityTicker<T> ticker(Level level,BlockEntityType<T> type){
        return !level.isClientSide&&type==MineralEcology.LIVING_TORCH_ENTITY.get()?(world,pos,state,entity)->tick(world,pos):null;
    }
    public static boolean reaches(Level level,BlockPos pos,Player player){
        Vec3 start=Vec3.atCenterOf(pos),end=player.getEyePosition();
        return player.isAlive()&&!player.isSpectator()&&player.position().distanceToSqr(start)<=RADIUS*RADIUS
                &&level.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getType()==HitResult.Type.MISS;
    }
    public static void tick(Level level,BlockPos pos){
        if(level.isClientSide||Math.floorMod(level.getGameTime()+pos.asLong(),INTERVAL)!=0)return;
        for(var player:level.getEntitiesOfClass(Player.class,new AABB(pos).inflate(RADIUS)))if(reaches(level,pos,player))
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED,EFFECT_TICKS,0,true,false,true));
    }
}
