package pro.erez.interstice.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.entity.TidePhantomEntity;
import pro.erez.interstice.fauna.StrongRealmFauna;
import pro.erez.interstice.fauna.TideFauna;
import pro.erez.interstice.geometry.GeometryProfiles;
import pro.erez.interstice.tide.ShelterDetector;
import pro.erez.interstice.tide.TideManager;
import pro.erez.interstice.tide.TidePhase;
import pro.erez.interstice.tide.TideSavedData;
import pro.erez.interstice.worldgen.IslandWorld;

/** Prepared physical fixtures on the actual V6 server. No real client or Survival route is claimed. */
@GameTestHolder("interstice_tide_fauna")
@PrefixGameTestTemplate(false)
public final class TidePhantomGameTests {
    private static final class PhaseScope implements AutoCloseable {
        final ServerLevel storage;
        final CompoundTag saved;
        PhaseScope(GameTestHelper h) {
            var server=h.getLevel().getServer();
            var preferred=server.getLevel(IslandWorld.TALL_WORLD);
            if(preferred==null)preferred=server.getLevel(IslandWorld.WORLD);
            storage=preferred==null?server.overworld():preferred;
            saved=TideManager.getSavedData(server).save(new CompoundTag(),storage.registryAccess());
        }
        void phase(TidePhase phase) { TideManager.getSavedData(storage.getServer()).setPhase(phase,10000); }
        @Override public void close() {
            storage.getDataStorage().set(TideSavedData.FILE_ID,TideSavedData.load(saved,storage.registryAccess(),storage.random));
        }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerLevel world;
        final int x,z;
        final ServerPlayer player;
        final PhaseScope phases;
        final List<TidePhantomEntity> phantoms=new ArrayList<>();
        Fixture(GameTestHelper h) {
            helper=h;world=h.getLevel().getServer().getLevel(IslandWorld.TENSION_WORLD);
            h.assertTrue(world!=null&&StrongRealmFauna.isV6(world),"Fixture must be the actual V6 dimension and generator");
            var local=h.absolutePos(new BlockPos(6,0,6));x=local.getX()+2048;z=local.getZ()+2048;
            int maximum=GeometryProfiles.get(world).maxLand();
            // Explicitly edited, disposable GT scene; natural map geometry is not measured here.
            for(int cx=(x-16)>>4;cx<=(x+16)>>4;cx++)for(int cz=(z-16)>>4;cz<=(z+16)>>4;cz++)world.getChunk(cx,cz);
            for(int dx=-16;dx<=16;dx++)for(int dz=-16;dz<=16;dz++) {
                world.setBlock(new BlockPos(x+dx,99,z+dz),Blocks.STONE.defaultBlockState(),3);
                for(int y=100;y<=maximum;y++)world.setBlock(new BlockPos(x+dx,y,z+dz),Blocks.AIR.defaultBlockState(),3);
            }
            roof(3);phases=new PhaseScope(h);phases.phase(TidePhase.SURGE);
            player=TestPlayers.create(h,new BlockPos(6,2,6),GameType.SURVIVAL);
            player.teleportTo(world,x+.5,100.01,z+.5,Set.of(),0,0);player.hasChangedDimension();player.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(ShelterDetector.isSheltered(world,player),"Prepared awning is not a real shelter");
        }
        void roof(int radius) { for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)world.setBlock(new BlockPos(x+dx,103,z+dz),Blocks.STONE.defaultBlockState(),3); }
        void walls(boolean closed) {
            for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)if(Math.abs(dx)==3||Math.abs(dz)==3)
                for(int y=100;y<=102;y++)world.setBlock(new BlockPos(x+dx,y,z+dz),closed?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
        }
        TidePhantomEntity phantom() { return phantom(world,player.position().add(1.7,1,0)); }
        TidePhantomEntity phantom(ServerLevel level,Vec3 position) {
            var phantom=TideFauna.TIDE_PHANTOM.get().create(level);
            helper.assertTrue(phantom!=null,"Registered tide phantom is absent");
            phantom.moveTo(position);phantom.setPersistenceRequired();
            helper.assertTrue(level.addFreshEntity(phantom),"Registered phantom could not enter the isolated scene");
            phantoms.add(phantom);return phantom;
        }
        @Override public void close() {
            for(var phantom:phantoms)if(!phantom.isRemoved())phantom.discard();
            if(player.server.getPlayerList().getPlayer(player.getUUID())!=null)TestPlayers.remove(player);
            phases.close();
        }
    }
    private static void released(GameTestHelper h,TidePhantomEntity phantom,String reason) {
        h.assertTrue(!phantom.isGrabbing()&&phantom.grabbedPlayerId()==null,reason);
    }
    @GameTest(template="empty",batch="tide_phantom_separation",timeoutTicks=1200)
    public static void coincidentWarningHuntersSeparateInsteadOfFlyingAsOne(GameTestHelper h){
        try(var f=new Fixture(h)){
            f.phases.phase(TidePhase.WARNING);
            Vec3 start=f.player.position().add(8,5,8);
            var first=f.phantom(f.world,start);var second=f.phantom(f.world,start);
            for(int tick=0;tick<15;tick++){
                first.updateHunt();second.updateHunt();
                first.move(MoverType.SELF,first.getDeltaMovement());second.move(MoverType.SELF,second.getDeltaMovement());
            }
            h.assertTrue(first.distanceToSqr(second)>2.25,"Coincident warning phantoms remained clustered");
        }h.succeed();
    }
    @GameTest(template="empty",batch="tide_phantom_scope",timeoutTicks=100)
    public static void naturalSpawningWindowIsOnlyWarningInActualV6(GameTestHelper h) {
        var v6=h.getLevel().getServer().getLevel(IslandWorld.TENSION_WORLD);
        var v5=h.getLevel().getServer().getLevel(IslandWorld.VANILLA_WORLD);
        h.assertTrue(v6!=null&&v5!=null&&StrongRealmFauna.isV6(v6),"Actual V6 and V5 dimensions are required");
        try(var phases=new PhaseScope(h)) {
            for(var phase:TidePhase.values()) {
                phases.phase(phase);
                h.assertTrue(TidePhantomEntity.spawnPhase(phase)==(phase==TidePhase.WARNING),"Phantoms spawn outside the pre-tide warning");
                h.assertTrue(TideFauna.spawningWindow(v6)==(phase==TidePhase.WARNING),"V6 warning spawn window differs from actual saved phase");
                h.assertTrue(!TideFauna.spawningWindow(v5)&&!TideFauna.spawningWindow(h.getLevel()),"Phantoms spawn in V5/Overworld");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="tide_phantom_geometry",timeoutTicks=1200)
    public static void sealedRoomAndBroadRoofRemainSheltersWhileAnOpenAwningHasAPhysicalExit(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            int loaded=f.world.getChunkSource().getLoadedChunksCount();
            var phantom=f.phantom();f.walls(true);
            h.assertTrue(TidePhantomEntity.safeEscape(f.world,f.player)==null&&!phantom.tryBeginGrab(f.player),"Phantom drags a player through a fully sealed room");
            // A tall enclosed hall has physical air for a phantom, but must not
            // receive a natural warning spawn inside its intact high ceiling.
            for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++) {
                f.world.setBlock(new BlockPos(f.x+dx,118,f.z+dz),Blocks.STONE.defaultBlockState(),3);
                if(Math.abs(dx)==3||Math.abs(dz)==3)for(int y=104;y<=117;y++)
                    f.world.setBlock(new BlockPos(f.x+dx,y,f.z+dz),Blocks.STONE.defaultBlockState(),3);
            }
            phantom.setPos(f.player.position().add(0,8,0));
            h.assertTrue(f.world.noCollision(phantom)&&!f.world.containsAnyLiquid(phantom.getBoundingBox()),"Tall enclosed spawn fixture has no actual dry space");
            h.assertTrue(!TideFauna.airborneHabitat(f.world,phantom),"Warning phantom can naturally appear inside a tall sealed hall");
            phantom.setPos(f.player.position().add(8,8,0));
            h.assertTrue(TideFauna.airborneHabitat(f.world,phantom),"Physically open dry air outside the hall was rejected");
            for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++) {
                f.world.setBlock(new BlockPos(f.x+dx,118,f.z+dz),Blocks.AIR.defaultBlockState(),3);
                if(Math.abs(dx)==3||Math.abs(dz)==3)for(int y=104;y<=117;y++)
                    f.world.setBlock(new BlockPos(f.x+dx,y,f.z+dz),Blocks.AIR.defaultBlockState(),3);
            }
            phantom.setPos(f.player.position().add(1.7,1,0));
            f.walls(false);var escape=TidePhantomEntity.safeEscape(f.world,f.player);
            h.assertTrue(escape!=null&&escape.distanceTo(f.player.position())<=12.01,"Open awning has no bounded physical exit");
            h.assertTrue(phantom.tryBeginGrab(f.player),"Reachable sheltered Survival player cannot be grabbed under an open awning");
            phantom.hurt(f.world.damageSources().generic(),1);released(h,phantom,"Damage did not release the awning grab");
            var blocked=f.phantom();h.assertTrue(blocked.tryBeginGrab(f.player),"Dynamic wall fixture could not start a grab");
            var original=f.player.position();
            f.world.setBlock(new BlockPos(f.x+1,100,f.z),Blocks.STONE.defaultBlockState(),3);
            f.world.setBlock(new BlockPos(f.x+1,101,f.z),Blocks.STONE.defaultBlockState(),3);
            blocked.updateHunt();released(h,blocked,"A newly closed physical wall did not interrupt capture");
            h.assertTrue(f.player.position().equals(original),"Closing the route teleported a player through the wall");
            f.world.setBlock(new BlockPos(f.x+1,100,f.z),Blocks.AIR.defaultBlockState(),3);
            f.world.setBlock(new BlockPos(f.x+1,101,f.z),Blocks.AIR.defaultBlockState(),3);
            f.roof(15);
            h.assertTrue(TidePhantomEntity.safeEscape(f.world,f.player)==null,"A broad genuine roof was bypassed by a fabricated distant route");
            h.assertTrue(f.world.getChunkSource().getLoadedChunksCount()==loaded,"Escape search forced new world chunks");
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="tide_phantom_target_priority",timeoutTicks=1200)
    public static void surgeTargetsReachableShelteredPlayerEvenWhenAnExposedPlayerIsCloser(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            ServerPlayer exposed=TestPlayers.create(h,new BlockPos(6,2,6),GameType.SURVIVAL);
            try {
                exposed.teleportTo(f.world,f.x+9.5,100.01,f.z+.5,Set.of(),0,0);exposed.hasChangedDimension();
                h.assertTrue(!ShelterDetector.isSheltered(f.world,exposed)&&ShelterDetector.isSheltered(f.world,f.player),"Target-priority fixture does not distinguish exposed and sheltered players");
                var phantom=f.phantom(f.world,f.player.position().add(8,1,0));
                h.assertTrue(phantom.distanceToSqr(exposed)<phantom.distanceToSqr(f.player),"Exposed decoy is not the closer player");
                // Prime the warning's target cache, then transition immediately:
                // SURGE must reevaluate rather than follow that exposed decoy.
                f.phases.phase(TidePhase.WARNING);phantom.updateHunt();phantom.setDeltaMovement(Vec3.ZERO);
                f.phases.phase(TidePhase.SURGE);phantom.updateHunt();
                h.assertTrue(phantom.getDeltaMovement().x<-.1,"Surge selected the closer exposed decoy instead of the reachable sheltered player");
                for(int tick=0;tick<40&&!phantom.isGrabbing();tick++) {
                    phantom.move(MoverType.SELF,phantom.getDeltaMovement());phantom.updateHunt();
                }
                h.assertTrue(phantom.isGrabbing()&&f.player.getUUID().equals(phantom.grabbedPlayerId()),"Surge pursuit failed to capture the actual sheltered target");
                h.assertTrue(!exposed.getUUID().equals(phantom.grabbedPlayerId()),"Surge captured an exposed decoy");
            } finally { if(exposed.server.getPlayerList().getPlayer(exposed.getUUID())!=null)TestPlayers.remove(exposed); }
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="tide_phantom_geometry",timeoutTicks=1200)
    public static void grabHasOneOwnerUsesBoundedHorizontalVelocityAndReleasesOutsideRoof(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            var first=f.phantom();var second=f.phantom();
            h.assertTrue(first.tryBeginGrab(f.player)&&f.player.getUUID().equals(first.grabbedPlayerId()),"Grab does not bind the actual server player's UUID");
            h.assertTrue(!second.tryBeginGrab(f.player),"Two phantoms own the same player's grab");
            var original=f.player.position();first.updateHunt();
            h.assertTrue(f.player.getVehicle()==first&&f.player.position().distanceTo(original)>.4&&f.player.position().distanceTo(original)<=.851,"Actual passenger was not carried at the bounded faster speed");
            h.assertTrue(!f.player.getAbilities().mayfly,"Capture granted a global flight permission");
            // Remove the actual roof over every occupied footprint cell: exposure must release immediately.
            var box=f.player.getBoundingBox();
            for(int x=(int)Math.floor(box.minX+.05);x<=(int)Math.floor(box.maxX-.05);x++)
                for(int z=(int)Math.floor(box.minZ+.05);z<=(int)Math.floor(box.maxZ-.05);z++)f.world.setBlock(new BlockPos(x,103,z),Blocks.AIR.defaultBlockState(),3);
            h.assertTrue(!ShelterDetector.isSheltered(f.world,f.player),"Roof removal did not expose the player's actual footprint");
            first.updateHunt();released(h,first,"Phantom kept holding an exposed player for the tide");
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="tide_phantom_pit",timeoutTicks=1200)
    public static void passengerIsLiftedFromOneBlockPitAndReleasedIntoOpenAir(GameTestHelper h){
        try(var f=new Fixture(h)){
            f.world.setBlock(new BlockPos(f.x,99,f.z),Blocks.AIR.defaultBlockState(),3);
            f.world.setBlock(new BlockPos(f.x,98,f.z),Blocks.STONE.defaultBlockState(),3);
            f.player.setPos(f.x+.5,99.01,f.z+.5);
            var phantom=f.phantom(f.world,f.player.position().add(0,1,0));
            h.assertTrue(phantom.tryBeginGrab(f.player)&&f.player.getVehicle()==phantom,"One-block pit prevented real capture");
            double height=f.player.getY();
            for(int tick=0;tick<20&&phantom.isGrabbing();tick++)phantom.updateHunt();
            h.assertTrue(f.player.getY()>height+.8&&!ShelterDetector.isSheltered(f.world,f.player),"Passenger was not lifted over the pit lip and carried outside the roof");
            h.assertTrue(!f.player.isPassenger()&&!phantom.isGrabbing(),"Open-air passenger was not released");
        }h.succeed();
    }
    @GameTest(template="empty",batch="tide_phantom_counterplay",timeoutTicks=1200)
    public static void successfulDamageDeathAndTheEndOfSurgeReleaseAndPreventImmediateRegrab(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            var hurt=f.phantom();h.assertTrue(hurt.tryBeginGrab(f.player),"Counterplay fixture could not start a grab");
            h.assertTrue(hurt.hurt(f.world.damageSources().generic(),2),"Fixture damage did not apply");released(h,hurt,"Successful hit did not release");
            h.assertTrue(!hurt.tryBeginGrab(f.player),"Damage allows an immediate regrab without counterplay cooldown");
            var dead=f.phantom();h.assertTrue(dead.tryBeginGrab(f.player),"Death fixture could not start a grab");
            dead.setHealth(0);dead.die(f.world.damageSources().generic());released(h,dead,"Death retained grab state");
            h.assertTrue(!dead.tryBeginGrab(f.player),"Dead phantom acquired a new player");
            var alreadyDead=f.phantom();alreadyDead.setHealth(0);
            h.assertTrue(!alreadyDead.tryBeginGrab(f.player),"A zero-health phantom acquired a first grab");alreadyDead.discard();
            var removed=f.phantom();removed.discard();h.assertTrue(!removed.tryBeginGrab(f.player),"Removed phantom acquired a first grab");
            for(var phase:List.of(TidePhase.EBB,TidePhase.CALM)) {
                f.phases.phase(TidePhase.SURGE);var ending=f.phantom();h.assertTrue(ending.tryBeginGrab(f.player),"Phase fixture could not start a grab");
                f.phases.phase(phase);ending.updateHunt();released(h,ending,"Ended tide retained a grab");
                h.assertTrue(ending.isRemoved(),"Phantom remained active after EBB/CALM");
            }
            f.phases.phase(TidePhase.SURGE);var limited=f.phantom();h.assertTrue(limited.tryBeginGrab(f.player),"Bounded-duration fixture could not start a grab");
            for(int tick=0;tick<61;tick++)limited.updateHunt();released(h,limited,"Capture exceeded sixty update steps");
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="tide_phantom_scope",timeoutTicks=1200)
    public static void grabRequiresSurgeSurvivalReachLineOfSightAndTheSameActualV6(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            var phantom=f.phantom();
            f.phases.phase(TidePhase.WARNING);h.assertTrue(!phantom.tryBeginGrab(f.player),"Warning phase already captures players");
            f.phases.phase(TidePhase.SURGE);
            f.player.setGameMode(GameType.CREATIVE);h.assertTrue(!phantom.tryBeginGrab(f.player),"Creative player was captured");
            f.player.setGameMode(GameType.SPECTATOR);h.assertTrue(!phantom.tryBeginGrab(f.player),"Spectator was captured");
            f.player.setGameMode(GameType.SURVIVAL);
            phantom.setPos(f.player.position().add(8,1,0));h.assertTrue(!phantom.tryBeginGrab(f.player),"Phantom grabbed beyond its physical reach");
            phantom.setPos(f.player.position().add(1.7,1,0));
            f.world.setBlock(new BlockPos(f.x+1,100,f.z),Blocks.STONE.defaultBlockState(),3);
            f.world.setBlock(new BlockPos(f.x+1,101,f.z),Blocks.STONE.defaultBlockState(),3);
            h.assertTrue(!phantom.tryBeginGrab(f.player),"LOS ignores a real wall between phantom and player");
            f.world.setBlock(new BlockPos(f.x+1,100,f.z),Blocks.AIR.defaultBlockState(),3);
            f.world.setBlock(new BlockPos(f.x+1,101,f.z),Blocks.AIR.defaultBlockState(),3);
            h.assertTrue(phantom.tryBeginGrab(f.player),"Removing the actual wall did not permit the reachable grab");
            f.player.setGameMode(GameType.CREATIVE);phantom.updateHunt();released(h,phantom,"Creative transition did not cancel a live grab");
            f.player.setGameMode(GameType.SURVIVAL);phantom.discard();
            var changed=f.phantom();h.assertTrue(changed.tryBeginGrab(f.player),"Dimension transition fixture could not start a grab");
            var v5=f.player.server.getLevel(IslandWorld.VANILLA_WORLD);
            f.player.teleportTo(v5,0.5,100,0.5,Set.of(),0,0);f.player.hasChangedDimension();changed.updateHunt();
            released(h,changed,"Leaving actual V6 did not cancel a grab");
            var wrong=f.phantom(h.getLevel(),h.absoluteVec(new Vec3(6,3,6)));
            h.assertTrue(!wrong.tryBeginGrab(f.player),"An Overworld phantom captured a player in V5");wrong.updateHunt();
            h.assertTrue(wrong.isRemoved(),"Out-of-dimension phantom continued hunting");
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="tide_phantom_persistence",timeoutTicks=1200)
    public static void grabNeverSurvivesNbtAndLogoutOrPlayerDeathCancelsIt(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            var phantom=f.phantom();h.assertTrue(phantom.tryBeginGrab(f.player),"Persistence fixture could not start a grab");
            var saved=phantom.saveWithoutId(new CompoundTag());
            for(var key:saved.getAllKeys())h.assertTrue(!saved.hasUUID(key)||!saved.getUUID(key).equals(f.player.getUUID()),"Saved NBT retained a grabbed player UUID");
            var restored=TideFauna.TIDE_PHANTOM.get().create(f.world);h.assertTrue(restored!=null,"Restored phantom type is absent");
            restored.load(saved);released(h,restored,"Cold NBT load restored a player capture");
            f.player.setHealth(0);phantom.updateHunt();released(h,phantom,"Player death did not release a grab");
            f.player.setHealth(20);phantom.discard();
            var loggedOut=f.phantom();h.assertTrue(loggedOut.tryBeginGrab(f.player),"Logout fixture could not start a grab");
            TestPlayers.remove(f.player);loggedOut.updateHunt();released(h,loggedOut,"Disconnected player remained captured");
        }
        h.succeed();
    }
    @GameTest(template="empty",batch="tide_phantom_geometry",timeoutTicks=1200)
    public static void unloadedEscapeSearchDoesNotCreateChunksAndSmallOpeningsDoNotFitAPlayer(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            f.walls(true);
            // A one-block-high opening cannot pass the real 1.8-block player body.
            f.world.setBlock(new BlockPos(f.x+3,100,f.z),Blocks.AIR.defaultBlockState(),3);
            h.assertTrue(TidePhantomEntity.safeEscape(f.world,f.player)==null,"Player-sized sweep passed a one-block-high opening");
            f.world.setBlock(new BlockPos(f.x+3,101,f.z),Blocks.AIR.defaultBlockState(),3);
            h.assertTrue(TidePhantomEntity.safeEscape(f.world,f.player)!=null,"A genuinely open two-block doorway cannot pass the player");
            f.world.setBlock(new BlockPos(f.x+3,101,f.z),Blocks.STONE_BRICK_STAIRS.defaultBlockState(),3);
            h.assertTrue(TidePhantomEntity.safeEscape(f.world,f.player)==null,"Swept player box ignored the partial stair collision in a low doorway");
            var probe=new ServerPlayer(f.world.getServer(),f.world,new GameProfile(UUID.randomUUID(),"route-probe"),ClientInformation.createDefault());
            probe.setPos(18_000_000.5,100,18_000_000.5);
            h.assertTrue(f.world.getChunkSource().getChunkNow(18_000_000>>4,18_000_000>>4)==null,"Unloaded scene was loaded before escape inspection");
            int loaded=f.world.getChunkSource().getLoadedChunksCount();
            h.assertTrue(TidePhantomEntity.safeEscape(f.world,probe)==null,"Unloaded escape geometry was accepted");
            h.assertTrue(f.world.getChunkSource().getChunkNow(18_000_000>>4,18_000_000>>4)==null,"Escape inspection loaded the far probe chunk");
            h.assertTrue(f.world.getChunkSource().getLoadedChunksCount()==loaded,"Unloaded escape search synchronously generated chunks");
        }
        h.succeed();
    }
}
