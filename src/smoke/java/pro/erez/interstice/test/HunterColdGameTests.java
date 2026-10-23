package pro.erez.interstice.test;

import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.*;
import pro.erez.interstice.fauna.*;

/** Real NBT on disk, two separate game-test JVMs. Not region/world-reload acceptance. */
@GameTestHolder("interstice_hunter_cold")
@PrefixGameTestTemplate(false)
public final class HunterColdGameTests {
    @GameTest(template="empty",timeoutTicks=40)
    public static void spiderNbtCrossesJvmWithWoundsAndFiniteBurstButNoTarget(GameTestHelper h) throws Exception {
        Path root=Path.of("").toAbsolutePath().normalize();
        h.assertTrue(root.toString().replace('\\','/').contains("/.verification/"),"Non-verification profile");
        String mode=System.getProperty("interstice.hunterCold");Path nbt=root.resolve("spider-state.nbt");
        var e=pro.erez.interstice.Interstice.CAVE_RIFT_SPIDER.get().create(h.getLevel());
        var result=new JsonObject();long pid=ProcessHandle.current().pid();result.addProperty("pid",pid);
        if("create".equals(mode)){
            h.assertTrue(!Files.exists(nbt),"Refusing to overwrite earlier spider NBT evidence");
            e.setVariant(pro.erez.interstice.entity.CaveRiftSpiderEntity.Variant.LURKER);
            e.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5,2,2.5)));
            var player=TestPlayers.create(h,new BlockPos(4,2,2),GameType.SURVIVAL);
            e.setTarget(player);e.triggerAmbushBurst();e.setHealth(7);
            h.assertTrue(e.getTarget()==player&&pro.erez.interstice.entity.SpiderBrain.observed(e)!=null,"No spider combat memory to save");
            var tag=e.saveWithoutId(new CompoundTag());tag.putInt("BurstTicks",37);NbtIo.writeCompressed(tag,nbt);TestPlayers.remove(player);
            result.addProperty("source_had_combat_memory",true);
        }else{
            h.assertTrue("reload".equals(mode),"Unknown cold mode");
            var source=JsonParser.parseString(Files.readString(root.resolve("spider-create.json"))).getAsJsonObject();
            h.assertTrue(source.get("pid").getAsLong()!=pid,"Spider reload reused creator JVM");
            e.load(NbtIo.readCompressed(nbt,NbtAccounter.unlimitedHeap()));
            h.assertTrue(e.getBrain() instanceof net.tslat.smartbrainlib.api.core.SmartBrain&&e.getHealth()==7&&e.getVariant()==pro.erez.interstice.entity.CaveRiftSpiderEntity.Variant.LURKER&&Math.abs(e.getBbHeight()-1.7)<.001,"Cold spider lost brain/variant/body/wounds");
            h.assertTrue(e.isBursting()&&e.saveWithoutId(new CompoundTag()).getInt("BurstTicks")==37,"Saved finite burst restarted or disappeared");
            h.assertTrue(e.getTarget()==null&&pro.erez.interstice.entity.SpiderBrain.observed(e)==null,"Cold spider retained hidden identity/observation");
            result.addProperty("creator_pid",source.get("pid").getAsLong());result.addProperty("wounds_variant_body_and_remaining_burst_preserved",true);result.addProperty("combat_memory_cleared",true);
        }
        result.addProperty("passed",true);Files.writeString(root.resolve("spider-"+mode+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void entityNbtCrossesJvmWithoutCombatMemory(GameTestHelper h) throws Exception {
        Path root=Path.of("").toAbsolutePath().normalize();
        h.assertTrue(root.toString().replace('\\','/').contains("/.verification/"),"Refusing non-verification profile");
        String mode=System.getProperty("interstice.hunterCold");Path nbt=root.resolve("hunter-state.nbt");
        var result=new JsonObject();long pid=ProcessHandle.current().pid();result.addProperty("pid",pid);
        var hunter=StrongRealmFauna.VAULT_HUNTER.get().create(h.getLevel());
        if("create".equals(mode)){
            h.assertTrue(!Files.exists(nbt),"Refusing to overwrite earlier NBT evidence");
            hunter.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5,2,2.5)));
            var player=TestPlayers.create(h,new BlockPos(4,2,2),GameType.SURVIVAL);
            hunter.setTarget(player);hunter.setHealth(7);
            h.assertTrue(hunter.lastKnownPosition()!=null&&hunter.getTarget()==player,"No real combat memory to serialize");
            CompoundTag tag=hunter.saveWithoutId(new CompoundTag());
            NbtIo.writeCompressed(tag,nbt);TestPlayers.remove(player);
            result.addProperty("source_had_combat_memory",true);result.addProperty("source_health",hunter.getHealth());
        }else{
            h.assertTrue("reload".equals(mode),"Unknown cold test mode");
            var source=JsonParser.parseString(Files.readString(root.resolve("hunter-create.json"))).getAsJsonObject();
            h.assertTrue(source.get("pid").getAsLong()!=pid,"Reload reused the source JVM");
            hunter.load(NbtIo.readCompressed(nbt,NbtAccounter.unlimitedHeap()));
            h.assertTrue(hunter.getBrain() instanceof net.tslat.smartbrainlib.api.core.SmartBrain,"Reload lost SmartBrain");
            h.assertTrue(hunter.getHealth()==7&&hunter.phase()==VaultHunter.Phase.IDLE&&hunter.getTarget()==null&&hunter.lastKnownPosition()==null,"Cold JVM healed or retained stale identity/position");
            result.addProperty("creator_pid",source.get("pid").getAsLong());result.addProperty("wounds_preserved",true);result.addProperty("combat_memory_cleared",true);
        }
        result.addProperty("passed",true);Files.writeString(root.resolve("hunter-"+mode+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));h.succeed();
    }
}
