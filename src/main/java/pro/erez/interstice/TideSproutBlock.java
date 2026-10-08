package pro.erez.interstice;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import pro.erez.interstice.tide.TideManager;
import pro.erez.interstice.tide.TidePhase;

/**
 * Dynamic tide plant ("Приливной побег").
 *
 * Behavior:
 * - During CALM / WARNING: remains a compact single-block bud (1 block high).
 * - During SURGE: dynamically grows 3 to 7 blocks high into the air, with leafy stems
 *   and a glowing, blooming bioluminescent flower at the top (light level 14).
 * - During EBB: smoothly retracts back down to 1 block high.
 * - Supports Bone Meal: right-clicking with bone meal triggers immediate growth.
 * - Section types:
 *   0 = root (when height=0 it renders as bud; when height>0 it renders as rooted base)
 *   1 = plain stem
 *   2 = leafy stem
 *   3 = top section (bloomed=false renders bud, bloomed=true renders glowing flower)
 */
public final class TideSproutBlock extends Block implements BonemealableBlock {

    public static final IntegerProperty SECTION = IntegerProperty.create("section", 0, 3);
    public static final BooleanProperty BLOOMED = BooleanProperty.create("bloomed");
    public static final IntegerProperty HEIGHT = IntegerProperty.create("height", 0, 6);
    public static final BooleanProperty HARVESTED = BooleanProperty.create("harvested");
    private record Mutation(Level level, BlockPos root) {}
    private static final ThreadLocal<Mutation> MUTATION = new ThreadLocal<>();

    private static final VoxelShape BUD_SHAPE = Block.box(4, 0, 4, 12, 11, 12);
    private static final VoxelShape FLOWER_SHAPE = Block.box(1, 0, 1, 15, 9, 15);
    private static final VoxelShape STEM_SHAPE = Block.box(6, 0, 6, 10, 16, 10);
    private static final VoxelShape LEAFY_STEM_SHAPE = Block.box(2, 0, 2, 14, 16, 14);
    private static final VoxelShape ROOT_SHAPE = net.minecraft.world.phys.shapes.Shapes.or(STEM_SHAPE,
            Block.box(4, 0, 6, 12, 2, 10), Block.box(6, 0, 4, 10, 2, 12));

