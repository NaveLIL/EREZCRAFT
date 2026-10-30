package pro.erez.interstice.client;

import com.google.gson.*;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import pro.erez.interstice.ship.*;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Same-pose opaque-atlas control versus normal glass, full Sodium/Sable/DH renderer. */
final class SkatGlassNative {
    private static final String[] NAMES={"port","front","starboard"};
    private static final double[][] VIEWS={{-1.5,4.2,-4.4,-18,0,-4.4},{-2,4.2,-4.3,-2,0,-25},{1.5,4.2,-4.4,20,0,-4.4}};
    private static final JsonObject REPORT=new JsonObject();
    private static final JsonArray CASES=new JsonArray();
    private static ServerLevel world;private static ServerSubLevel ship;private static BlockPos origin;
    private static int stage,ticks,index;private static long drawsBefore;private static JsonObject oldPixels;private static int[] controlPixels;
    private static CompletableFuture<?> work;
    static JsonObject report(){var r=REPORT.deepCopy();r.add("cases",CASES.deepCopy());r.addProperty("stage",stage);r.addProperty("view",index);return r;}
    private static void require(boolean b,String message){if(!b)throw new IllegalStateException(message);}
    private static ServerPlayer player(){return world.getServer().getPlayerList().getPlayers().getFirst();}
    private static Vector3d global(double x,double y,double z){return ship.logicalPose().transformPosition(new Vector3d(origin.getX()+x,origin.getY()+y,origin.getZ()+z),new Vector3d());}
    private static void camera(){
        var a=VIEWS[index];var p=global(a[0],a[1],a[2]);var target=global(a[3],a[4],a[5]);
        target.y=pro.erez.interstice.ocean.LowerToxinSurface.sample(world,target.x,target.z,0).height();
        double dx=target.x-p.x,dy=target.y-p.y-1.62,dz=target.z-p.z;
        player().teleportTo(world,p.x,p.y,p.z,Set.of(),(float)Math.toDegrees(Math.atan2(-dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));
        player().setDeltaMovement(Vec3.ZERO);
    }
    static void begin(ServerLevel w,ServerSubLevel body,BlockPos o){
        world=w;ship=body;origin=o;
        try{WaveShipFixture.pause(w);}catch(Exception e){throw new IllegalStateException(e);}
        var p=player();p.stopRiding();p.setGameMode(GameType.SPECTATOR);p.getAbilities().flying=true;p.onUpdateAbilities();
        camera();var helm=(SkatHelmEntity)world.getBlockEntity(origin.offset(0,4,-6));
        for(int channel:new int[]{1,2,4}){
            // Begin in this disposable fixture with no cooldown: existing mask is 6.
            if((helm.lighting()&channel)==0)require(helm.setLighting(p,channel,true),"Cannot enable test cabin lighting");
        }
        require(SkatGlassSprite.uploads>0&&SkatGlassSprite.levels>0,"Glass sprite opacity never uploaded");REPORT.addProperty("glass_sprite_uploads",SkatGlassSprite.uploads);REPORT.addProperty("glass_sprite_mip_levels",SkatGlassSprite.levels);REPORT.addProperty("viewer_mode","spectator for a stationary eye; survival tested separately");try{var api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");boolean enabled=(Boolean)api.getMethod("isShaderPackInUse").invoke(api.getMethod("getInstance").invoke(null));REPORT.addProperty("shader_pack_in_use",enabled);var expected=Minecraft.getInstance().gameDirectory.toPath().resolve("skat-expect-shaders");if(java.nio.file.Files.isRegularFile(expected))require(enabled==Boolean.parseBoolean(java.nio.file.Files.readString(expected).trim()),"Actual Iris state differs from requested scenario");}catch(Exception ex){throw new IllegalStateException(ex);}REPORT.addProperty("actual_skat_glass",true);REPORT.addProperty("same_frozen_pose",true);
        REPORT.addProperty("diagnostic_night_vision",true);REPORT.addProperty("near_ocean",true);
    }
    private static void opacity(int value){var atlas=Minecraft.getInstance().getModelManager().getAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);var sprite=atlas.getSprite(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("interstice","block/skat/glass"));for(var image:sprite.contents().byMipLevel)for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)image.setPixelRGBA(x,y,(image.getPixelRGBA(x,y)&0xffffff)|(value<<24));int old=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);try{com.mojang.blaze3d.systems.RenderSystem.bindTexture(atlas.getId());sprite.contents().uploadFirstFrame(sprite.getX(),sprite.getY());}finally{com.mojang.blaze3d.systems.RenderSystem.bindTexture(old);}}
    private static void aim(){var mc=Minecraft.getInstance();var a=VIEWS[index];var target=global(a[3],a[4],a[5]);target.y=pro.erez.interstice.ocean.LowerToxinSurface.sample(world,target.x,target.z,0).height();var eye=mc.gameRenderer.getMainCamera().getPosition();var direction=new Vector3d(target.x-eye.x,target.y-eye.y,target.z-eye.z);var frame=dev.ryanhcode.sable.api.entity.EntitySubLevelUtil.getCustomEntityOrientation(mc.player,1);if(frame!=null)frame.transformInverse(direction);mc.player.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z))));}
    private static JsonObject pixels(){
        var r=new JsonObject();var cam=Minecraft.getInstance().gameRenderer.getMainCamera();var eye=cam.getPosition();var local=ship.logicalPose().transformPositionInverse(new Vector3d(eye.x,eye.y,eye.z),new Vector3d()).sub(origin.getX(),origin.getY(),origin.getZ());require(local.y>4.9&&local.y<6.08,"Diagnostic eye left window band: "+local);r.addProperty("actual_camera_local",local.toString());r.addProperty("actual_world_view",cam.getLookVector().toString());try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){
            long red=0,green=0,blue=0;int sea=0,total=0,restoredChroma=0;if(stage==1)controlPixels=new int[image.getWidth()*image.getHeight()];
            // The bridge console covers the front view's center. Measure the
            // actual window band, retaining its opaque frame/console as controls.
            int x0=image.getWidth()/20,x1=image.getWidth()*19/20,y0=image.getHeight()*15/100,y1=image.getHeight()*65/100;
            for(int x=x0;x<x1;x++)for(int y=y0;y<y1;y++){
                int c=image.getPixelRGBA(x,y),rr=c&255,gg=(c>>>8)&255,bb=(c>>>16)&255;
                if(stage==1)controlPixels[total]=c;else if(stage==2){int old=controlPixels[total],r0=old&255,g0=old>>>8&255,b0=old>>>16&255;int c0=r0-Math.max(g0,b0),c1=rr-Math.max(gg,bb);if(c1-c0>=10&&c1>=-3)restoredChroma++;}red+=rr;green+=gg;blue+=bb;total++;if(rr>gg*1.25+3&&rr>bb*1.1+1)sea++;
            }
            r.addProperty("red",red/(double)total);r.addProperty("green",green/(double)total);r.addProperty("blue",blue/(double)total);
            r.addProperty("restored_toxin_chroma_pixels",restoredChroma);r.addProperty("sea_pixels",sea);r.addProperty("pixels",total);
            r.addProperty("x0",x0);r.addProperty("x1",x1);r.addProperty("y0",y0);r.addProperty("y1",y1);
        }return r;
    }
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"skat-glass-"+name+".png",mc.getMainRenderTarget(),m->{});}
    static boolean tick(){
        if(work!=null&&!work.isDone())return false;if(work!=null){work.join();work=null;}
        var mc=Minecraft.getInstance();ticks++;if(stage<=2&&mc.player!=null)aim();
        if(stage==0){mc.options.hideGui=true;mc.options.fov().set(70);LowerToxinRenderer.VERIFY_LATE_PASS=false;opacity(255);REPORT.addProperty("opaque_sprite_control",true);drawsBefore=LowerToxinRenderer.draws;ticks=0;stage=1;}
        else if(stage==1){if(ticks<30)return false;require(LowerToxinRenderer.draws>drawsBefore,"Near ocean never drew");oldPixels=pixels();shot(NAMES[index]+"-before");opacity(SkatGlassSprite.OPACITY);LowerToxinRenderer.VERIFY_LATE_PASS=false;ticks=0;stage=2;}
        else if(stage==2){if(ticks<30)return false;var corrected=pixels();shot(NAMES[index]+"-after");var c=new JsonObject();c.addProperty("window",NAMES[index]);c.add("before",oldPixels);c.add("after",corrected);CASES.add(c);
            int delta=corrected.get("sea_pixels").getAsInt()-oldPixels.get("sea_pixels").getAsInt();
            int restored=corrected.get("restored_toxin_chroma_pixels").getAsInt();c.addProperty("restored_missing_sea",restored>corrected.get("pixels").getAsInt()*.02);
            // Some windows already see the sea with the former pass order.
            // Those views must retain it, while affected views must demonstrate restoration.
            require(delta>=-corrected.get("pixels").getAsInt()*.02,"Sea visibility regressed through "+NAMES[index]+" glass: "+c);
            require(restored>corrected.get("pixels").getAsInt()*.02,"Sea did not reappear versus the opaque control through "+NAMES[index]+": "+c);
            if(++index<VIEWS.length){work=world.getServer().submit(SkatGlassNative::camera);stage=0;ticks=0;}
            else{work=world.getServer().submit(()->player().removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION));stage=3;ticks=0;}
        }else if(stage==3){if(ticks<30)return false;int restored=0;for(var c:CASES)if(c.getAsJsonObject().get("restored_missing_sea").getAsBoolean())restored++;require(restored==NAMES.length,"Not every window restored the ocean");REPORT.addProperty("restored_views",restored);shot("normal-no-night-vision");REPORT.addProperty("normal_capture_no_night_vision",true);REPORT.addProperty("lighting_mask",7);REPORT.addProperty("dh_loaded",net.neoforged.fml.ModList.get().isLoaded("distanthorizons"));REPORT.addProperty("dry_hull_cells",pro.erez.interstice.ocean.ToxinHullOcclusion.clientCells);require(pro.erez.interstice.ocean.ToxinHullOcclusion.clientCells>0,"Hull masking lost");REPORT.addProperty("passed",true);return true;}
        return false;
    }
    private SkatGlassNative(){}
}
