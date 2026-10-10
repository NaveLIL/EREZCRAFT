package pro.erez.interstice.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.BitSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import pro.erez.interstice.minerals.MineralEcology;
import pro.erez.interstice.minerals.RiftOreBlock;
import pro.erez.interstice.worldgen.StoneVaults;
import pro.erez.interstice.worldgen.terrain.NativeColumnSamplerV6;
import pro.erez.interstice.worldgen.terrain.V6MouthPlanner;

/** Conservative walking graph on actual FULL blocks. No terrain generation or edits. */
final class V6CaveSurvey {
    private V6CaveSurvey() {}
    record PlannedRoute(JsonObject report,BlockPos camera,BlockPos target) {}
    static JsonObject plannedMouth(V6MouthPlanner.Mouth mouth){
        var report=new JsonObject();report.addProperty("cell_x",mouth.cellX());report.addProperty("cell_z",mouth.cellZ());
        report.add("surface",json(mouth.surface()));report.add("entry",json(mouth.entry()));report.add("interior",json(mouth.interior()));
        report.addProperty("original_internal_no_mouth_nodes",mouth.originalInternalNodes());report.addProperty("connector_steps",mouth.connector().size()-1);
        var route=new JsonArray();for(var p:mouth.route())route.add(json(p));report.add("planned_canonical_route",route);
        report.addProperty("selection_scope","First accepted canonical plan in the fixed world-cell window, chosen before any additional FULL inspection");
        return report;
    }
    /** Validates the preselected route using only already requested actual FULL chunks. */
    static PlannedRoute actualPlannedRoute(ServerLevel level,V6MouthPlanner.Mouth mouth){
        var report=new JsonObject();var nodes=new JsonArray();var blocked=new JsonArray();var fluids=new JsonArray();var hazards=new JsonArray();
        boolean physical=true,airOnly=true;BlockPos previous=null;
        for(var p:mouth.route()){
            requireFull(level,p);var feet=level.getBlockState(p);var head=level.getBlockState(p.above());var floor=level.getBlockState(p.below());
            boolean dry=feet.getFluidState().isEmpty()&&head.getFluidState().isEmpty()&&floor.getFluidState().isEmpty();
            boolean support=!floor.is(Blocks.BEDROCK)&&floor.isFaceSturdy(level,p.below(),Direction.UP);
            boolean clear=passable(level,p)&&passable(level,p.above());boolean step=true;JsonObject extraHead=null;
            if(previous!=null){int horizontal=Math.abs(p.getX()-previous.getX())+Math.abs(p.getZ()-previous.getZ());int dy=p.getY()-previous.getY();
                step=horizontal==1&&Math.abs(dy)<=1;
                if(dy!=0){var extra=(dy>0?previous:p).above(2);requireFull(level,extra);var state=level.getBlockState(extra);boolean extraClear=passable(level,extra),extraDry=state.getFluidState().isEmpty();step&=extraClear&&extraDry;airOnly&=state.isAir();
                    extraHead=json(extra);extraHead.addProperty("role","step_extra_headroom");extraHead.addProperty("block",BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());extraHead.addProperty("dry",extraDry);extraHead.addProperty("collision_clear",extraClear);
                    if(!extraDry)fluids.add(extraHead.deepCopy());if(!extraClear)blocked.add(extraHead.deepCopy());hazard(level,hazards,extra,state,"step_extra_headroom");
                }
            }
            var node=json(p);node.addProperty("floor",BuiltInRegistries.BLOCK.getKey(floor.getBlock()).toString());node.addProperty("feet",BuiltInRegistries.BLOCK.getKey(feet.getBlock()).toString());node.addProperty("head",BuiltInRegistries.BLOCK.getKey(head.getBlock()).toString());
            node.addProperty("dry",dry);node.addProperty("sturdy_floor",support);node.addProperty("collision_clear",clear);node.addProperty("cardinal_step_and_extra_headroom",step);if(extraHead!=null)node.add("step_extra_head_cell",extraHead);nodes.add(node);
            if(!dry)fluids.add(node.deepCopy());if(!support||!clear||!step)blocked.add(node.deepCopy());
            hazard(level,hazards,p,feet,"feet");hazard(level,hazards,p.above(),head,"head");hazard(level,hazards,p.below(),floor,"floor");
            physical&=dry&&support&&clear&&step;airOnly&=feet.isAir()&&head.isAir();previous=p;
        }
        var sampler=NativeColumnSamplerV6.of(level.getChunkSource().randomState());BlockPos camera=null,target=null;int roofHeight=0,view=0;
        if(physical){
            // Preserve selection: never seek a different mouth after decoration blocked it.
            // Choose the longest readable segment of this same verified route, with the
            // deepest node breaking ties. A bend or a single step remains useful; there
            // is no minimum-three-straight requirement in the independent world criterion.
            var route=mouth.route();
            for(int i=route.size()-1;i>=0;i--){var p=route.get(i);
                if(sampler.density(p.getX(),p.getY(),p.getZ(),true)<=0||sampler.density(p.getX(),p.getY(),p.getZ())>0)continue;
                int ceiling=actualRoof(level,p,16);if(ceiling<2)continue;
                for(int sign:new int[]{1,-1}){
                    int first=i+sign;if(first<0||first>=route.size())continue;
                    var delta=route.get(first).subtract(p);BlockPos furthest=null;int run=0;
                    for(int length=1;length<=6;length++){
                        int index=i+sign*length;if(index<0||index>=route.size())break;
                        var next=route.get(index);
                        if(!next.equals(p.offset(delta.getX()*length,delta.getY()*length,delta.getZ()*length))||!cameraVisible(level,p,next))break;
                        furthest=next;run=length;
                    }
                    if(run>view){camera=p;target=furthest.above();roofHeight=ceiling;view=run;}
                }
            }
        }
        report.addProperty("scope","Actual FULL route: dry sturdy floor and two whole-cell collision-free body cells; cardinal steps +/-1. Static geometry inspection, not human traversal or entity-safe Survival.");
        report.addProperty("geometry_edits",0);report.addProperty("physical_collision_route_verified",physical);report.addProperty("legacy_air_only_route_verified",airOnly&&physical);
        report.addProperty("route_nodes",mouth.route().size());report.add("actual_route_nodes",nodes);report.add("blocking_material_cells",blocked);report.add("fluid_cells",fluids);report.add("hazard_cells",hazards);
        report.addProperty("spider_or_gas_entity_safety_claimed",false);report.addProperty("actual_roofed_internal_camera_found",camera!=null);
        report.addProperty("world_cave_route_criterion",physical&&camera!=null);report.addProperty("voluntary_three_straight_view_required",false);
        if(camera!=null){report.add("camera_feet",json(camera));report.add("camera_target",json(target));report.addProperty("camera_actual_roof_height",roofHeight);report.addProperty("camera_forward_readable_cells",view);report.addProperty("camera_eye_y",camera.getY()+1.62);report.addProperty("camera_target_y",target.getY()+.2);report.addProperty("camera_collider_ray_clear",true);}
        return new PlannedRoute(report,camera,target);
    }
    private static boolean loadedFull(ServerLevel level,BlockPos p){var c=level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4);return c!=null&&c.getPersistedStatus().isOrAfter(ChunkStatus.FULL);}
    private static void requireFull(ServerLevel level,BlockPos p){if(!loadedFull(level,p))throw new IllegalStateException("Planned-route inspection would implicitly load an undeclared chunk: "+p);}
    private static boolean passable(ServerLevel level,BlockPos p){return level.getBlockState(p).getCollisionShape(level,p,CollisionContext.empty()).isEmpty();}
    static boolean collisionCameraClear(ServerLevel level,BlockPos p){
        if(!loadedFull(level,p))return false;var floor=level.getBlockState(p.below());
        return !floor.is(Blocks.BEDROCK)&&floor.getFluidState().isEmpty()&&floor.isFaceSturdy(level,p.below(),Direction.UP)
                &&passable(level,p)&&passable(level,p.above())&&level.getBlockState(p).getFluidState().isEmpty()&&level.getBlockState(p.above()).getFluidState().isEmpty();
    }
    private static boolean cameraVisible(ServerLevel level,BlockPos feet,BlockPos targetFeet){
        var from=new Vec3(feet.getX()+.5,feet.getY()+1.62,feet.getZ()+.5);
        var to=new Vec3(targetFeet.getX()+.5,targetFeet.getY()+1.2,targetFeet.getZ()+.5);
        return level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,CollisionContext.empty())).getType()==HitResult.Type.MISS;
    }
    private static int actualRoof(ServerLevel level,BlockPos p,int maximum){for(int dy=2;dy<=maximum;dy++){
        var at=p.above(dy);if(!loadedFull(level,at))return 0;var s=level.getBlockState(at);if(mass(s))return dy;if(!s.getFluidState().isEmpty())return 0;
    }return 0;}
    private static void hazard(ServerLevel level,JsonArray hazards,BlockPos p,BlockState state,String role){
        String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if(state.is(Blocks.COBWEB)||id.contains("cobweb")||id.contains("spider_egg_sac")||id.contains("venom_reed")||id.contains("spore_pod")||id.contains("sting_frond")||id.contains("clingweed")){
            var row=json(p);row.addProperty("role",role);row.addProperty("block",id);row.addProperty("kind",id.contains("cobweb")?"spider_web":id.contains("spider_egg_sac")?"spider_egg_sac":"dangerous_flora");row.addProperty("collision_blocking",!state.getCollisionShape(level,p,CollisionContext.empty()).isEmpty());hazards.add(row);
        }
    }
    static boolean mass(BlockState state){return StoneVaults.isGround(state)||state.getBlock() instanceof RiftOreBlock
        ||state.is(MineralEcology.ROOT_LOAM.get())||state.is(MineralEcology.TOXIC_SAND.get())
        ||state.is(MineralEcology.MINERAL_POWDER.get())||state.is(MineralEcology.MINERAL_FROST.get());}
    static JsonObject survey(ServerLevel level,BlockPos centre,int radius,int minY,int maxY){
        if(radius<1||radius>24||minY>=maxY)throw new IllegalArgumentException("Cave survey bounds");
        int width=radius*2+1,plane=width*width,height=maxY-minY+1,size=plane*height;
        int originX=centre.getX()-radius,originZ=centre.getZ()-radius;
        for(int x=(originX-1)>>4;x<=(originX+width)>>4;x++)for(int z=(originZ-1)>>4;z<=(originZ+width)>>4;z++)
            if(level.getChunkSource().getChunkNow(x,z)==null)throw new IllegalStateException("Cave plot halo is not loaded FULL: "+x+","+z);
        var walk=new BitSet(size);var surface=new BitSet(size);var roofed=new BitSet(size);var tunnels=new BitSet(size);var air=new BitSet(size+plane*2);
        var sampler=NativeColumnSamplerV6.of(level.getChunkSource().randomState());
        int[] highestMass=new int[plane];Arrays.fill(highestMass,minY-1);long actualAir=0;var pos=new BlockPos.MutableBlockPos();
        for(int z=0;z<width;z++)for(int x=0;x<width;x++)for(int y=minY-1;y<=maxY+2;y++){
            var state=level.getBlockState(pos.set(originX+x,y,originZ+z));
            if(mass(state)&&y<=maxY)highestMass[z*width+x]=y;
            if(y>=minY&&state.isAir()){air.set((y-minY)*plane+z*width+x);actualAir++;}
        }
        for(int z=0;z<width;z++)for(int x=0;x<width;x++)for(int y=0;y<height;y++){
            int index=y*plane+z*width+x;if(!air.get(index)||!air.get(index+plane))continue;
            var floor=level.getBlockState(pos.set(originX+x,minY+y-1,originZ+z));
            if(floor.is(Blocks.BEDROCK)||!floor.getFluidState().isEmpty()||!floor.isFaceSturdy(level,pos,Direction.UP))continue;
            walk.set(index);if(highestMass[z*width+x]<minY+y+2&&mass(floor))surface.set(index);
            if(highestMass[z*width+x]>=minY+y+2){roofed.set(index);
                if(sampler.density(originX+x,minY+y,originZ+z,true)>0&&sampler.density(originX+x,minY+y,originZ+z)<=0)tunnels.set(index);
            }
        }
        var seen=new BitSet(size);int[] queue=new int[size];int components=0,accessible=0,enclosed=0,truncated=0,entrances=0;
        int bestStart=-1,bestTunnelCount=0,bestComponentNodes=0,tunnelComponents=0;var componentRows=new JsonArray();
        for(int first=walk.nextSetBit(0);first>=0;first=walk.nextSetBit(first+1)){
            if(seen.get(first))continue;int read=0,write=1,surfaces=0,roofs=0,tunnelCount=0,entryEdges=0,source=-1;boolean boundary=false;queue[0]=first;seen.set(first);
            while(read<write){int node=queue[read++],y=node/plane,z=(node%plane)/width,x=node%width;
                if(surface.get(node)){surfaces++;if(source<0)source=node;}if(roofed.get(node))roofs++;if(tunnels.get(node))tunnelCount++;
                if(x==0||z==0||x==width-1||z==width-1||y==0||y==height-1)boundary=true;
                for(int[] d:DIRECTIONS)for(int dy=-1;dy<=1;dy++){
                    int nx=x+d[0],nz=z+d[1],ny=y+dy;if(nx<0||nz<0||nx>=width||nz>=width||ny<0||ny>=height)continue;
                    int next=ny*plane+nz*width+nx;if(!walk.get(next)||!stepClear(air,node,next,plane,dy))continue;
                    if(surface.get(node)&&roofed.get(next))entryEdges++;
                    if(!seen.get(next)){seen.set(next);queue[write++]=next;}
                }
            }
            components++;boolean reachable=surfaces>0&&roofs>0;
            if(reachable){accessible++;entrances+=entryEdges;if(tunnelCount>0){tunnelComponents++;if(tunnelCount>bestTunnelCount){bestStart=source;bestTunnelCount=tunnelCount;bestComponentNodes=write;}}}
            else if(roofs>0){if(boundary)truncated++;else enclosed++;}
            if(roofs>0){var row=new JsonObject();row.addProperty("nodes",write);row.addProperty("roofed_nodes",roofs);row.addProperty("subtractive_tunnel_nodes",tunnelCount);row.addProperty("open_surface_nodes",surfaces);row.addProperty("surface_to_roof_edges",entryEdges);row.addProperty("has_surface_route",reachable);row.addProperty("has_subtractive_tunnel_route",reachable&&tunnelCount>0);row.addProperty("touches_window_boundary",boundary);componentRows.add(row);}
        }
        var report=new JsonObject();report.addProperty("scope","Actual loaded FULL walking graph; feet/head AIR, dry sturdy floor, conservative horizontal steps +/-1. No outside-window reachability claim.");
        report.addProperty("radius",radius);report.addProperty("min_y",minY);report.addProperty("max_y",maxY);report.addProperty("centre_x",centre.getX());report.addProperty("centre_z",centre.getZ());
        report.addProperty("air_voxels_including_top_halo",actualAir);report.addProperty("walkable_nodes",walk.cardinality());report.addProperty("components",components);
        report.addProperty("accessible_roofed_components",accessible);report.addProperty("surface_entrance_edges",entrances);
        report.addProperty("subtractive_tunnel_nodes",tunnels.cardinality());report.addProperty("accessible_subtractive_tunnel_components",tunnelComponents);
        report.addProperty("enclosed_roofed_components_without_surface_route_in_window",enclosed);report.addProperty("boundary_truncated_roofed_components_without_surface_route",truncated);report.add("roofed_components",componentRows);
        report.addProperty("accessible_cave_found",bestStart>=0);report.addProperty("largest_accessible_component_nodes",bestComponentNodes);
        if(bestStart>=0){
            int[] parent=new int[size],distance=new int[size];Arrays.fill(parent,-2);int read=0,write=1,best=bestStart;queue[0]=bestStart;parent[bestStart]=-1;
            while(read<write){int node=queue[read++],y=node/plane,z=(node%plane)/width,x=node%width;
                if(tunnels.get(node)&&distance[node]>distance[best])best=node;
                for(int[] d:DIRECTIONS)for(int dy=-1;dy<=1;dy++){
                    int nx=x+d[0],nz=z+d[1],ny=y+dy;if(nx<0||nz<0||nx>=width||nz>=width||ny<0||ny>=height)continue;
                    int next=ny*plane+nz*width+nx;if(parent[next]!=-2||!walk.get(next)||!stepClear(air,node,next,plane,dy))continue;
                    parent[next]=node;distance[next]=distance[node]+1;queue[write++]=next;
                }
            }
            int interior=-1,interiorView=0,interiorHeight=0;int[] interiorDirection=null;
            for(int q=0;q<write;q++){
                int node=queue[q];if(!tunnels.get(node))continue;var feet=point(node,plane,width,originX,minY,originZ);int ceiling=ceilingHeight(level,feet);
                if(ceiling<3||ceiling>5)continue;
                for(int[] d:DIRECTIONS){int run=0;
                    for(int length=1;length<=6;length++){
                        int wx=feet.getX()+d[0]*length,wz=feet.getZ()+d[1]*length;
                        if(wx<originX||wx>=originX+width||wz<originZ||wz>=originZ+width)break;
                        int next=(feet.getY()-minY)*plane+(wz-originZ)*width+wx-originX;
                        if(!walk.get(next)||!tunnels.get(next)||sampler.density(wx,feet.getY()+1,wz,true)<=0||sampler.density(wx,feet.getY()+1,wz)>0)break;
                        int headroom=ceilingHeight(level,new BlockPos(wx,feet.getY(),wz));if(headroom<3||headroom>5)break;run=length;
                    }
                    if(run>=3&&(run>interiorView||run==interiorView&&distance[node]>distance[interior])){interior=node;interiorView=run;interiorHeight=ceiling;interiorDirection=d;}
                }
            }
            report.addProperty("camera_internal_tunnel_view_found",interior>=0);if(interior>=0)best=interior;
            var camera=point(best,plane,width,originX,minY,originZ);var entrance=point(bestStart,plane,width,originX,minY,originZ);
            report.add("camera_feet",json(camera));report.add("route_surface_start",json(entrance));report.addProperty("route_steps",distance[best]);
            var route=new JsonArray();int count=0;for(int node=best;node>=0;node=parent[node]){if(count<512)route.add(json(point(node,plane,width,originX,minY,originZ)));count++;}
            report.add("route_camera_to_surface",route);report.addProperty("route_output_truncated",count>512);
            BlockPos target=interior>=0?camera.offset(interiorDirection[0]*interiorView,1,interiorDirection[1]*interiorView):camera.above();
            report.add("camera_target",json(target));report.addProperty("camera_clear_view_blocks",interiorView);report.addProperty("camera_subtractive_standing_run",interiorView);report.addProperty("camera_actual_ceiling_height",interiorHeight);report.addProperty("camera_look_y",camera.getY()+1.62);
        }
        return report;
    }
    private static final int[][] DIRECTIONS={{1,0},{-1,0},{0,1},{0,-1}};
    private static int ceilingHeight(ServerLevel level,BlockPos feet){for(int dy=2;dy<=6;dy++){var state=level.getBlockState(feet.above(dy));if(mass(state))return dy;if(!state.isAir())return 0;}return 0;}
    private static boolean stepClear(BitSet air,int from,int to,int plane,int dy){return dy==0||(dy>0?air.get(from+2*plane):air.get(to+2*plane));}
    private static BlockPos point(int node,int plane,int width,int ox,int oy,int oz){return new BlockPos(ox+node%width,oy+node/plane,oz+(node%plane)/width);}
    static JsonObject json(BlockPos p){var o=new JsonObject();o.addProperty("x",p.getX());o.addProperty("y",p.getY());o.addProperty("z",p.getZ());return o;}
    static BlockPos point(JsonObject o){return new BlockPos(o.get("x").getAsInt(),o.get("y").getAsInt(),o.get("z").getAsInt());}
}
