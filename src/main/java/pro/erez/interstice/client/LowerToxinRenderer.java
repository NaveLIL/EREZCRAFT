package pro.erez.interstice.client;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.level.ChunkEvent;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.ocean.LowerToxinSurface;

/** Static per-chunk GPU meshes. Only wave time changes per frame; never rebuild terrain for animation. */
@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT)
public final class LowerToxinRenderer {
    public static volatile ShaderInstance shader;
    public static long builds,draws;
    /** Isolated rendering comparison only; gameplay never changes this switch. */
    public static volatile boolean VERIFY_FLAT;
    /** Isolated A/B capture of the former depth-order bug; false in normal play. */
    public static volatile boolean VERIFY_LATE_PASS;
    private static ClientLevel world;
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){var w=Minecraft.getInstance().level;if(w!=null)pro.erez.interstice.ocean.ToxinHullOcclusion.update(w);OceanEntityLights.update();}
    private static final Map<Long,VertexBuffer> meshes=new HashMap<>();
    private static final Set<Long> dirty=new HashSet<>();
    public static boolean active(){var mc=Minecraft.getInstance();return shader!=null&&mc.level!=null&&LowerToxinSurface.active(mc.level);}
    private static void dirtyTile(int x,int z){long key=ChunkPos.asLong(x,z);if(meshes.containsKey(key))dirty.add(key);}
    public static void invalidate(BlockPos pos){if(world!=null&&Math.abs(pos.getY()-pro.erez.interstice.geometry.GeometryProfiles.get(world).lowerSeaTop())<4)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)dirtyTile((pos.getX()>>4)+x,(pos.getZ()>>4)+z);}
    @SubscribeEvent public static void chunk(ChunkEvent.Load e){if(e.getLevel() instanceof ClientLevel){var c=e.getChunk().getPos();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)dirtyTile(c.x+x,c.z+z);}}
    @SubscribeEvent public static void unload(ChunkEvent.Unload e){if(e.getLevel() instanceof ClientLevel){long k=e.getChunk().getPos().toLong();var b=meshes.remove(k);if(b!=null)RenderSystem.recordRenderCall(b::close);dirty.remove(k);var c=e.getChunk().getPos();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x!=0||z!=0)dirty.add(ChunkPos.asLong(c.x+x,c.z+z));}}
    private static void clear(){for(var v:meshes.values())if(v!=null)v.close();meshes.clear();dirty.clear();LowerToxinShoreMask.release();LowerToxinHullMask.clear();ShipLightUniforms.clear();OceanEntityLights.clear();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){RenderSystem.recordRenderCall(()->{clear();world=null;});}
    private static VertexBuffer build(int cx,int cz){
        LowerToxinShoreMask.tile(Minecraft.getInstance().level,cx,cz);
        var mc=Minecraft.getInstance();var w=mc.level;var builder=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);boolean any=false;
        float base=(float)LowerToxinSurface.base(w);
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){int wx=cx*16+x,wz=cz*16+z;if(!LowerToxinSurface.cell(w,wx,wz))continue;any=true;
            // Dynamic lights must never be baked into a cached static VBO.
            var pos=new BlockPos(wx,(int)base+1,wz);int light=LightTexture.pack(w.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,pos),w.getBrightness(net.minecraft.world.level.LightLayer.SKY,pos));
            for(int[] d:new int[][]{{0,0},{0,1},{1,1},{1,0}}){float mask=(float)LowerToxinSurface.cornerMask(w,wx+d[0],wz+d[1]);builder.addVertex(x+d[0],base,z+d[1]).setColor(1f,1f,1f,mask).setUv(0,0).setLight(light);}
        }
        var data=builder.build();if(!any){if(data!=null)data.close();return null;}var gpu=new VertexBuffer(VertexBuffer.Usage.STATIC);gpu.bind();gpu.upload(data);VertexBuffer.unbind();builds++;return gpu;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        // The opaque sea must write depth before transparent windows blend over it.
        var stage=VERIFY_LATE_PASS?RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS:RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS;
        if(e.getStage()!=stage||!active())return;var mc=Minecraft.getInstance();if(world!=mc.level){clear();world=mc.level;}
        var camera=e.getCamera().getPosition();int cx=(int)Math.floor(camera.x/16),cz=(int)Math.floor(camera.z/16),radius=mc.options.getEffectiveRenderDistance()+1,remaining=4;
        var sprite=mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"block/heavy_oc01"));
        LowerToxinHullMask.bind(shader,camera);
        int oldAtlas=RenderSystem.getShaderTexture(0);RenderSystem.setShaderTexture(0,TextureAtlas.LOCATION_BLOCKS);
        shader.setSampler("Sampler0",mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getId());mc.gameRenderer.lightTexture().turnOnLightLayer();shader.setSampler("Sampler2",RenderSystem.getShaderTexture(2));
        shader.safeGetUniform("ErezWaveScale").set(VERIFY_FLAT?0f:1f);
        shader.safeGetUniform("ErezSprite").set(sprite.getU0(),sprite.getV0(),sprite.getU1(),sprite.getV1());shader.safeGetUniform("ErezTime").set((float)LowerToxinSurface.time(world,e.getPartialTick().getGameTimeDeltaPartialTick(false)));
        RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.disableBlend();RenderSystem.disableCull();
        try {
        // Near tiles first, bounded upload work, and frustum culling for cached meshes.
        for(int ring=0;ring<=radius;ring++)for(int dx=-ring;dx<=ring;dx++)for(int dz=-ring;dz<=ring;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=ring)continue;int x=cx+dx,z=cz+dz;long key=ChunkPos.asLong(x,z);if(!world.hasChunk(x,z))continue;
            if((!meshes.containsKey(key)||dirty.contains(key))&&remaining>0){var old=meshes.put(key,build(x,z));if(old!=null)old.close();dirty.remove(key);remaining--;}
            var mesh=meshes.get(key);if(mesh==null||!e.getFrustum().isVisible(new AABB(x*16,LowerToxinSurface.base(world)-.5,z*16,x*16+16,LowerToxinSurface.base(world)+.5,z*16+16)))continue;
            shader.safeGetUniform("ErezOffset").set((float)(x*16-camera.x),(float)-camera.y,(float)(z*16-camera.z));shader.safeGetUniform("ErezWorld").set(Math.floorMod(x*16,4096)*1f,Math.floorMod(z*16,4096)*1f);
            ShipLightUniforms.bind(shader,camera,x,z);OceanEntityLights.bind(shader,camera,x,z);
            mesh.bind();mesh.drawWithShader(e.getModelViewMatrix(),e.getProjectionMatrix(),shader);draws++;
        }
        } finally {VertexBuffer.unbind();RenderSystem.setShaderTexture(0,oldAtlas);RenderSystem.enableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();mc.gameRenderer.lightTexture().turnOffLightLayer();}
    }
    @EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void shaders(RegisterShadersEvent e)throws java.io.IOException{e.registerShader(new ShaderInstance(e.getResourceProvider(),ResourceLocation.fromNamespaceAndPath(Interstice.ID,"lower_toxin"),DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),s->{shader=s;RenderSystem.recordRenderCall(()->dirty.addAll(meshes.keySet()));});}
    }
    private LowerToxinRenderer(){}
}
