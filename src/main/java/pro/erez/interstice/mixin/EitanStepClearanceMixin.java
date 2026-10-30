package pro.erez.interstice.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import pro.erez.interstice.vehicle.EitanBlock;
import pro.erez.interstice.vehicle.EitanWheelBlock;

/** Sable's step probe adds 10 cm to the player's actual dimensions. That rejects
 * the authored 1.56 m voxel passage even for a 1.50 m crouched player. Keep a
 * positive 1 cm margin and finer probe increments only on this chassis. Actual
 * SAT collision and the player's dimensions remain unchanged.
 */
@Pseudo
@Mixin(targets="dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision",remap=false)
public abstract class EitanStepClearanceMixin {
    private static boolean eitan(LevelAccelerator level,Iterable<BlockPos> blocks){for(var p:blocks){var b=level.getBlockState(p).getBlock();if(b instanceof EitanBlock||b instanceof EitanBlock.Control||b instanceof EitanWheelBlock)return true;}return false;}
    @ModifyConstant(method="tryStepUp",constant=@Constant(doubleValue=.1))
    private static double interstice$passageMargin(double original,@Local(argsOnly=true) LevelAccelerator level,@Local(argsOnly=true) Iterable<BlockPos> blocks){return eitan(level,blocks)?.01:original;}
    @ModifyConstant(method="tryStepUp",constant=@Constant(doubleValue=.0625))
    private static double interstice$fineStepProbe(double original,@Local(argsOnly=true) LevelAccelerator level,@Local(argsOnly=true) Iterable<BlockPos> blocks){return eitan(level,blocks)?.015625:original;}
}