    public TideSproutBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_CYAN)
                .strength(0.0F)
                .sound(SoundType.WET_GRASS)
                .noCollission()
                .instabreak()
                .lightLevel(s -> !s.getValue(BLOOMED) ? 0 : switch (s.getValue(SECTION)) { case 3 -> s.getValue(HARVESTED) ? 0 : 14; case 0 -> 7; default -> 5; })
                .randomTicks());
        registerDefaultState(stateDefinition.any()
                .setValue(SECTION, 0)
                .setValue(BLOOMED, false)
                .setValue(HARVESTED, false)
                .setValue(HEIGHT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SECTION, BLOOMED, HEIGHT, HARVESTED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int section = state.getValue(SECTION);
        if (section == 3 || section == 0 && state.getValue(HEIGHT) == 0) {
            return state.getValue(BLOOMED) ? FLOWER_SHAPE : BUD_SHAPE;
        }
        return section == 2 ? LEAFY_STEM_SHAPE : section == 0 ? ROOT_SHAPE : STEM_SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(SECTION) == 0) {
            BlockState below = level.getBlockState(pos.below());
            return below.isFaceSturdy(level, pos.below(), Direction.UP);
        }
        BlockState below = level.getBlockState(pos.below());
        return below.is(this);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!state.canSurvive(level, pos)) {
            level.scheduleTick(pos, this, 1);
        }
        return state;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && state.getValue(SECTION) == 0) {
            if (level instanceof ServerLevel serverLevel) {
                pro.erez.interstice.tide.TideSproutTracker.addRoot(serverLevel, pos);
            }
            // Schedule immediate tick upon placement so it reacts right away
            level.scheduleTick(pos, this, 2);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
            return;
        }
        if (state.getValue(SECTION) != 0) return;
        processGrowth(state, level, pos, random);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(SECTION) != 0) return;
        processGrowth(state, level, pos, random);
    }

    private void processGrowth(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var server = level.getServer();
        if (server == null) return;
        if (malformed(level, pos, state.getValue(HEIGHT))) {
            collapse(level, pos, true);
            state = level.getBlockState(pos);
        }
        var tide = TideManager.getSavedData(server).snapshot();
        TidePhase phase = tide.phase();
        boolean surge = (phase == TidePhase.SURGE);
        var harvests=pro.erez.interstice.minerals.SproutHarvestData.get(level);
        if(state.getValue(HARVESTED)&&(!surge||harvests.older(pos,tide.totalCycles()))){
            state=state.setValue(HARVESTED,false);level.setBlock(pos,state,3);harvests.clear(pos);
            var oldTop=level.getBlockState(pos.above(state.getValue(HEIGHT)));
            if(oldTop.is(this))level.setBlock(pos.above(state.getValue(HEIGHT)),oldTop.setValue(HARVESTED,false),3);
        }
        int currentHeight = state.getValue(HEIGHT);
        int targetHeight = 2 + Math.abs((pos.getX() * 31 + pos.getZ() * 17) % 5);

        if (surge) {
            if (currentHeight < targetHeight) {
                grow(level, pos, state, currentHeight + 1, random);
                level.scheduleTick(pos, this, 8 + random.nextInt(10));
            } else {
                if (!state.getValue(BLOOMED)) {
                    level.setBlock(pos, state.setValue(BLOOMED, true), 3);
                    BlockPos topPos = pos.above(currentHeight);
                    BlockState top = level.getBlockState(topPos);
                    if (top.is(this)) {
                        level.setBlock(topPos, top.setValue(BLOOMED, true), 3);
                    }
                }
                // Keep checking periodically during surge so we know when it ends
                level.scheduleTick(pos, this, 20);
            }
        } else {
            if (currentHeight > 0) {
                shrink(level, pos, state, currentHeight);
                level.scheduleTick(pos, this, 8 + random.nextInt(10));
            } else {
                if (state.getValue(BLOOMED)) {
                    level.setBlock(pos, state.setValue(BLOOMED, false), 3);
                }
            }
        }
    }

    private void grow(ServerLevel level, BlockPos root, BlockState rootState, int newHeight, RandomSource random) {
        BlockPos topPos = root.above(newHeight);
        if(level.getChunkSource().getGenerator() instanceof pro.erez.interstice.worldgen.IslandChunkGenerator islands
                &&!pro.erez.interstice.worldgen.IslandChunkGenerator.featureAllowed(level,islands.geometry(),topPos.getX(),topPos.getY(),topPos.getZ()))return;
        if (!level.getBlockState(topPos).isAir()) return;

        // Convert previous top to stem section
        if (newHeight > 1) {
            BlockPos prevTop = root.above(newHeight - 1);
            BlockState prevState = level.getBlockState(prevTop);
            if (prevState.is(this)) {
                int stemSection = (newHeight % 2 == 0) ? 2 : 1;
                level.setBlock(prevTop, prevState.setValue(SECTION, stemSection).setValue(BLOOMED, true), 3);
            }
        }

        // Place new top flower/bud
        BlockState newTop = defaultBlockState()
                .setValue(SECTION, 3)
                .setValue(BLOOMED, true)
                .setValue(HARVESTED, rootState.getValue(HARVESTED))
                .setValue(HEIGHT, newHeight);
        level.setBlock(topPos, newTop, 3);

        // Update root
        level.setBlock(root, rootState.setValue(HEIGHT, newHeight).setValue(BLOOMED, true), 3);

        // Particle burst at new growth tip
        level.sendParticles(ParticleTypes.GLOW, topPos.getX() + 0.5, topPos.getY() + 0.5, topPos.getZ() + 0.5,
                4, 0.2, 0.2, 0.2, 0.02);
    }

    private void shrink(ServerLevel level, BlockPos root, BlockState rootState, int currentHeight) {
        BlockPos topPos = root.above(currentHeight);
        if (level.getBlockState(topPos).is(this)) {
            mutate(level, root, () -> level.removeBlock(topPos, false));
        }
        int newHeight = currentHeight - 1;
        BlockState newRoot = rootState.setValue(HEIGHT, newHeight).setValue(BLOOMED, false);
        level.setBlock(root, newRoot, 3);

        if (newHeight > 0) {
            BlockPos newTop = root.above(newHeight);
            BlockState ns = level.getBlockState(newTop);
            if (ns.is(this)) {
                level.setBlock(newTop, ns.setValue(SECTION, 3).setValue(BLOOMED, false), 3);
            }
        }
    }

    // --- BonemealableBlock implementation ---

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        BlockPos rootPos = findRoot(level, pos, state);
        if (rootPos == null) return false;
        BlockState rootState = level.getBlockState(rootPos);
        return rootState.getValue(HEIGHT) < 6;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos rootPos = findRoot(level, pos, state);
        if (rootPos == null) return;
        BlockState rootState = level.getBlockState(rootPos);
        int currentHeight = rootState.getValue(HEIGHT);
        if (currentHeight < 6) {
            grow(level, rootPos, rootState, currentHeight + 1, random);
        }
    }

    private BlockPos findRoot(LevelReader level, BlockPos pos, BlockState state) {
        if (state.getValue(SECTION) == 0) return pos;
        BlockPos cur = pos.below();
        while (cur.getY() >= level.getMinBuildHeight()) {
            BlockState bs = level.getBlockState(cur);
            if (bs.is(this) && bs.getValue(SECTION) == 0) return cur;
            if (!bs.is(this)) break;
            cur = cur.below();
        }
        return null;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!newState.is(this) && !level.isClientSide && !internallyUpdating(level, pos)) {
            if (state.getValue(SECTION) == 0 && level instanceof ServerLevel serverLevel) {
                pro.erez.interstice.tide.TideSproutTracker.removeRoot(serverLevel, pos);
            }
            BlockPos root = findRoot(level, pos, state);
            if (root != null) collapse(level, root, state.getValue(SECTION) != 0);
            else collapse(level, pos, false);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState state,Level level,BlockPos pos,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit){
        if(!stack.is(net.minecraft.world.item.Items.SHEARS))return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(state.getValue(SECTION)!=3||!state.getValue(BLOOMED)||player.isSpectator()||!player.canInteractWithBlock(pos,1.0))return net.minecraft.world.ItemInteractionResult.FAIL;
        BlockPos root=findRoot(level,pos,state);if(root==null)return net.minecraft.world.ItemInteractionResult.FAIL;
        BlockState base=level.getBlockState(root);
        if(!base.getValue(BLOOMED)||!root.above(base.getValue(HEIGHT)).equals(pos)||malformed(level,root,base.getValue(HEIGHT)))return net.minecraft.world.ItemInteractionResult.FAIL;
        if(level.isClientSide)return net.minecraft.world.ItemInteractionResult.SUCCESS;
        var server=(ServerLevel)level;var tide=TideManager.getSavedData(server.getServer()).snapshot();
        var harvests=pro.erez.interstice.minerals.SproutHarvestData.get(server);
        if(tide.phase()!=TidePhase.SURGE||harvests.locked(root,tide.totalCycles())||base.getValue(HARVESTED)&&!harvests.older(root,tide.totalCycles()))return net.minecraft.world.ItemInteractionResult.FAIL;
        harvests.mark(root,tide.totalCycles());
        level.setBlock(root,base.setValue(HARVESTED,true),3);level.setBlock(pos,state.setValue(HARVESTED,true),3);
        var bud=new net.minecraft.world.item.ItemStack(pro.erez.interstice.minerals.MineralEcology.LUMINOUS_BUD.get());if(!player.addItem(bud))player.drop(bud,false);
        if(!player.isCreative())stack.hurtAndBreak(1,player,net.minecraft.world.entity.LivingEntity.getSlotForHand(hand));
        server.sendParticles(ParticleTypes.GLOW,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,6,.15,.15,.15,.01);
        return net.minecraft.world.ItemInteractionResult.CONSUME;
    }
    @Override public java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder builder){
        // Detached sections never duplicate a rooted plant. The retained root still folds normally.
        if(state.getValue(SECTION)!=0)return java.util.List.of();
        var drops=super.getDrops(state,builder);
        if(state.getValue(HARVESTED))for(var stack:drops)if(stack.is(Interstice.TIDE_SPROUT_ITEM.get())){
            stack.set(net.minecraft.core.component.DataComponents.BLOCK_STATE,net.minecraft.world.item.component.BlockItemStateProperties.EMPTY.with(HARVESTED,true));
            var origin=builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN);
            Long cycle=origin==null?null:pro.erez.interstice.minerals.SproutHarvestData.get(builder.getLevel()).cycle(BlockPos.containing(origin));
            if(cycle!=null){var tag=new net.minecraft.nbt.CompoundTag();tag.putLong("interstice_bud_cycle",cycle);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));}
        }
        return drops;
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);
        if(level instanceof ServerLevel server&&state.getValue(SECTION)==0&&state.getValue(HARVESTED)){
            var data=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if(data!=null&&data.copyTag().contains("interstice_bud_cycle"))pro.erez.interstice.minerals.SproutHarvestData.get(server).mark(pos,data.copyTag().getLong("interstice_bud_cycle"));
        }
    }

    private static boolean internallyUpdating(Level level, BlockPos pos) {
        Mutation mutation = MUTATION.get();
        return mutation != null && mutation.level() == level && mutation.root().getX() == pos.getX()
                && mutation.root().getZ() == pos.getZ() && pos.getY() >= mutation.root().getY()
                && pos.getY() <= mutation.root().getY() + 6;
    }
    private static void mutate(Level level, BlockPos root, Runnable action) {
        Mutation previous = MUTATION.get();
        MUTATION.set(new Mutation(level, root.immutable()));
        try { action.run(); }
        finally { if (previous == null) MUTATION.remove(); else MUTATION.set(previous); }
    }
    private void collapse(Level level, BlockPos root, boolean keepRoot) {
        mutate(level, root, () -> {
            BlockState base = level.getBlockState(root);
            if (keepRoot && base.is(this) && base.getValue(SECTION) == 0) {
                level.setBlock(root, base.setValue(HEIGHT, 0).setValue(BLOOMED, false), 3);
            }
            for (int y = 1; y <= 6; y++) {
                BlockPos part = root.above(y);
                BlockState child = level.getBlockState(part);
                if (child.is(this)) {
                    if (child.getValue(SECTION) == 0) break;
                    level.removeBlock(part, false);
                }
            }
        });
        if (keepRoot) level.scheduleTick(root, this, 2);
    }
    private boolean malformed(Level level, BlockPos root, int height) {
        for (int y = 1; y <= 6; y++) {
            BlockState child = level.getBlockState(root.above(y));
            if (y <= height) {
                if (!child.is(this)) return true;
                int section = child.getValue(SECTION);
                if (y == height ? section != 3 : section != 1 && section != 2) return true;
            } else if (child.is(this)) {
                if (child.getValue(SECTION) == 0) break;
                return true;
            }
        }
        return false;
    }

    /** Wakes up sprouts around the given position so they start growing or shrinking immediately. */
    public static void wakeNearbySprouts(ServerLevel level, BlockPos center, int radius) {
        pro.erez.interstice.tide.TideSproutTracker.wakeNearby(level, center, radius);
    }
}
