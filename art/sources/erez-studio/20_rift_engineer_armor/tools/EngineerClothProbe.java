import java.nio.file.*;
import java.util.*;
import org.joml.*;
import com.google.gson.*;
import pro.erez.interstice.client.EngineerCloth;

/** Dense posed-leg contact probe over the actual authored coat surfaces, not a duplicate solver. */
public final class EngineerClothProbe{
    public static void main(String[] args)throws Exception{
        var d=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();var points=new ArrayList<JsonObject>();
        for(var p:d.getAsJsonArray("parts")){var q=p.getAsJsonObject();String n=q.get("name").getAsString();if(q.get("bone").getAsString().endsWith("_leg") && (n.contains("skirt")||n.contains("_hem")||n.contains("inner_lining")||n.contains("gusset")||n.contains("_front_fold")||n.contains("_back_fold")||n.contains("waist_bridge")))points.add(q);}
        var result=new JsonObject();var modes=new JsonArray();float maximum=0,pinned=0,displacement=0;long checked=0;
        for(String mode:List.of("idle","walk","sprint","crouch","jump","swim","flight","rapid_turn")){
            var cloth=new EngineerCloth.State();float worst=0;var out=new Vector3f();
            for(int tick=0;tick<100;tick++){
                float swing=(float)java.lang.Math.sin(tick*.18),angle=switch(mode){case "walk"->swing*.7F;case "sprint"->swing*1.0F;case "swim"->swing*.45F;case "flight"->-.35F;case "crouch"->swing*.18F;default->0;};
                Matrix4f body=new Matrix4f(),right=new Matrix4f(),left=new Matrix4f();
                if(mode.equals("crouch"))body.translate(0,3.2F,0).rotateX(.5F);
                float z=mode.equals("crouch")?4:0,y=mode.equals("crouch")?12.2F:12;
                right.translate(-1.9F,y,z).rotateX(angle);left.translate(1.9F,y,z).rotateX(-angle);
                cloth.pose(body,right,left);cloth.update(tick,new Vector3f(mode.equals("rapid_turn")?swing*1.6F:0,mode.equals("jump")?swing*.7F:0,mode.equals("sprint")?-.9F:0));
                pinned=java.lang.Math.max(pinned,cloth.pinnedError());displacement=java.lang.Math.max(displacement,cloth.maximumDisplacement);
                for(var p:points){String name=p.get("name").getAsString();boolean r=p.get("bone").getAsString().equals("right_leg");float px=r?-1.9F:1.9F;
                    var verts=p.getAsJsonArray("vertices");
                    for(var face:p.getAsJsonArray("faces")){
                        var ids=face.getAsJsonObject().getAsJsonArray("indices");if(ids.size()!=4)continue;
                        for(int v=0;v<=5;v++)for(int u=0;u<=3;u++){
                            float[] pos={0,0,0};float U=u/3F,V=v/5F;
                            for(int i=0;i<4;i++){float w=switch(i){case 0->(1-U)*(1-V);case 1->U*(1-V);case 2->U*V;default->(1-U)*V;};var a=verts.get(ids.get(i).getAsInt()).getAsJsonArray();for(int k=0;k<3;k++)pos[k]+=a.get(k).getAsFloat()*w;}
                            cloth.deform(name,r?0:1,pos[0]+px,pos[1]+12,pos[2],.5F,out);if(!out.isFinite())throw new AssertionError("Nonfinite coat");
                            worst=java.lang.Math.max(worst,cloth.penetration(out));checked++;
                        }
                    }
                }
            }
            maximum=java.lang.Math.max(maximum,worst);var row=new JsonObject();row.addProperty("pose",mode);row.addProperty("maximum_vertex_penetration_model_units",worst);modes.add(row);
        }
        result.add("poses",modes);result.addProperty("sampled_surface_points",checked);result.addProperty("maximum_vertex_penetration_model_units",maximum);result.addProperty("maximum_waist_pin_error",pinned);result.addProperty("maximum_cage_displacement",displacement);result.addProperty("passed",maximum<.005&&pinned<.00001);
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(result));System.out.println(result);
        if(!result.get("passed").getAsBoolean())throw new AssertionError("Cloth contact probe failed");
    }
}
