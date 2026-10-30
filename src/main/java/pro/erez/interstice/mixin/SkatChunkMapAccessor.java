package pro.erez.interstice.mixin;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ChunkMap.class)
public interface SkatChunkMapAccessor {
    @Accessor("level") ServerLevel interstice$level();
}
