package pro.erez.interstice;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class ToxicLiquidBlock extends LiquidBlock {
    public static final ResourceKey<DamageType> TOXIN = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Interstice.ID, "toxin"));
    public ToxicLiquidBlock(FlowingFluid fluid, Properties properties) { super(fluid, properties); }
    @Override
    public boolean isRandomlyTicking(net.minecraft.world.level.block.state.BlockState state) {
        return true;
    }

    @Override
    public void randomTick(net.minecraft.world.level.block.state.BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        super.randomTick(state, level, pos, random);
        pro.erez.interstice.toxin.OverworldToxinHazard.corrodeEnvironment(level, pos, random);
    }

    @Override
    public void animateTick(net.minecraft.world.level.block.state.BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        super.animateTick(state, level, pos, random);
        pro.erez.interstice.toxin.OverworldToxinHazard.animateFumes(level, pos, random);
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living) || !living.isAlive()) return;
        Level world = living.level();
        if (world.isClientSide
                || (living instanceof Player player && (player.isCreative() || player.isSpectator()))) return;
        long now = world.getGameTime();
        var data = living.getPersistentData();
        // Stored on each entity so crossing several liquid cells cannot multiply damage.
        if (data.contains("interstice:last_toxin_tick") && now - data.getLong("interstice:last_toxin_tick") < 20) return;
        var box = living.getBoundingBox().deflate(0.001);
        boolean touching = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x=Mth.floor(box.minX); x<Mth.ceil(box.maxX) && !touching; x++)
            for (int y=Mth.floor(box.minY); y<Mth.ceil(box.maxY) && !touching; y++)
                for (int z=Mth.floor(box.minZ); z<Mth.ceil(box.maxZ); z++) {
                    pos.set(x,y,z);
                    var fluid = world.getFluidState(pos);
                    if (fluid.getFluidType() instanceof ToxicFluidType && FluidContact.overlap(world,pos,fluid,box)>0) {
                        touching=true;break;
                    }
                }
        if (!touching) return;
        data.putLong("interstice:last_toxin_tick", now);
        living.hurt(new DamageSource(world.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TOXIN)), 6.0F);
        pro.erez.interstice.toxin.OverworldToxinHazard.applyVaporHazard(living, world);
    }
}
