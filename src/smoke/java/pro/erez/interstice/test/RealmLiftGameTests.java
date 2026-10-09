package pro.erez.interstice.test;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.agriculture.RealmAgriculture;
import pro.erez.interstice.lift.*;
import pro.erez.interstice.minerals.MineralEcology;

@GameTestHolder("interstice_lift")
@PrefixGameTestTemplate(false)
public final class RealmLiftGameTests {
    private record Fixture(ServerPlayer owner, BlockPos pos, FieldAnchorEntity anchor, FieldLiftEntity lift) {}
    private static Fixture fixture(GameTestHelper h) {
        BlockPos approximate = h.absolutePos(new BlockPos(6, 2, 6));
        BlockPos pos = new BlockPos((approximate.getX() & ~15) + 7, approximate.getY(), (approximate.getZ() & ~15) + 7);
        h.getLevel().getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 1; dy <= 52; dy++)
            h.getLevel().setBlock(pos.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
        h.getLevel().setBlock(pos, RealmLift.ANCHOR.get().defaultBlockState(), 3);
        var owner = TestPlayers.create(h, new BlockPos(6, 2, 6), GameType.SURVIVAL);
        owner.teleportTo(h.getLevel(), pos.getX() + 2.5, pos.getY() + 1, pos.getZ() + .5, java.util.Set.of(), 0, 0); owner.hasChangedDimension();
        var anchor = (FieldAnchorEntity)h.getLevel().getBlockEntity(pos); anchor.setOwner(owner.getUUID());
        h.assertTrue(anchor.deploy(owner), "A native owned field anchor must deploy one real vehicle");
        return new Fixture(owner, pos, anchor, anchor.lift());
    }
    private static void cleanup(GameTestHelper h, Fixture fixture) {
        fixture.owner.stopRiding(); fixture.owner.closeContainer();
        var live = fixture.anchor.lift(); if (live != null) live.cargo().clearContent();
        h.getLevel().setBlock(fixture.pos, Blocks.AIR.defaultBlockState(), 3); TestPlayers.remove(fixture.owner);
    }
    private static int drops(GameTestHelper h, BlockPos pos, Item item) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4, 54, 4)).stream()
                .filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeBoardingRetainsWorldOwnedCargoAndNeverSavesRootVehicle(GameTestHelper h) {
        var fixture = fixture(h);
        try {
            var lift = fixture.lift;
            h.assertTrue(lift.interact(fixture.owner, InteractionHand.MAIN_HAND).consumesAction() && fixture.owner.getVehicle() == lift,
                    "RMB must board a native passenger vehicle");
            lift.positionRider(fixture.owner);
            h.assertTrue(fixture.owner.isPassenger() && lift.getPassengers().contains(fixture.owner) && lift.getControllingPassenger() == null,
                    "Passenger state must stay native while the client has no vehicle-driving authority");
            h.assertTrue(lift.shouldBeSaved() && !fixture.owner.saveWithoutId(new CompoundTag()).contains("RootVehicle"),
                    "A boarded anchored lift must save once in the world, never as a second inventory copy in playerdata");
            h.assertTrue(!lift.canUsePortal(true) && !lift.canChangeDimensions(h.getLevel(), h.getLevel()), "Anchored transport cannot migrate itself through a portal");
        } finally { cleanup(h, fixture); }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void loadedMotionMovesPassengerVerticallyAndPaysOnlyActualMovement(GameTestHelper h) {
        var fixture = fixture(h); fixture.anchor.addFuel(100);
        fixture.owner.startRiding(fixture.lift); fixture.lift.positionRider(fixture.owner);
        double start = fixture.lift.getY(); long startedAt = h.getLevel().getGameTime(); int startedEntityTick = fixture.lift.tickCount;
        h.assertTrue(fixture.anchor.control(fixture.owner, FieldAnchorEntity.Action.UP), "Funded native lift must accept ascent");
        h.runAtTickTime(20, () -> {
            try {
                System.out.println("LIFT_NATIVE_MOTION_TRACE startY=" + start + " currentY=" + fixture.lift.getY()
                        + " moved=" + (fixture.lift.getY() - start) + " fuel=" + fixture.anchor.fuel() + " target=" + fixture.anchor.target()
                        + " status=" + fixture.anchor.status() + " commanded=" + fixture.anchor.commanded()
                        + " serverTickDelta=" + (h.getLevel().getGameTime() - startedAt) + " entityTickDelta=" + (fixture.lift.tickCount - startedEntityTick)
                        + " passenger=" + (fixture.owner.getVehicle() == fixture.lift) + " playerY=" + fixture.owner.getY()
                        + " deckBox=" + fixture.lift.getBoundingBox() + " passengerBox=" + fixture.owner.getBoundingBox()
                        + " deckCollision=" + h.getLevel().getBlockCollisions(fixture.lift, fixture.lift.getBoundingBox().move(0, RealmLift.SPEED, 0)).iterator().hasNext()
                        + " passengerCollision=" + h.getLevel().getBlockCollisions(fixture.owner, fixture.owner.getBoundingBox().move(0, RealmLift.SPEED, 0)).iterator().hasNext());
                h.assertTrue(fixture.lift.getY() > start + .5 && fixture.lift.getY() < start + 2,
                        "Twenty real ticks must produce slow physical lift motion, not a teleport");
                h.assertTrue(fixture.owner.getVehicle() == fixture.lift && Math.abs(fixture.owner.getX() - fixture.lift.getX()) < .01,
                        "The actual passenger must remain attached to the moving platform");
                h.assertTrue(fixture.anchor.fuel() > 60 && fixture.anchor.fuel() < 100, "Only loaded moving ticks must debit finite fuel");
                fixture.anchor.stop(FieldAnchorEntity.Status.STOPPED); int fuel = fixture.anchor.fuel(); double stopped = fixture.lift.getY();
                h.runAtTickTime(25, () -> {
                    try { h.assertTrue(fixture.anchor.fuel() == fuel && Math.abs(fixture.lift.getY() - stopped) < .0001, "Stopped transport cannot consume fuel or drift"); }
                    finally { cleanup(h, fixture); }
                    h.succeed();
                });
            } catch (RuntimeException | Error error) { cleanup(h, fixture); throw error; }
        });
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void collisionAndFuelExhaustionStopBeforeHiddenMovementOrOverspending(GameTestHelper h) {
        var fixture = fixture(h);
        try {
            fixture.anchor.addFuel(2); fixture.anchor.control(fixture.owner, FieldAnchorEntity.Action.UP);
            double start = fixture.lift.getY(); fixture.lift.tick(); fixture.lift.tick(); fixture.lift.tick();
            h.assertTrue(fixture.anchor.fuel() == 0 && !fixture.anchor.commanded() && fixture.lift.getY() <= start + RealmLift.SPEED * 2 + .0001,
                    "An exhausted motor may not move beyond its two paid ticks");
            fixture.anchor.addFuel(100);
            fixture.owner.startRiding(fixture.lift); fixture.lift.positionRider(fixture.owner);
            int roof = (int)Math.ceil(fixture.owner.getBoundingBox().maxY);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) h.getLevel().setBlock(new BlockPos(fixture.pos.getX() + dx, roof, fixture.pos.getZ() + dz), Blocks.STONE.defaultBlockState(), 3);
            fixture.anchor.control(fixture.owner, FieldAnchorEntity.Action.UP);
            int fuel = fixture.anchor.fuel(); double y = fixture.lift.getY();
            for (int tick = 0; tick < 30 && fixture.anchor.commanded(); tick++) fixture.lift.tick();
            h.assertTrue(fixture.anchor.status() == FieldAnchorEntity.Status.OBSTRUCTED && fixture.lift.getY() < y + 1,
                    "Passenger headroom must stop a platform before suffocation");
            h.assertTrue(fixture.anchor.fuel() <= fuel && fixture.anchor.fuel() >= fuel - 16, "Only successful short movement before the obstruction may spend fuel");
        } finally { cleanup(h, fixture); }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeCargoMenuMovesRealStacksAndClosesAfterDestruction(GameTestHelper h) {
        var fixture = fixture(h);
        try {
            var tool = new ItemStack(Items.DIAMOND_PICKAXE); tool.setDamageValue(88); tool.set(DataComponents.CUSTOM_NAME, Component.literal("Saved mine tool"));
            var expected = tool.copy(); fixture.lift.cargo().setItem(8, tool); fixture.owner.setShiftKeyDown(true);
            h.assertTrue(fixture.lift.interact(fixture.owner, InteractionHand.MAIN_HAND).consumesAction() && fixture.owner.containerMenu instanceof ChestMenu,
                    "Shift+RMB must open a real nine-slot native cargo menu");
            var menu = (ChestMenu)fixture.owner.containerMenu;
            h.assertTrue(menu.getContainer().getContainerSize() == RealmLift.CARGO_SLOTS && menu.stillValid(fixture.owner), "Cargo slots and ownership must be validated server-side");
            var moved = menu.quickMoveStack(fixture.owner, 8);
            h.assertTrue(moved.is(Items.DIAMOND_PICKAXE) && fixture.lift.cargo().getItem(8).isEmpty()
                    && fixture.owner.getInventory().items.stream().anyMatch(stack -> ItemStack.isSameItemSameComponents(stack, expected)),
                    "Native menu transfer must preserve item components and consume the source once");
            fixture.lift.discard(); h.assertTrue(!menu.stillValid(fixture.owner), "A destroyed platform may not accept stale cargo clicks");
        } finally { cleanup(h, fixture); }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void coldAnchorAndVehicleRestorationPreserveIdentityCargoAndNoRespawnDuringWait(GameTestHelper h) {
        var fixture = fixture(h);
        try {
            fixture.anchor.addFuel(123);
            var item = new ItemStack(Items.IRON_CHESTPLATE); item.setDamageValue(73); item.set(DataComponents.CUSTOM_NAME, Component.literal("Cold cargo"));
            fixture.lift.cargo().setItem(8, item); UUID id = fixture.lift.getUUID(), anchorId = fixture.anchor.anchorId();
            var savedAnchor = fixture.anchor.saveWithFullMetadata(h.getLevel().registryAccess()); var savedVehicle = new CompoundTag();
            h.assertTrue(fixture.lift.save(savedVehicle), "Native vehicle NBT must encode its actual type and sparse cargo");
            fixture.lift.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.assertTrue(drops(h, fixture.pos, Items.IRON_CHESTPLATE) == 0, "Chunk unloading cannot drop cargo");
            var anchor = new FieldAnchorEntity(fixture.pos, h.getLevel().getBlockState(fixture.pos)); anchor.loadWithComponents(savedAnchor, h.getLevel().registryAccess());
            anchor.setLevel(h.getLevel()); h.getLevel().setBlockEntity(anchor);
            for (int attempt = 0; attempt < 12; attempt++) h.assertTrue(!anchor.deploy(fixture.owner), "A missing saved vehicle must wait rather than spawn a duplicate");
            h.assertTrue(anchor.liftId().equals(id) && anchor.anchorId().equals(anchorId) && anchor.fuel() == 123, "Waiting cannot change identity or refill saved fuel");
            var loaded = (FieldLiftEntity)EntityType.loadEntityRecursive(savedVehicle, h.getLevel(), entity -> entity);
            h.assertTrue(loaded != null && h.getLevel().addFreshEntity(loaded), "The same native vehicle UUID must restore after real unloading");
            h.assertTrue(anchor.lift() == loaded && loaded.getUUID().equals(id) && ItemStack.matches(loaded.cargo().getItem(8), item),
                    "Cold reload must restore the only anchored vehicle with exact cargo components");
            loaded.cargo().clearContent(); h.getLevel().setBlock(fixture.pos, Blocks.AIR.defaultBlockState(), 3);
        } finally { TestPlayers.remove(fixture.owner); }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void anchorAndVehicleDestructionReleaseEveryCargoStackExactlyOnce(GameTestHelper h) {
        var fixture = fixture(h);
        try {
            fixture.lift.cargo().setItem(0, new ItemStack(Items.IRON_INGOT, 37));
            fixture.lift.cargo().setItem(8, new ItemStack(Items.DIAMOND, 3));
            h.getLevel().setBlock(fixture.pos, Blocks.AIR.defaultBlockState(), 3);
            fixture.lift.discard(); fixture.lift.remove(Entity.RemovalReason.KILLED);
            h.assertTrue(drops(h, fixture.pos, Items.IRON_INGOT) == 37 && drops(h, fixture.pos, Items.DIAMOND) == 3 && fixture.lift.cargo().isEmpty(),
                    "Anchor removal and repeated destruction callbacks may drop the native cargo only once");
            var data = fixture.lift.saveWithoutId(new CompoundTag());
            var duplicate = RealmLift.LIFT.get().create(h.getLevel()); duplicate.load(data); duplicate.discard();
            h.assertTrue(drops(h, fixture.pos, Items.IRON_INGOT) == 37, "A released or unregistered duplicate decode object cannot renew lost cargo");
        } finally { TestPlayers.remove(fixture.owner); }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void controllerLinksThroughNativeUseAndChecksOwnerDimensionAndReach(GameTestHelper h) {
        var fixture = fixture(h);
        try {
            var controller = new ItemStack(RealmLift.CONTROLLER.get()); fixture.owner.setItemInHand(InteractionHand.MAIN_HAND, controller); fixture.owner.setShiftKeyDown(true);
            var hit = new BlockHitResult(Vec3.atCenterOf(fixture.pos), net.minecraft.core.Direction.UP, fixture.pos, false);
            h.assertTrue(RealmLift.CONTROLLER.get().useOn(new UseOnContext(fixture.owner, InteractionHand.MAIN_HAND, hit)).consumesAction(), "Server item use must link the actual nearby owned anchor");
            fixture.owner.setShiftKeyDown(false); fixture.anchor.addFuel(40);
            h.assertTrue(RealmLift.CONTROLLER.get().use(h.getLevel(), fixture.owner, InteractionHand.MAIN_HAND).getResult().consumesAction() && fixture.anchor.commanded(), "Linked native controller must start bounded ascent");
            fixture.lift.tick();double risen=fixture.lift.getY();
            RealmLift.CONTROLLER.get().use(h.getLevel(), fixture.owner, InteractionHand.MAIN_HAND);
            h.assertTrue(!fixture.anchor.commanded(), "The second native controller use must stop the platform");
            var stopped=fixture.anchor.saveWithFullMetadata(h.getLevel().registryAccess());
            var cold=new FieldAnchorEntity(fixture.pos,fixture.anchor.getBlockState());cold.loadWithComponents(stopped,h.getLevel().registryAccess());
            h.assertTrue(!cold.commanded()&&cold.target()==fixture.anchor.ceiling(),"Cold saved stopped transport must remember its upward endpoint");
            h.assertTrue(RealmLift.CONTROLLER.get().use(h.getLevel(),fixture.owner,InteractionHand.MAIN_HAND).getResult().consumesAction()
                    &&fixture.anchor.commanded()&&fixture.anchor.target()==fixture.anchor.baseY()&&!fixture.owner.isShiftKeyDown(),
                    "The third ordinary RMB must start descent without vanilla sneak dismount");
            fixture.lift.tick();h.assertTrue(fixture.lift.getY()<risen,"The native cyclic command must physically descend the real platform");
            RealmLift.CONTROLLER.get().use(h.getLevel(),fixture.owner,InteractionHand.MAIN_HAND);
            h.assertTrue(!fixture.anchor.commanded()&&fixture.anchor.target()==fixture.anchor.baseY(),"The fourth ordinary RMB must stop and retain the downward endpoint");
            var foreign = TestPlayers.create(h, new BlockPos(6, 2, 6), GameType.SURVIVAL);
            try {
                foreign.setItemInHand(InteractionHand.MAIN_HAND, controller.copy());
                h.assertTrue(!RealmLift.CONTROLLER.get().use(h.getLevel(), foreign, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                        "A valid copied controller link must not confer ownership on a non-rider");
            } finally { TestPlayers.remove(foreign); }
            var correct = controller.get(DataComponents.CUSTOM_DATA).copyTag();
            var wrongDimension = correct.copy(); wrongDimension.putString("LiftDimension", "minecraft:the_nether"); controller.set(DataComponents.CUSTOM_DATA, CustomData.of(wrongDimension));
            h.assertTrue(!RealmLift.CONTROLLER.get().use(h.getLevel(), fixture.owner, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                    "A linked controller cannot reach across dimensions");
            controller.set(DataComponents.CUSTOM_DATA, CustomData.of(correct));
            fixture.owner.teleportTo(h.getLevel(), fixture.pos.getX() + 100.5, fixture.pos.getY() + 1, fixture.pos.getZ() + .5, java.util.Set.of(), 0, 0);
            fixture.owner.hasChangedDimension();
            h.assertTrue(!RealmLift.CONTROLLER.get().use(h.getLevel(), fixture.owner, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                    "Even the owner cannot send remote transport commands outside the bounded range");
            fixture.owner.teleportTo(h.getLevel(), fixture.pos.getX() + 2.5, fixture.pos.getY() + 1, fixture.pos.getZ() + .5, java.util.Set.of(), 0, 0);
            fixture.owner.hasChangedDimension();
            var tag = controller.get(DataComponents.CUSTOM_DATA).copyTag(); tag.putUUID("LiftAnchorId", UUID.randomUUID()); controller.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            h.assertTrue(!RealmLift.CONTROLLER.get().use(h.getLevel(), fixture.owner, InteractionHand.MAIN_HAND).getResult().consumesAction() && !fixture.anchor.commanded(), "A replaced anchor UUID must reject stale links without moving anything");
        } finally { cleanup(h, fixture); }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void logoutKeepsWorldPlatformAndCargoInsteadOfPlayerRootVehicleCopy(GameTestHelper h) {
        var fixture = fixture(h);
        fixture.lift.cargo().setItem(0, new ItemStack(Items.DIAMOND, 2));
        fixture.owner.startRiding(fixture.lift); fixture.lift.positionRider(fixture.owner);
        UUID vehicle = fixture.lift.getUUID(); TestPlayers.remove(fixture.owner);
        h.assertTrue(h.getLevel().getEntity(vehicle) == fixture.lift && fixture.lift.getPassengers().isEmpty()
                && fixture.lift.cargo().getItem(0).getCount() == 2 && fixture.lift.shouldBeSaved(),
                "Native logout must remove only the passenger, retaining one world-owned cargo platform");
        fixture.lift.cargo().clearContent(); h.getLevel().setBlock(fixture.pos, Blocks.AIR.defaultBlockState(), 3); h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void advancedCraftsAndFuelInteractionsUseActualProcessedNativeResources(GameTestHelper h) {
        var input = CraftingInput.of(3, 3, List.of(new ItemStack(RealmAgriculture.MESH.get()), new ItemStack(Interstice.VITRIOLITE.get()), new ItemStack(RealmAgriculture.MESH.get()),
                new ItemStack(RealmAgriculture.PURE_LINING.get()), new ItemStack(Interstice.PRESSURE_COUPLER.get()), new ItemStack(RealmAgriculture.PURE_LINING.get()),
                new ItemStack(pro.erez.interstice.worldgen.GardenMaterials.PALEHEART_PLANKS.get()), new ItemStack(MineralEcology.WORLD_STICK.get()), new ItemStack(pro.erez.interstice.worldgen.GardenMaterials.PALEHEART_PLANKS.get())));
        var result = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow().value().assemble(input, h.getLevel().registryAccess());
        h.assertTrue(result.is(RealmLift.ANCHOR_ITEM.get()), "Field anchors must be a real advanced material recipe");
        var fixture = fixture(h);
        try {
            var fuel = new ItemStack(MineralEcology.UMBRAL_COAL.get(), 2); fixture.owner.setItemInHand(InteractionHand.MAIN_HAND, fuel);
            var hit = new BlockHitResult(Vec3.atCenterOf(fixture.pos), net.minecraft.core.Direction.UP, fixture.pos, false);
            h.assertTrue(fixture.owner.gameMode.useItemOn(fixture.owner, h.getLevel(), fuel, InteractionHand.MAIN_HAND, hit).consumesAction()
                    && fuel.getCount() == 1 && fixture.anchor.fuel() == 3200, "Native refuelling must consume one coal for finite stored motion ticks");
            h.assertTrue(!fixture.anchor.addFuel(RealmLift.MAX_FUEL) && fixture.anchor.ceiling() - fixture.anchor.baseY() <= RealmLift.MAX_TRAVEL,
                    "Capacity and total vertical reach may not be bypassed");
        } finally { cleanup(h, fixture); }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void dryLowV5ShoreCanDeployWhileSeaHeightAndLegacyBandRemainProtected(GameTestHelper h){
        var player=TestPlayers.create(h,new BlockPos(6,2,6),GameType.SURVIVAL);
        var world=h.getLevel().getServer().getLevel(pro.erez.interstice.worldgen.IslandWorld.VANILLA_WORLD);
        var old=h.getLevel().getServer().getLevel(pro.erez.interstice.worldgen.IslandWorld.TALL_WORLD);
        h.assertTrue(world!=null&&old!=null,"Low-shore check requires actual V5 and retained tall worlds");
        BlockPos point=new BlockPos(1607,36,1607),under=point.atY(34),oldPoint=new BlockPos(1607,36,1623);
        try{
            world.getChunk(point.getX()>>4,point.getZ()>>4);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                world.setBlock(point.offset(dx,-1,dz),Interstice.ABYSSAL_TURF.get().defaultBlockState(),3);
                for(int y=36;y<=40;y++)world.setBlock(new BlockPos(point.getX()+dx,y,point.getZ()+dz),Blocks.AIR.defaultBlockState(),3);
            }
            world.setBlock(point,RealmLift.ANCHOR.get().defaultBlockState(),3);
            player.teleportTo(world,point.getX()+2.5,37,point.getZ()+.5,java.util.Set.of(),0,0);player.hasChangedDimension();
            var anchor=(FieldAnchorEntity)world.getBlockEntity(point);anchor.setOwner(player.getUUID());
            h.assertTrue(anchor.deploy(player),"A dry native V5 plain at Y35 must not inherit the old Y41 floating-island floor");
            world.setBlock(point,Blocks.AIR.defaultBlockState(),3);
            world.setBlock(under,RealmLift.ANCHOR.get().defaultBlockState(),3);
            var unsafe=(FieldAnchorEntity)world.getBlockEntity(under);unsafe.setOwner(player.getUUID());
            h.assertTrue(!unsafe.deploy(player),"An anchor at the lower toxic sea height must remain invalid");
            world.setBlock(under,Blocks.AIR.defaultBlockState(),3);world.setBlock(point,RealmLift.ANCHOR.get().defaultBlockState(),3);
            world.setBlock(point.above(),Interstice.HEAVY_BLOCK.get().defaultBlockState(),3);
            var wet=(FieldAnchorEntity)world.getBlockEntity(point);wet.setOwner(player.getUUID());
            h.assertTrue(!wet.deploy(player),"Dry height alone cannot allow a carriage inside an elevated toxic river");
            old.getChunk(oldPoint.getX()>>4,oldPoint.getZ()>>4);old.setBlock(oldPoint,RealmLift.ANCHOR.get().defaultBlockState(),3);
            player.teleportTo(old,oldPoint.getX()+2.5,37,oldPoint.getZ()+.5,java.util.Set.of(),0,0);player.hasChangedDimension();
            var legacy=(FieldAnchorEntity)old.getBlockEntity(oldPoint);legacy.setOwner(player.getUUID());
            h.assertTrue(!legacy.deploy(player),"Revision1 tall islands must retain their accepted minLand band");
        }finally{
            world.setBlock(point,Blocks.AIR.defaultBlockState(),3);world.setBlock(under,Blocks.AIR.defaultBlockState(),3);
            world.setBlock(point.above(),Blocks.AIR.defaultBlockState(),3);old.setBlock(oldPoint,Blocks.AIR.defaultBlockState(),3);TestPlayers.remove(player);
        }h.succeed();
    }
}
