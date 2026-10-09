package pro.erez.interstice.test;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.ecology.*;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.worldgen.*;

@GameTestHolder("interstice_ecology") @PrefixGameTestTemplate(false)
public final class ForestEcologyGameTests {
    private static final BlockPos POD=new BlockPos(6,3,6);
    private static BlockPos plant(GameTestHelper h){var p=h.absolutePos(POD);h.getLevel().setBlock(p.below(),Blocks.STONE.defaultBlockState(),3);h.getLevel().setBlock(p,RealmEcology.SPORE_POD.get().defaultBlockState(),3);return p;}
    private static ServerPlayer player(GameTestHelper h){return TestPlayers.create(h,new BlockPos(6,3,6),GameType.SURVIVAL);}
    private static List<AreaEffectCloud> gas(GameTestHelper h,BlockPos p){return h.getLevel().getEntitiesOfClass(AreaEffectCloud.class,new AABB(p).inflate(7),e->e.getTags().contains(SporePodGas.ENTITY_TAG));}
    private static void step(GameTestHelper h,BlockPos p,ServerPlayer player){player.teleportTo(h.getLevel(),p.getX()+.5,p.getY()+.125,p.getZ()+.5,Set.of(),0,0);player.hasChangedDimension();h.getLevel().getBlockState(p).getBlock().stepOn(h.getLevel(),p,h.getLevel().getBlockState(p),player);}
    private static void use(GameTestHelper h,BlockPos p,ServerPlayer player){player.gameMode.useItemOn(player,h.getLevel(),player.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(p),Direction.UP,p,false));}
    @GameTest(template="empty",timeoutTicks=150)
    public static void realScheduledWarningProducesOneFiniteCloudAndChangesNoTerrain(GameTestHelper h){
        var p=plant(h);h.getLevel().setBlock(p.east(),Blocks.STONE.defaultBlockState(),3);var player=player(h);step(h,p,player);
        h.assertTrue(h.getLevel().getBlockState(p).getValue(SporePodBlock.PHASE)==SporePodBlock.Phase.PRIMED&&h.getLevel().getBlockState(p).getValue(SporePodBlock.FUSE)==12&&gas(h,p).isEmpty(),"Physical step did not begin one real warning before gas");
        h.runAtTickTime(5,()->{h.assertTrue(gas(h,p).isEmpty()&&!player.hasEffect(MobEffects.POISON),"Gas damages during its visible warning");});
        h.runAtTickTime(18,()->{try{h.assertTrue(h.getLevel().getBlockState(p).getValue(SporePodBlock.PHASE)==SporePodBlock.Phase.SPENT&&gas(h,p).size()==1&&player.hasEffect(MobEffects.POISON)&&player.hasEffect(MobEffects.WEAKNESS)
                &&h.getLevel().getBlockState(p.east()).is(Blocks.STONE)&&h.getLevel().getBlockState(p.below()).is(Blocks.STONE),"Mine did not finish once, apply local toxin, or left an explosion hole");}finally{TestPlayers.remove(player);}});
        h.runAtTickTime(125,()->{h.assertTrue(gas(h,p).isEmpty()&&h.getLevel().getBlockState(p).getValue(SporePodBlock.PHASE)==SporePodBlock.Phase.SPENT,"Mine cloud or spent mine reactivates forever");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=70)
    public static void nativeShearsDefuseDuringWarningTwoPlayersCannotDuplicateShell(GameTestHelper h){
        var p=plant(h);var first=player(h);var second=player(h);first.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SHEARS));second.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SHEARS));step(h,p,first);
        h.runAtTickTime(4,()->{try{
            use(h,p,first);use(h,p,second);h.assertTrue(h.getLevel().getBlockState(p).isAir()&&first.getMainHandItem().getDamageValue()==1&&second.getMainHandItem().getDamageValue()==0,"Ordinary shears did not win one authoritative defuse or second actor paid twice");
            int count=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(4),e->e.getItem().is(RealmEcology.POD_SHELL.get())).stream().mapToInt(e->e.getItem().getCount()).sum()+first.getInventory().countItem(RealmEcology.POD_SHELL.get())+second.getInventory().countItem(RealmEcology.POD_SHELL.get());
            h.assertTrue(count==1&&gas(h,p).isEmpty(),"Two-player defuse created two shells or emitted gas");
        }finally{TestPlayers.remove(first);TestPlayers.remove(second);}});
        h.runAtTickTime(35,()->{h.assertTrue(gas(h,p).isEmpty(),"Old queued warning tick emitted after safe removal");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void jumpItemsCreativeAndSpectatorDoNotPressTheMine(GameTestHelper h){
        var p=plant(h);var player=player(h);
        try{player.teleportTo(h.getLevel(),p.getX()+.5,p.getY()+1,p.getZ()+.5,Set.of(),0,0);var state=h.getLevel().getBlockState(p);state.entityInside(h.getLevel(),p,player);
            state.entityInside(h.getLevel(),p,new ItemEntity(h.getLevel(),p.getX()+.5,p.getY(),p.getZ()+.5,new ItemStack(Items.DIAMOND)));
            player.setGameMode(GameType.CREATIVE);step(h,p,player);player.setGameMode(GameType.SPECTATOR);step(h,p,player);
            h.assertTrue(h.getLevel().getBlockState(p).getValue(SporePodBlock.PHASE)==SporePodBlock.Phase.ARMED&&gas(h,p).isEmpty(),"Non-foot contact or protected game mode presses a mine");h.succeed();
        }finally{TestPlayers.remove(player);}
    }
    @GameTest(template="empty",timeoutTicks=150)
    public static void gasActualEntityNbtPreservesAbsoluteExpiryAndDoesNotResetOrMixClingweed(GameTestHelper h){
        var p=h.absolutePos(POD);h.assertTrue(ClingweedGas.emit(h.getLevel(),p)&&SporePodGas.emit(h.getLevel(),p,12),"New gas improperly suppresses the old independent clingweed source");
        var cloud=gas(h,p).getFirst();long expires=cloud.getPersistentData().getLong(SporePodGas.EXPIRES);
        h.assertTrue(!SporePodGas.emit(h.getLevel(),p.east(),0)&&cloud.getPersistentData().getLong(SporePodGas.EXPIRES)==expires,"Nearby mine re-emission duplicates or extends an existing gas lifetime");
        CompoundTag[] saved={null};h.runAtTickTime(30,()->{saved[0]=new CompoundTag();cloud.saveWithoutId(saved[0]);var restored=new AreaEffectCloud(h.getLevel(),p.getX(),p.getY(),p.getZ());restored.load(saved[0]);
            h.assertTrue(restored.getTags().contains(SporePodGas.ENTITY_TAG)&&restored.getPersistentData().getLong(SporePodGas.EXPIRES)==expires&&restored.getPersistentData().getLong(SporePodGas.READY)==cloud.getPersistentData().getLong(SporePodGas.READY),"Native entity serialization loses absolute cloud timing");
            cloud.discard();h.getLevel().addFreshEntity(restored);
        });
        h.runAtTickTime(125,()->{h.assertTrue(gas(h,p).isEmpty(),"Saved late-loaded cloud restarted a full lifetime");var late=new AreaEffectCloud(h.getLevel(),p.getX(),p.getY(),p.getZ());late.load(saved[0]);SporePodGas.process(late);h.assertTrue(late.isRemoved(),"Unloaded gas whose deadline passed survives when loaded");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void forestReedSharesOldStingCooldownAndNativeShearsHarvestOneFiber(GameTestHelper h){
        var p=h.absolutePos(POD);h.getLevel().setBlock(p.below(),Blocks.STONE.defaultBlockState(),3);h.getLevel().setBlock(p,RealmEcology.VENOM_REED.get().defaultBlockState(),3);var player=player(h);
        try{var state=h.getLevel().getBlockState(p);state.entityInside(h.getLevel(),p,player);float health=player.getHealth();long until=player.getPersistentData().getLong(CavePlantBlock.STING_COOLDOWN_TAG);
            CaveEcology.STING_FROND.get().defaultBlockState().entityInside(h.getLevel(),p,player);state.entityInside(h.getLevel(),p,player);
            h.assertTrue(player.getHealth()==health&&player.getPersistentData().getLong(CavePlantBlock.STING_COOLDOWN_TAG)==until&&player.hasEffect(MobEffects.POISON),"Two native stinging species multiply a single contact");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SHEARS));use(h,p,player);
            int count=player.getInventory().countItem(RealmEcology.VENOM_FIBER.get())+h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(3),e->e.getItem().is(RealmEcology.VENOM_FIBER.get())).stream().mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(h.getLevel().getBlockState(p).isAir()&&count==1&&player.getMainHandItem().getDamageValue()==1,"Native reed shears remove wrong amount, lose material or skip durability");h.succeed();
        }finally{TestPlayers.remove(player);}
    }
    private static ProtoChunk fixture(GameTestHelper h,boolean canopy){
        var c=new ProtoChunk(new ChunkPos(-1,0),UpgradeData.EMPTY,LevelHeightAccessor.create(0,256),h.getLevel().registryAccess().registryOrThrow(Registries.BIOME),null);
        var biome=h.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(RealmBiomes.PALE_GARDENS);c.fillBiomesFromNoise((x,y,z,s)->biome,Climate.empty());c.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.BIOMES);
        for(int x=-16;x<0;x++)for(int z=0;z<16;z++){c.setBlockState(new BlockPos(x,35,z),Interstice.ABYSSAL_TURF.get().defaultBlockState(),false);if(canopy)c.setBlockState(new BlockPos(x,43,z),Interstice.GLOOMCROWN_LEAVES.get().defaultBlockState(),false);}return c;
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void floraUsesOnlyNewForestedGroundDeterministicallyWithoutOldBiomeWrites(GameTestHelper h){
        int found=0;
        for(long seed=0;seed<160&&found==0;seed++){
            var old=fixture(h,true);h.assertTrue(ForestFlora.decorate(GeometryProfile.TALL,old,seed,h.getLevel(),4).equals(new ForestFlora.Counts(0,0)),"Archive revision receives new flora");
            var plain=fixture(h,false);h.assertTrue(ForestFlora.decorate(GeometryProfile.TALL,plain,seed,h.getLevel(),5).equals(new ForestFlora.Counts(0,0)),"Unforested plain becomes a mine carpet");
            var first=fixture(h,true);var second=fixture(h,true);var counts=ForestFlora.decorate(GeometryProfile.TALL,first,seed,h.getLevel(),5);h.assertTrue(counts.equals(ForestFlora.decorate(GeometryProfile.TALL,second,seed,h.getLevel(),5)),"Identical seeded forest produces different budgets");
            for(int x=-16;x<0;x++)for(int z=0;z<16;z++)h.assertTrue(first.getBlockState(new BlockPos(x,36,z)).equals(second.getBlockState(new BlockPos(x,36,z))),"Same seed produces different forest placement");found=counts.reeds()+counts.pods();
        }h.assertTrue(found>0,"Prepared eligible forest yielded no new native ecosystem");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void globalMineAnchorsHaveRealSpacingAcrossNegativeChunkBoundaries(GameTestHelper h){
        var points=new ArrayList<BlockPos>();for(int x=-70;x<70;x++)for(int z=-70;z<70;z++)if(ForestFlora.mineAnchor(73,x,z))points.add(new BlockPos(x,0,z));
        h.assertTrue(points.size()>2,"Global anchor sample contains no real candidates");for(int i=0;i<points.size();i++)for(int j=0;j<i;j++)h.assertTrue(points.get(i).distSqr(points.get(j))>324,"Mine anchors ignore minimum spacing across region/chunk signs");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void nativeBlockStateCodecKeepsRemainingWarningInsteadOfRestartingFuse(GameTestHelper h){
        var p=plant(h);var state=h.getLevel().getBlockState(p).setValue(SporePodBlock.PHASE,SporePodBlock.Phase.PRIMED).setValue(SporePodBlock.FUSE,5);
        var ops=h.getLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var encoded=net.minecraft.world.level.block.state.BlockState.CODEC.encodeStart(ops,state).getOrThrow();
        var restored=net.minecraft.world.level.block.state.BlockState.CODEC.parse(ops,encoded).getOrThrow();h.assertTrue(restored.getValue(SporePodBlock.FUSE)==5&&restored.getValue(SporePodBlock.PHASE)==SporePodBlock.Phase.PRIMED,"Native persisted block state lost the remaining warning");
        h.getLevel().setBlock(p,restored,3);h.getLevel().scheduleTick(p,RealmEcology.SPORE_POD.get(),1);
        h.runAtTickTime(2,()->{h.assertTrue(gas(h,p).isEmpty(),"Restored partial warning immediately gassed its visitor");});
        h.runAtTickTime(9,()->{h.assertTrue(gas(h,p).size()==1&&h.getLevel().getBlockState(p).getValue(SporePodBlock.PHASE)==SporePodBlock.Phase.SPENT,"Saved five-tick warning restarted twelve ticks or failed to finish");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void realShellPlacementIsSafeAndRearmingAfterDefuseHasANewFullWarning(GameTestHelper h){
        var p=plant(h);var player=player(h);step(h,p,player);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SHEARS));
        h.runAtTickTime(4,()->{
            use(h,p,player);var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(3),e->e.getItem().is(RealmEcology.POD_SHELL.get()));
            h.assertTrue(drops.size()==1,"Defused mine did not leave its real portable shell");var shell=drops.getFirst().getItem().copy();drops.getFirst().discard();
            player.teleportTo(h.getLevel(),p.getX()-1.5,p.getY(),p.getZ()+.5,Set.of(),0,0);player.hasChangedDimension();player.setItemInHand(InteractionHand.MAIN_HAND,shell);
            player.gameMode.useItemOn(player,h.getLevel(),shell,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(p.below()),Direction.UP,p.below(),false));
            h.assertTrue(h.getLevel().getBlockState(p).is(RealmEcology.SPORE_POD.get())&&h.getLevel().getBlockState(p).getValue(SporePodBlock.PHASE)==SporePodBlock.Phase.SPENT&&player.getMainHandItem().isEmpty(),"Ordinary shell placement is armed or duplicates its source");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(pro.erez.interstice.agriculture.RealmAgriculture.PASTE.get(),2));use(h,p,player);
            h.assertTrue(h.getLevel().getBlockState(p).getValue(SporePodBlock.PHASE)==SporePodBlock.Phase.ARMED&&player.getMainHandItem().getCount()==1,"Explicit native biopaste arming skipped resource cost");step(h,p,player);
        });
        h.runAtTickTime(12,()->{h.assertTrue(gas(h,p).isEmpty(),"Previous queued fuse prematurely fired a newly armed mine");});
        h.runAtTickTime(23,()->{try{h.assertTrue(gas(h,p).size()==1&&h.getLevel().getBlockState(p).getValue(SporePodBlock.PHASE)==SporePodBlock.Phase.SPENT,"New warning did not finish one real gas cycle");h.succeed();}finally{TestPlayers.remove(player);}});
    }
}
