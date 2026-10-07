package pro.erez.interstice.test;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pro.erez.interstice.FluidContact;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.OceanLiquidBlock;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.geometry.GeometryProfile;
import pro.erez.interstice.geometry.GeometryProfiles;
import pro.erez.interstice.geometry.GeometrySync;
import pro.erez.interstice.worldgen.IslandChunkGenerator;

/** Disposable runtime fixtures, absent from the release JAR. */
@GameTestHolder(Interstice.ID)
@PrefixGameTestTemplate(false)
public final class GeometryGameTests {
    public static final ResourceKey<Level> PROBE=ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(Interstice.ID,"geometry_probe"));
    public static final GeometryProfile SHIFTED=new GeometryProfile(1,0,128,34,102,6);
    private static ServerLevel probe(GameTestHelper h) {
        return java.util.Objects.requireNonNull(h.getLevel().getServer().getLevel(PROBE),"Missing smoke-only geometry dimension");
    }
    private static void rejects(GameTestHelper h,Runnable action,String reason) {
        boolean rejected=false;
        try { action.run(); } catch(IllegalArgumentException | IllegalStateException expected) { rejected=true; }
        h.assertTrue(rejected,reason);
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void legacyGeometryMatchesIndependentGoldenObservations(GameTestHelper h) throws Exception {
        int count=0;
        try(var reader=new BufferedReader(new InputStreamReader(java.util.Objects.requireNonNull(
                GeometryGameTests.class.getResourceAsStream("/interstice/geometry-legacy-golden.txt")),StandardCharsets.UTF_8))) {
            for(String row:reader.lines().toList()) {
                if(row.startsWith("#") || row.isBlank()) continue;
                String[] fields=row.split(" +");
                boolean chaotic=Boolean.parseBoolean(fields[0]);
                double x=Double.parseDouble(fields[1]),z=Double.parseDouble(fields[2]);
                double[] actual={SeaSurface.vertexHeight(GeometryProfile.LEGACY,x,z,chaotic),
                        SeaSurface.heightAt(GeometryProfile.LEGACY,x,z,chaotic),
                        SeaSurface.cellMinimum(GeometryProfile.LEGACY,(int)Math.floor(x),(int)Math.floor(z),chaotic)};
                for(int i=0;i<actual.length;i++) h.assertTrue(Double.doubleToLongBits(actual[i])==
                        Double.doubleToLongBits(Double.parseDouble(fields[i+3])),"Legacy geometry changed: "+row+" field="+i);
                count++;
            }
        }
        h.assertTrue(count==16,"Expected all 16 independent golden observations");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void profileCodecRejectsInvalidOrFutureDefinitions(GameTestHelper h) {
        JsonObject valid=GeometryProfile.CODEC.encodeStart(JsonOps.INSTANCE,SHIFTED).getOrThrow().getAsJsonObject();
        h.assertTrue(GeometryProfile.CODEC.parse(JsonOps.INSTANCE,valid).getOrThrow().equals(SHIFTED),"Profile roundtrip failed");
        String[] keys={"version","min_y","height","upper_reference","lower_sea_top","clearance"};
        int[] invalid={2,1,127,125,90,80};
        for(int i=0;i<keys.length;i++) {
            JsonObject bad=valid.deepCopy();bad.addProperty(keys[i],invalid[i]);
            h.assertTrue(GeometryProfile.CODEC.parse(JsonOps.INSTANCE,bad).error().isPresent(),"Invalid "+keys[i]+" was accepted");
        }
        JsonObject absentVersion=valid.deepCopy();absentVersion.remove("version");
        h.assertTrue(GeometryProfile.CODEC.parse(JsonOps.INSTANCE,absentVersion).error().isPresent(),"Present profiles must have a version");
        rejects(h,()->SHIFTED.checkHeight(LevelHeightAccessor.create(0,256)),"Profile mismatch must fail before rendering/generation");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void generatorCodecPreservesProfileAndExplicitLegacyFallback(GameTestHelper h) {
        var generator=(IslandChunkGenerator)probe(h).getChunkSource().getGenerator();
        var ops=RegistryOps.create(JsonOps.INSTANCE,h.getLevel().registryAccess());
        JsonObject json=IslandChunkGenerator.CODEC.codec().encodeStart(ops,generator).getOrThrow().getAsJsonObject();
        h.assertTrue(json.getAsJsonObject("geometry").get("version").getAsInt()==1,"Saved generator must contain versioned geometry");
        h.assertTrue(IslandChunkGenerator.CODEC.codec().parse(ops,json).getOrThrow().geometry().equals(SHIFTED),"Nondefault geometry lost in codec");
        JsonObject legacy=json.deepCopy();legacy.remove("geometry");
        var restored=IslandChunkGenerator.CODEC.codec().parse(ops,legacy).getOrThrow();
        h.assertTrue(restored.geometry().equals(GeometryProfile.LEGACY),"Old absent field must retain original heights");
        h.assertTrue(IslandChunkGenerator.CODEC.codec().encodeStart(ops,restored).getOrThrow().getAsJsonObject().has("geometry"),"New legacy saves must write an explicit profile");
        JsonObject future=json.deepCopy();future.getAsJsonObject("geometry").addProperty("version",99);
        h.assertTrue(IslandChunkGenerator.CODEC.codec().parse(ops,future).error().isPresent(),"Malformed present field must not silently use legacy");
        JsonObject mismatch=json.deepCopy();mismatch.getAsJsonObject("geometry").addProperty("height",256);
        h.assertTrue(IslandChunkGenerator.CODEC.codec().parse(ops,mismatch).error().isPresent(),"Noise settings/profile height mismatch must be rejected");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void configurationPayloadIsBoundToConnectionAndValidated(GameTestHelper h) {
        FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            Map<ResourceLocation,GeometryProfile> input=new HashMap<>();input.put(PROBE.location(),SHIFTED);
            var payload=new GeometrySync.Profiles(input);input.clear();
            GeometrySync.Profiles.STREAM_CODEC.encode(buffer,payload);
            var decoded=GeometrySync.Profiles.STREAM_CODEC.decode(buffer);
            h.assertTrue(decoded.values().equals(Map.of(PROBE.location(),SHIFTED)),"Wire profile not preserved or mutable");
            Connection first=new Connection(PacketFlow.CLIENTBOUND),second=new Connection(PacketFlow.CLIENTBOUND);
            var height=LevelHeightAccessor.create(0,128);
            rejects(h,()->GeometryProfiles.forClientLevel(first,PROBE,height),"Missing configuration must fail explicitly");
            GeometryProfiles.configure(first,decoded.values());GeometryProfiles.configure(second,Map.of(PROBE.location(),GeometryProfile.LEGACY));
            h.assertTrue(GeometryProfiles.forClientLevel(first,PROBE,height).equals(SHIFTED),"Another connection replaced this profile");
            h.assertTrue(GeometryProfiles.forClientLevel(second,PROBE,height).equals(GeometryProfile.LEGACY),"Connection profiles leaked");
            buffer.clear();buffer.writeVarInt(2);
            for(int i=0;i<2;i++) {buffer.writeResourceLocation(PROBE.location());SHIFTED.write(buffer);}
            rejects(h,()->GeometrySync.Profiles.STREAM_CODEC.decode(buffer),"Duplicate dimension must be rejected");
            buffer.clear();buffer.writeVarInt(1025);
            rejects(h,()->GeometrySync.Profiles.STREAM_CODEC.decode(buffer),"Unbounded profile count must be rejected");
        } finally {buffer.release();}
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void actualWorldViewsBucketContactAndImmersionUseShiftedProfile(GameTestHelper h) {
        ServerLevel world=probe(h);
        double x=7.375,z=9.625,surface=0x1.6a424fd30a82fp6+8;
        BlockPos pos=BlockPos.containing(x,surface,z);
        world.getChunkAt(pos);
        BlockGetter[] views={world,world.getChunkAt(pos),new PathNavigationRegion(world,pos.offset(-1,-1,-1),pos.offset(1,1,1))};
        for(BlockGetter view:views) {
            h.assertTrue(GeometryProfiles.get(view).equals(SHIFTED),"Actual block view lost its world's profile: "+view.getClass());
            h.assertTrue(view.getBlockState(pos).getBlock() instanceof OceanLiquidBlock,"Shifted world did not generate real sea voxels");
            h.assertFalse(FluidContact.pointInLight(view,x,surface-0.02,z),"Eye in dry corner must stay dry: "+view.getClass());
            h.assertTrue(FluidContact.pointInLight(view,x,surface+0.02,z),"Shifted eye immersion missing");
            AABB dry=new AABB(x-.0001,surface-.03,z-.0001,x+.0001,surface-.02,z+.0001);
            AABB wet=dry.move(0,.06,0);
            var fluid=view.getFluidState(pos);
            h.assertTrue(FluidContact.overlap(view,pos,fluid,dry)==0,"Dry footprint received contact");
            h.assertTrue(FluidContact.overlap(view,pos,fluid,wet)>0,"Wet footprint missed contact");
            float expected=(float)(pos.getY()+1-(0x1.69d75422e2fb3p6+8));
            h.assertTrue(Math.abs(fluid.getHeight(view,pos)-expected)<1e-6,"Fluid thickness used a different profile");
            var hit=fluid.getShape(view,pos).clip(new Vec3(x,surface-.5,z),new Vec3(x,surface+.5,z),pos);
            h.assertTrue(hit!=null && Math.abs(hit.getLocation().y-surface)<1e-6,"Bucket ray missed the shifted surface");
        }
        h.assertTrue(GeometryProfiles.get(h.getLevel()).equals(GeometryProfile.LEGACY),"Ordinary world profile was changed");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void shiftedSeaDrivesNativeEntitySwimmingEyesAndToxin(GameTestHelper h) {
        checkNativeEntities(h,probe(h),8);
    }
    @GameTest(template="empty",timeoutTicks=300)
    public static void tallSeaDrivesNativeEntitySwimmingEyesAndToxin(GameTestHelper h) {
        checkNativeEntities(h,java.util.Objects.requireNonNull(h.getLevel().getServer().getLevel(
                pro.erez.interstice.worldgen.IslandWorld.TALL_WORLD)),128);
    }
    private static void checkNativeEntities(GameTestHelper h,ServerLevel world,int offset) {
        double x=7.375,z=9.625,surface=0x1.6a424fd30a82fp6+offset;
        // There are no players/ticking tickets in this additional dimension.
        // Loading a LevelChunk alone does not activate its native entity ticks.
        var forced=new java.util.ArrayList<net.minecraft.world.level.ChunkPos>();
        for(int cx=-1;cx<=1;cx++) for(int cz=-1;cz<=1;cz++) {
            if(world.setChunkForced(cx,cz,true)) forced.add(new net.minecraft.world.level.ChunkPos(cx,cz));
            world.getChunk(cx,cz);
        }
        world.getChunkAt(BlockPos.containing(x,surface,z));
        var wet=java.util.Objects.requireNonNull(EntityType.PIG.create(world));
        var dry=java.util.Objects.requireNonNull(EntityType.PIG.create(world));
        for(var pig:new net.minecraft.world.entity.animal.Pig[]{dry,wet}) {pig.setNoAi(true);pig.setNoGravity(true);}
        wet.moveTo(x,surface+.04-wet.getEyeHeight(),z,0,0);
        dry.moveTo(x,surface-3,z,0,0);
        world.addFreshEntity(wet);world.addFreshEntity(dry);
        h.runAtTickTime(20,()->System.out.println("GEOMETRY_ENTITY_READY wet_ticks="+wet.tickCount
                +" removed="+wet.isRemoved()+" loaded="+world.areEntitiesLoaded(wet.chunkPosition().toLong())
                +" ticking="+world.getChunkSource().isPositionTicking(wet.chunkPosition().toLong())));
        // Full chunks and entity sections finish loading asynchronously. Wait for real native ticks,
        // bounded by the test timeout; never tick entities manually or infer readiness from a ticket.
        h.startSequence().thenWaitUntil(()->h.assertTrue(wet.tickCount>=12 && dry.tickCount>=12,
                "Probe entities did not receive native server ticks: wet="+wet.tickCount+", dry="+dry.tickCount))
                .thenExecute(()->{
            try {
                System.out.println("GEOMETRY_ENTITY_PROBE dimension="+world.dimension().location()+" wet_ticks="+wet.tickCount+" dry_ticks="+dry.tickCount
                        +" wet_health="+wet.getHealth()+" dry_health="+dry.getHealth());
                h.assertTrue(wet.tickCount>=8 && dry.tickCount>=8,"Probe entities did not receive native server ticks");
                h.assertTrue(wet.getHealth()<wet.getMaxHealth(),"Shifted sea did not cause toxin damage");
                h.assertTrue(wet.getEyeInFluidType()==Interstice.LIGHT_TYPE.get(),"Native eyes used wrong surface");
                h.assertTrue(wet.getFluidTypeHeight(Interstice.LIGHT_TYPE.get())>0,"Native swimming used wrong surface");
                h.assertTrue(dry.getHealth()==dry.getMaxHealth() && dry.getFluidTypeHeight(Interstice.LIGHT_TYPE.get())==0,"Dry entity received shifted-sea contact");
            } finally {
                wet.discard();dry.discard();
                for(var chunk:forced) world.setChunkForced(chunk.x,chunk.z,false);
            }
        }).thenSucceed();
    }
}
