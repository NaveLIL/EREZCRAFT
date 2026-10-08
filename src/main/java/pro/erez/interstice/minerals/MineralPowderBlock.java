package pro.erez.interstice.minerals;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Loose chemical dust sinks like powder snow, but does not freeze, extinguish or turn into vanilla snow. */
public final class MineralPowderBlock extends PowderSnowBlock {
    private static final String COOLDOWN="interstice_mineral_powder_until";
    public MineralPowderBlock(){this(BlockBehaviour.Properties.ofFullCopy(Blocks.POWDER_SNOW).strength(.25F).requiresCorrectToolForDrops());}
    private MineralPowderBlock(BlockBehaviour.Properties properties){super(properties);}
    @Override public MapCodec<PowderSnowBlock> codec(){return simpleCodec(p->new MineralPowderBlock(p));}
    @Override public void entityInside(BlockState state,Level level,BlockPos pos,Entity entity){
        if(entity instanceof Player player&&(player.isCreative()||player.isSpectator()))return;
        entity.makeStuckInBlock(state,new Vec3(.65,.85,.65));
        if(!level.isClientSide&&entity instanceof LivingEntity living&&living.isAlive()){
            long now=level.getServer().overworld().getGameTime();
            if(living.getPersistentData().getLong(COOLDOWN)<=now){
                living.getPersistentData().putLong(COOLDOWN,now+40);
                living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,80,0));
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,60,0));
            }
        }
    }
    @Override public ItemStack pickupBlock(Player player,LevelAccessor level,BlockPos pos,BlockState state){return ItemStack.EMPTY;}
    @Override public Optional<SoundEvent> getPickupSound(){return Optional.empty();}
}
