package pro.erez.interstice.vehicle;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.*;

/** Authored inspection geometry, loaded only by the explicit preview registry. */
public final class EitanPreviewData {
    public record Cell(int id,BlockPos pos,List<double[]> base,List<List<double[]>> poses,int light){}
    private static final JsonObject SOURCE=source();
    public static final String REVISION=SOURCE.get("revision").getAsString();
    public static final String SHA256=SOURCE.get("geometry_sha256").getAsString();
    public static final List<Cell> CELLS=read();
    public static final IntegerProperty CELL=IntegerProperty.create("cell",0,CELLS.size()-1);
    public static final IntegerProperty POSE=IntegerProperty.create("pose",0,3);
    private static final VoxelShape[][] CACHE=new VoxelShape[CELLS.size()][4];
    private static JsonObject source(){try(var in=EitanPreviewData.class.getResourceAsStream("/data/interstice/eitan/eitan.json")){if(in==null)throw new IOException("Missing E01 geometry");return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new ExceptionInInitializerError(e);}}
    private static List<double[]> boxes(JsonArray a){var out=new ArrayList<double[]>();for(var v:a){double[] b=new double[6];for(int i=0;i<6;i++){b[i]=v.getAsJsonArray().get(i).getAsDouble();if(!Double.isFinite(b[i])||b[i]<0||b[i]>1)throw new JsonParseException("Out-of-cell E01 collider");}if(b[0]>=b[3]||b[1]>=b[4]||b[2]>=b[5])throw new JsonParseException("Empty E01 collider");out.add(b);}return List.copyOf(out);}
    private static List<Cell> read(){var out=new ArrayList<Cell>();for(var value:SOURCE.getAsJsonArray("cells")){var c=value.getAsJsonObject();var p=c.getAsJsonArray("pos");var poses=new ArrayList<List<double[]>>();for(var a:c.getAsJsonArray("pose_boxes"))poses.add(boxes(a.getAsJsonArray()));if(poses.size()!=4)throw new JsonParseException("Expected four E01 inspection poses");int id=c.get("id").getAsInt();if(id!=out.size())throw new JsonParseException("Unordered E01 cells");out.add(new Cell(id,new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt()),boxes(c.getAsJsonArray("boxes")),List.copyOf(poses),c.get("light").getAsInt()));}if(out.size()<40||out.size()>500)throw new JsonParseException("Unexpected E01 size");return List.copyOf(out);}
    public static JsonArray metadata(String key){return SOURCE.getAsJsonArray(key).deepCopy();}
    public static synchronized VoxelShape shape(int id,int pose){if(CACHE[id][pose]!=null)return CACHE[id][pose];var c=CELLS.get(id);var s=new ArrayList<VoxelShape>();for(var boxes:List.of(c.base,c.poses.get(pose)))for(var b:boxes)s.add(Shapes.box(b[0],b[1],b[2],b[3],b[4],b[5]));return CACHE[id][pose]=s.isEmpty()?Shapes.empty():Shapes.or(s.getFirst(),s.subList(1,s.size()).toArray(VoxelShape[]::new)).optimize();}
    private EitanPreviewData(){}
}
