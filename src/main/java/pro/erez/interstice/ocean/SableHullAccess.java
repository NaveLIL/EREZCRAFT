package pro.erez.interstice.ocean;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
/** Typed calls avoid reflecting over Sable overloads that mention client-only classes. */
public final class SableHullAccess {
    public static List<?> subLevels(Level world){
        if(world instanceof ServerLevel server)return SubLevelContainer.getContainer(server).getAllSubLevels();
        var container=SubLevelContainer.getContainer(world);return container==null?List.of():container.getAllSubLevels();
    }
    private SableHullAccess(){}
}
