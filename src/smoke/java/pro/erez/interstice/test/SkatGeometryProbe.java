package pro.erez.interstice.test;
import java.nio.file.*;
import com.google.gson.*;
import net.minecraft.world.phys.AABB;
import pro.erez.interstice.ship.SkatData;

/** Small CPU-only validation, no Minecraft client/server or user world is opened. */
public final class SkatGeometryProbe {
    private static void require(boolean condition,String why){if(!condition)throw new IllegalStateException(why);}
    public static void main(String[] args)throws Exception {
        require(SkatData.CELLS.size()==1509,"Cell count");require(SkatData.CARGO.size()==24,"Cargo count");
        spillChecks();
        require(pro.erez.interstice.ship.SkatData.LIGHTS.size()==37,"Author lamp count");
        require(pro.erez.interstice.ship.SkatLightField.sample(-1.6,3.2,-11,1)>3,"Headlight cone missing ahead");
        require(pro.erez.interstice.ship.SkatLightField.sample(-1.6,4.4,-3,1)==0,"Headlight illuminates backward");
        require(pro.erez.interstice.ship.SkatLightField.sample(-1.6,3.2,-11,0)==0,"Off circuit still emits");
        require(pro.erez.interstice.ship.SkatLightField.sample(1000,0,0,7)==0,"Light field escapes bounds");
        require(pro.erez.interstice.ship.SkatLightField.ATLAS.length==228480,"Unexpected shared light memory");
        require(clear(new AABB(.15,3.55,4.90,.85,4.20,5.60)),"Deck lip blocks headroom while stepping up");
        require(clear(new AABB(.2,4.11,8.8,.8,5.91,9.4)),"Crane obstructs the standing stair entry");
        require(clear(new AABB(-.3,4.45,7.4,1.3,5.65,8.7)),"Parked crane hook intrudes above the upper stairs");
        int faces=0,walkSamples=0,cargoSamples=0;
        int bakedFaces=0;
        for(var c:SkatData.CELLS){var shape=SkatData.shape(c.id());for(var box:shape.toAabbs())require(box.minX>=0&&box.minY>=0&&box.minZ>=0&&box.maxX<=1&&box.maxY<=1&&box.maxZ<=1,"Collision outside cell "+c.id());faces+=shape.toAabbs().size();
            try(var stream=SkatGeometryProbe.class.getResourceAsStream("/assets/interstice/models/block/skat/c"+c.id()+".json")){
                var json=JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for(var value:json.getAsJsonArray("quads")){var points=value.getAsJsonObject().getAsJsonArray("vertices");var vectors=new java.util.ArrayList<org.joml.Vector3f>();for(var p:points){var a=p.getAsJsonArray();vectors.add(new org.joml.Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()));}
                    var normal=new org.joml.Vector3f(vectors.get(1)).sub(vectors.get(0)).cross(new org.joml.Vector3f(vectors.get(2)).sub(vectors.get(0))).normalize();require(normal.isFinite(),"Actual float baking normal invalid "+c.id());bakedFaces++;
                }
            }
        }
        // A vanilla-size player moves throughout the authored 2.5 x 2.06 hold aisle.
        for(double z=-6.0;z<4.9;z+=.2){var player=new AABB(.2,1.65,z-.3,.8,3.45,z+.3);require(clear(player),"Invisible obstruction in hold at "+z);walkSamples++;}
        for(var cargo:SkatData.CARGO){double x=cargo.x()<.5?-0.89:1.89; // authored handles, after the +0.5 X transform
            for(double y:new double[]{cargo.y()-.26,cargo.y()+.26}){var selected=SkatData.cargoAt(x,y,cargo.z());require(selected!=null&&selected.index()==cargo.index(),"Wrong drawer at tier edge "+cargo.index());cargoSamples++;}
        }
        var report=new JsonObject();report.addProperty("passed",true);report.addProperty("cpu_only",true);report.addProperty("cells",SkatData.CELLS.size());report.addProperty("collision_boxes",faces);report.addProperty("baking_normals_checked",bakedFaces);report.addProperty("hold_walk_samples",walkSamples);report.addProperty("cargo_face_samples",cargoSamples);report.addProperty("gameplay_verified",false);
        report.addProperty("light_parts",SkatData.LIGHTS.size());report.addProperty("shared_light_atlas_bytes",pro.erez.interstice.ship.SkatLightField.ATLAS.length);report.addProperty("headlight_direction_and_off_checked",true);
        var path=Path.of(".verification/skat-20261029/java-geometry.json");Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println(report);
    }
    private static boolean clear(AABB player){for(var c:SkatData.CELLS)for(var b:c.boxes())if(player.intersects(new AABB(b[0],b[1],b[2],b[3],b[4],b[5]).move(c.pos())))return false;return true;}
    private static void spillChecks(){
        boolean[] solid=new boolean[5*4*5];for(int y=0;y<3;y++)for(int z=0;z<5;z++)for(int x=0;x<5;x++)if(y==0||x==0||z==0||x==4||z==4)solid[(y*5+z)*5+x]=true;
        int feet=(1*5+2)*5+2;var spill=pro.erez.interstice.ocean.HullSeal.spillLevels(solid,5,4,5);require(spill[feet]==3,"Open hull spill rim incorrect");
        solid[(2*5+2)*5]=false;spill=pro.erez.interstice.ocean.HullSeal.spillLevels(solid,5,4,5);require(spill[feet]==2,"A side breach above the feet did not flood downward");
        solid[(2*5+2)*5]=true;for(int z=0;z<5;z++)for(int x=0;x<5;x++)solid[(3*5+z)*5+x]=true;spill=pro.erez.interstice.ocean.HullSeal.spillLevels(solid,5,4,5);require(spill[feet]==127,"Closed compartment is not sealed");
        solid[2*5+2]=false;spill=pro.erez.interstice.ocean.HullSeal.spillLevels(solid,5,4,5);require(spill[feet]==1,"Floor breach did not flood");
        boolean[] fine=new boolean[12*12*12];for(int z=0;z<3;z++)for(int x=0;x<3;x++)pro.erez.interstice.ocean.HullRaster.fill(fine,12,12,12,x,0,z,4,new AABB(0,.5,0,1,.65,1));
        require(fine[(2*12+6)*12+6]&&!fine[(3*12+6)*12+6],"Thin floor raster ate its walkable air layer");
    }
    private SkatGeometryProbe(){}
}
