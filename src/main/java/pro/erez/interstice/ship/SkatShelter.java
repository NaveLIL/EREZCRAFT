package pro.erez.interstice.ship;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3d;

/** Physical moving roof, traced in the ship's coordinates; a ship name never grants immunity. */
public final class SkatShelter {
    private static final boolean DEBUG=System.getProperty("interstice.studioSmoke","").equals("skat");
    public static String lastFailure="no ship overlap";
    // The authored planks have 2 cm cosmetic joints. Probe within a 3.5 cm
    // footprint so these joints do not act like an open hatch in the tide rule.
    private static final double[][] JOINT={{0,0},{.035,0},{-.035,0},{0,.035},{0,-.035},{.035,.035},{-.035,.035},{.035,-.035},{-.035,-.035}};
    public static boolean sheltered(Level w,Entity entity){
        var box=entity.getBoundingBox();double margin=Math.min(.08,box.getXsize()/4);
        double[][] points={{box.minX+margin,box.minZ+margin},{box.maxX-margin,box.minZ+margin},{box.minX+margin,box.maxZ-margin},{box.maxX-margin,box.maxZ-margin},{box.getCenter().x,box.getCenter().z}};
        for(var ship:SubLevelContainer.getContainer(w).getAllSubLevels()){
            if(ship.isRemoved())continue;var bounds=ship.boundingBox();if(box.maxX<bounds.minX()||box.minX>bounds.maxX()||box.maxZ<bounds.minZ()||box.minZ>bounds.maxZ()||box.maxY>bounds.maxY()||box.maxY+12<bounds.minY()){if(DEBUG)lastFailure="bounds: entity="+box+" ship="+bounds;continue;}
            boolean covered=true;
            for(var point:points){var a=ship.logicalPose().transformPositionInverse(new Vector3d(point[0],box.maxY+.02,point[1]),new Vector3d());var b=ship.logicalPose().transformPositionInverse(new Vector3d(point[0],box.maxY+12,point[1]),new Vector3d());
                boolean hit=false;
                for(var joint:JOINT){var start=new Vec3(a.x+joint[0],a.y,a.z+joint[1]);var end=new Vec3(b.x+joint[0],b.y,b.z+joint[1]);
                    var roof=BlockGetter.traverseBlocks(start,end,entity,(e,p)->{
                        var state=w.getBlockState(p);if(!(state.getBlock() instanceof SkatBlock))return null;
                        return state.getCollisionShape(w,p,CollisionContext.of(e)).clip(start,end,p);
                    },e->null);
                    if(roof!=null){hit=true;break;}
                }
                if(!hit){if(DEBUG)lastFailure="roof ray: "+a+" -> "+b+" entity="+box+" ship="+bounds;covered=false;break;}
            }
            if(covered)return true;
        }return false;
    }
    private SkatShelter(){}
}
