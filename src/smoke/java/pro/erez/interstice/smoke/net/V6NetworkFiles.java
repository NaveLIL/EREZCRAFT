package pro.erez.interstice.smoke.net;

import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.core.BlockPos;

/** Common smoke-only file protocol. Atomic writes prevent peers reading a partial stage. */
public final class V6NetworkFiles {
    public static final int PORT=26593;
    public static final Path EVIDENCE=Path.of(System.getProperty("interstice.v6NetworkEvidence","")).toAbsolutePath().normalize();
    public static String name(int id){return id==1?"V6PeerOne":"V6PeerTwo";}
    public static int id(String name){return name.equals("V6PeerOne")?1:name.equals("V6PeerTwo")?2:0;}
    public static void validate(){String configured=System.getProperty("interstice.v6NetworkEvidence","");if(configured.isBlank()||!Path.of(configured).isAbsolute()||!EVIDENCE.toString().replace('\\','/').contains("/.verification/"))throw new IllegalStateException("Network evidence must be explicitly absolute inside .verification");}
    public static void write(String name,JsonObject json)throws Exception{
        validate();Files.createDirectories(EVIDENCE);Path target=EVIDENCE.resolve(name),temporary=EVIDENCE.resolve(name+".tmp");
        Files.writeString(temporary,new GsonBuilder().setPrettyPrinting().create().toJson(json));
        // Windows readers can briefly hold a handle without FILE_SHARE_DELETE. Preserve
        // atomic publication; a transient sharing violation must not disconnect a TCP peer.
        for(int attempt=0;;attempt++)try{
            try{Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unavailable){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
            return;
        }catch(AccessDeniedException busy){if(attempt>=19)throw busy;Thread.sleep(5);}
    }
    public static JsonObject read(String name)throws Exception{return JsonParser.parseString(Files.readString(EVIDENCE.resolve(name))).getAsJsonObject();}
    public static JsonObject point(BlockPos p){var o=new JsonObject();o.addProperty("x",p.getX());o.addProperty("y",p.getY());o.addProperty("z",p.getZ());return o;}
    public static BlockPos point(JsonObject o){return new BlockPos(o.get("x").getAsInt(),o.get("y").getAsInt(),o.get("z").getAsInt());}
    private V6NetworkFiles(){}
}
