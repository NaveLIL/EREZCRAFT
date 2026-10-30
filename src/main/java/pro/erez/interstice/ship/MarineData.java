package pro.erez.interstice.ship;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.*;

/** Authored NE01 cell solids, cached once for the enabled preview. */
public final class MarineData {
    public record Mechanism(String name,int index,List<double[]> closed,List<double[]> open){}
    public record Cell(int id,BlockPos pos,List<double[]> boxes,List<Mechanism> mechanisms,int light){}
    private static final JsonObject SOURCE=source();
    public static final String REVISION=SOURCE.get("revision").getAsString();
    public static final String GEOMETRY_SHA256=SOURCE.get("geometry_sha256").getAsString();
    public static final List<Cell> CELLS=read();
    public static final IntegerProperty CELL=IntegerProperty.create("cell",0,CELLS.size()-1);
    private static final VoxelShape[][] SHAPES=new VoxelShape[CELLS.size()][];
    private static List<double[]> boxes(JsonArray array){var out=new ArrayList<double[]>();for(var value:array){double[] b=new double[6];for(int i=0;i<6;i++)b[i]=value.getAsJsonArray().get(i).getAsDouble();out.add(b);}return List.copyOf(out);}
    private static JsonObject source(){
        try(var stream=MarineData.class.getResourceAsStream("/data/interstice/ne01/ne01.json")){
            if(stream==null)throw new IOException("Missing authored NE01 cells");
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    public static JsonArray metadata(String name){return SOURCE.getAsJsonArray(name).deepCopy();}
    private static List<Cell> read(){
        try{
            var json=SOURCE;var out=new ArrayList<Cell>();
            for(var value:json.getAsJsonArray("cells")){var c=value.getAsJsonObject();var p=c.getAsJsonArray("pos");var mechanisms=new ArrayList<Mechanism>();
                for(var m:c.getAsJsonArray("mechanisms")){var g=m.getAsJsonObject();mechanisms.add(new Mechanism(g.get("name").getAsString(),g.get("index").getAsInt(),boxes(g.getAsJsonArray("closed")),boxes(g.getAsJsonArray("open"))));}
                out.add(new Cell(c.get("id").getAsInt(),new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt()),boxes(c.getAsJsonArray("boxes")),List.copyOf(mechanisms),c.get("light").getAsInt()));
            }
            if(out.size()<1500||out.size()>2400)throw new IOException("Unexpected NE01 cell count");for(int i=0;i<out.size();i++)if(out.get(i).id()!=i)throw new IOException("Noncanonical NE01 cell IDs");return List.copyOf(out);
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    public static Cell cell(int id){return CELLS.get(id);}
    public static synchronized VoxelShape shape(int id,int mask){
        var c=cell(id);if(SHAPES[id]==null)SHAPES[id]=new VoxelShape[1<<c.mechanisms.size()];mask&=SHAPES[id].length-1;
        if(SHAPES[id][mask]!=null)return SHAPES[id][mask];var parts=new ArrayList<VoxelShape>();
        for(var b:c.boxes)parts.add(Shapes.box(b[0],b[1],b[2],b[3],b[4],b[5]));
        for(int i=0;i<c.mechanisms.size();i++)for(var b:((mask&(1<<i))==0?c.mechanisms.get(i).closed:c.mechanisms.get(i).open))parts.add(Shapes.box(b[0],b[1],b[2],b[3],b[4],b[5]));
        return SHAPES[id][mask]=parts.isEmpty()?Shapes.empty():Shapes.or(parts.getFirst(),parts.subList(1,parts.size()).toArray(VoxelShape[]::new)).optimize();
    }
    private MarineData(){}
}
