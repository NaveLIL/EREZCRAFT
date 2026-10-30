package pro.erez.interstice.vehicle;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

/** Immutable source geometry and bounded lazy collision caches. */
public final class EitanData {
    public record Collision(int index,List<List<double[]>> states){}
    public record Cell(int id,BlockPos pos,List<double[]> boxes,List<Collision> groups,int light){}
    public record Mount(int slot,String bone,BlockPos pos,double[] hub,int axle,int side){}
    public record Seat(int index,String id,String role,double x,double y,double z,boolean inward){}
    public record Group(String name,int index,int poseCount,int sweepCount,AABB sweep){}
    private static final JsonObject SOURCE=source();
    public static final double[] STEERING_PIVOT=vector(SOURCE.getAsJsonArray("steering_pivot"));
    public static final int STEPS=SOURCE.get("steps").getAsInt();
    public static final String SHA256=SOURCE.get("geometry_sha256").getAsString();
    public static final List<Cell> CELLS=readCells();
    public static final Map<BlockPos,Cell> BY_POS=map();
    public static final BlockPos CONTROL=pos(SOURCE.getAsJsonArray("controller"));
    public static final List<Mount> MOUNTS=readMounts();
    public static final List<Seat> SEATS=readSeats();
    public static final List<Group> GROUPS=readGroups();
    public static final List<String> CHANNELS=List.of("RAMP","RAMP_DOOR","DRIVER_HATCH","COMMANDER_HATCH","TROOP_HATCH_L","TROOP_HATCH_R","TROOP_SEAT_L1","TROOP_SEAT_L2","TROOP_SEAT_L3","TROOP_SEAT_L4","TROOP_SEAT_L5","TROOP_SEAT_R1","TROOP_SEAT_R2","TROOP_SEAT_R3","TROOP_SEAT_R4");
    private static final List<Map<Long,VoxelShape>> CACHE=new ArrayList<>();
    static {if(STEPS!=16||MOUNTS.size()!=8||SEATS.size()!=12)throw new ExceptionInInitializerError("E01 source contract");for(var c:CELLS)CACHE.add(new LinkedHashMap<>(16,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<Long,VoxelShape> e){return size()>128;}});}
    private static JsonObject source(){try(var in=EitanData.class.getResourceAsStream("/data/interstice/eitan/vehicle.json")){if(in==null)throw new IOException("Missing Eitan driving geometry");return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new ExceptionInInitializerError(e);}}
    public static JsonArray metadata(String key){return SOURCE.getAsJsonArray(key).deepCopy();}
    public static BlockPos pos(JsonArray a){return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    public static double[] vector(JsonArray a){return new double[]{a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()};}
    private static List<double[]> boxes(JsonArray a){var out=new ArrayList<double[]>();for(var v:a){var b=new double[6];for(int i=0;i<6;i++){b[i]=v.getAsJsonArray().get(i).getAsDouble();if(!Double.isFinite(b[i])||b[i]<0||b[i]>1)throw new JsonParseException("E01 collider outside cell");}out.add(b);}return List.copyOf(out);}
    private static List<Cell> readCells(){var out=new ArrayList<Cell>();for(var value:SOURCE.getAsJsonArray("cells")){var c=value.getAsJsonObject();var groups=new ArrayList<Collision>();for(var v:c.getAsJsonArray("groups")){var g=v.getAsJsonObject();var states=new ArrayList<List<double[]>>();for(var s:g.getAsJsonArray("states"))states.add(boxes(s.getAsJsonArray()));groups.add(new Collision(g.get("index").getAsInt(),List.copyOf(states)));}int id=c.get("id").getAsInt();if(id!=out.size()||groups.size()>8)throw new JsonParseException("E01 cell contract");out.add(new Cell(id,pos(c.getAsJsonArray("pos")),boxes(c.getAsJsonArray("boxes")),List.copyOf(groups),c.get("light").getAsInt()));}if(out.size()<50||out.size()>500)throw new JsonParseException("E01 cell count");return List.copyOf(out);}
    private static Map<BlockPos,Cell> map(){var out=new HashMap<BlockPos,Cell>();for(var c:CELLS)out.put(c.pos,c);return Map.copyOf(out);}
    private static List<Mount> readMounts(){var out=new ArrayList<Mount>();for(var v:metadata("mounts")){var m=v.getAsJsonObject();out.add(new Mount(m.get("slot").getAsInt(),m.get("bone").getAsString(),pos(m.getAsJsonArray("pos")),vector(m.getAsJsonArray("hub")),m.get("axle").getAsInt(),m.get("side").getAsInt()));}return List.copyOf(out);}
    private static List<Seat> readSeats(){var sorted=new ArrayList<JsonObject>();for(var v:metadata("seats"))sorted.add(v.getAsJsonObject());sorted.sort(Comparator.comparingInt(s->switch(s.get("role").getAsString()){case "driver"->0;case "commander"->1;case "remote_station_operator"->2;default->3;}));var out=new ArrayList<Seat>();for(var s:sorted){var p=s.getAsJsonArray("position_m");out.add(new Seat(out.size(),s.get("id").getAsString(),s.get("role").getAsString(),p.get(0).getAsDouble(),p.get(2).getAsDouble(),p.get(1).getAsDouble(),s.get("facing").getAsString().equals("inward")));}return List.copyOf(out);}
    private static List<Group> readGroups(){var out=new ArrayList<Group>();for(var v:metadata("collision_groups")){var g=v.getAsJsonObject();var a=vector(g.getAsJsonArray("swept_min"));var b=vector(g.getAsJsonArray("swept_max"));out.add(new Group(g.get("name").getAsString(),g.get("index").getAsInt(),g.get("pose_count").getAsInt(),g.get("sweep_count").getAsInt(),new AABB(a[0],a[1],a[2],b[0],b[1],b[2])));}return List.copyOf(out);}
    public static synchronized VoxelShape shape(Cell cell,int[] states){long key=0;for(var g:cell.groups)key=(key<<7)|states[g.index];var cache=CACHE.get(cell.id);var old=cache.get(key);if(old!=null)return old;var pieces=new ArrayList<VoxelShape>();add(pieces,cell.boxes);for(var g:cell.groups)add(pieces,g.states.get(states[g.index]));var result=pieces.isEmpty()?Shapes.empty():Shapes.or(pieces.getFirst(),pieces.subList(1,pieces.size()).toArray(VoxelShape[]::new)).optimize();cache.put(key,result);return result;}
    private static void add(List<VoxelShape> shapes,List<double[]> boxes){for(var b:boxes)shapes.add(Shapes.box(b[0],b[1],b[2],b[3],b[4],b[5]));}
    private EitanData(){}
}
