package pro.erez.interstice.ship;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.*;

/** Derived from the immutable authored SK03. Geometry is parsed once, never per frame/tick. */
public final class SkatData {
    public static final String REVISION="SK04";
    public record Cell(int id,BlockPos pos,String role,int cargo,List<double[]> boxes,List<double[]> closed,String opening,boolean lamp,int lightMask) {}
    public record Light(String name,double[] a,double[] b,int channel) {}
    public record Cargo(int index,BlockPos anchor,double x,double y,double z,double halfX,double halfY,double halfZ,boolean deck){}
    public static final List<Cell> CELLS=read();
    public static final List<Cargo> CARGO=readCargo();
    public static final List<Light> LIGHTS=readLights();
    public static final IntegerProperty CELL=IntegerProperty.create("cell",0,CELLS.size()-1);
    private static final VoxelShape[] SHAPES=new VoxelShape[CELLS.size()];
    private static List<Cell> read(){
        try(var stream=SkatData.class.getResourceAsStream("/data/interstice/skat/sk03.json")){
            if(stream==null)throw new IOException("Missing authored SK03 geometry");
            var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            var out=new ArrayList<Cell>();
            for(var value:json.getAsJsonArray("cells")){var c=value.getAsJsonObject();var p=c.getAsJsonArray("pos");var boxes=new ArrayList<double[]>();
                for(var b:c.getAsJsonArray("boxes")){double[] a=new double[6];for(int i=0;i<6;i++)a[i]=b.getAsJsonArray().get(i).getAsDouble();boxes.add(a);}
                var closed=new ArrayList<double[]>();if(c.has("closed_boxes"))for(var b:c.getAsJsonArray("closed_boxes")){double[] a=new double[6];for(int i=0;i<6;i++)a[i]=b.getAsJsonArray().get(i).getAsDouble();closed.add(a);}
                out.add(new Cell(c.get("id").getAsInt(),new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt()),c.get("role").getAsString(),c.get("cargo").getAsInt(),List.copyOf(boxes),List.copyOf(closed),c.has("opening")?c.get("opening").getAsString():"",c.has("lamp")&&c.get("lamp").getAsBoolean(),c.has("light_mask")?c.get("light_mask").getAsInt():0));
            }
            if(out.size()<1400||out.size()>2000||out.stream().filter(c->c.role.equals("cargo")).count()<24||out.stream().filter(c->c.role.equals("cargo")).count()>32)throw new IOException("Unexpected SK03 inventory/layout");
            return List.copyOf(out);
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    public static Cell cell(int index){return CELLS.get(index);}
    private static List<Light> readLights(){
        try(var stream=SkatData.class.getResourceAsStream("/data/interstice/skat/sk03.json")){
            var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();var lights=new ArrayList<Light>();
            for(var value:json.getAsJsonArray("lights")){var v=value.getAsJsonObject();double[] a=new double[3],b=new double[3];for(int i=0;i<3;i++){a[i]=v.getAsJsonArray("a").get(i).getAsDouble();b[i]=v.getAsJsonArray("b").get(i).getAsDouble();}lights.add(new Light(v.get("name").getAsString(),a,b,v.get("channel").getAsInt()));}return List.copyOf(lights);
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    public static Cell at(BlockPos pos){return CELLS.stream().filter(c->c.pos.equals(pos)).findFirst().orElseThrow();}
    private static List<Cargo> readCargo(){
        try(var stream=SkatData.class.getResourceAsStream("/data/interstice/skat/sk03.json")){
            var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();var out=new ArrayList<Cargo>();int i=0;
            for(var c:json.getAsJsonArray("cargo")){var a=c.getAsJsonObject().getAsJsonArray("center");double x=a.get(0).getAsDouble(),y=a.get(1).getAsDouble(),z=a.get(2).getAsDouble();var obj=c.getAsJsonObject();var h=obj.getAsJsonArray("half");out.add(new Cargo(i++,BlockPos.containing(x,y,z),x,y,z,h==null?1.55:h.get(0).getAsDouble(),h==null?.48:h.get(1).getAsDouble(),h==null?.66:h.get(2).getAsDouble(),obj.has("deck")&&obj.get("deck").getAsBoolean()));}return List.copyOf(out);
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    public static Cargo cargoAt(double x,double y,double z){
        return CARGO.stream().filter(c->Math.abs(x-c.x)<=c.halfX&&Math.abs(y-c.y)<=c.halfY&&Math.abs(z-c.z)<=c.halfZ)
            .min(Comparator.comparingDouble(c->Math.abs(y-c.y)+Math.abs(z-c.z))).orElse(null);
    }
    public static VoxelShape shape(int index){
        var cached=SHAPES[index];if(cached!=null)return cached;var result=Shapes.empty();
        for(var b:cell(index).boxes)result=Shapes.or(result,Shapes.box(b[0],b[1],b[2],b[3],b[4],b[5]));
        return SHAPES[index]=result.optimize();
    }
    private SkatData(){}
}
