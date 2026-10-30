package pro.erez.interstice.fauna;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.EventHooks;

/** Bounded claw damage to vegetation, never a general-purpose block demolisher. */
public final class LozhnokorenVegetation {
    public static final TagKey<Block> PLANTS=TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("interstice","lozhnokoren_breakable_plants"));
    public static boolean vegetation(Level level,BlockPos pos){
        var s=level.getBlockState(pos);if(s.hasBlockEntity()&&!(s.getBlock() instanceof pro.erez.interstice.ecology.ClingweedBlock)||s.getDestroySpeed(level,pos)<0)return false;
        if(s.is(PLANTS))return true;
        if(s.is(BlockTags.LEAVES))return !s.hasProperty(LeavesBlock.PERSISTENT)||!s.getValue(LeavesBlock.PERSISTENT);
        if(!s.is(BlockTags.LOGS)||s.getBlock() instanceof RotatedPillarBlock&&net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().startsWith("stripped_"))return false;
        // Bark logs only when attached to a crown of natural leaves. Persistent decorative foliage does not qualify.
        for(int y=0;y<=18;y++)for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){var p=pos.offset(x,y,z);if(!level.hasChunkAt(p))continue;var leaf=level.getBlockState(p);if(leaf.is(BlockTags.LEAVES)&&leaf.hasProperty(LeavesBlock.PERSISTENT)&&!leaf.getValue(LeavesBlock.PERSISTENT))return true;}
        return false;
    }
    public static int sweep(Lozhnokoren mob){
        var level=mob.level();if(mob.phase()!=Lozhnokoren.Phase.STRIKE||(int)mob.phaseAge(0)!=Lozhnokoren.IMPACT_TICK)return 0;if(level.isClientSide||!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)||!EventHooks.canEntityGrief(level,mob))return 0;
        var head=mob.headPosition();var center=head.add(mob.forward().scale(1.35));int count=0;
        for(var p:BlockPos.betweenClosed((int)Math.floor(center.x-2.8),(int)Math.floor(mob.getY()),(int)Math.floor(center.z-2.8),(int)Math.ceil(center.x+2.8),(int)Math.ceil(mob.getY()+3.3),(int)Math.ceil(center.z+2.8))){
            if(count>=32)break;if(!level.hasChunkAt(p)||!level.getWorldBorder().isWithinBounds(p))continue;
            Vec3 d=Vec3.atCenterOf(p).subtract(head);double len=d.horizontalDistance();if(len>3.5||len>.01&&mob.forward().dot(new Vec3(d.x,0,d.z).normalize())<.45||!vegetation(level,p))continue;
            var state=level.getBlockState(p);if(!state.canEntityDestroy(level,p,mob)||!EventHooks.onEntityDestroyBlock(mob,p,state))continue;
            // Recheck after the protection event; a handler may replace the block.
            if(level.getBlockState(p)!=state)continue;if(level.destroyBlock(p,false,mob))count++;
        }return count;
    }
    private LozhnokorenVegetation(){}
}
