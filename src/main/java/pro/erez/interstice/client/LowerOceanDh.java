package pro.erez.interstice.client;

import java.lang.reflect.*;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.opengl.*;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.geometry.GeometryProfiles;
import pro.erez.interstice.worldgen.IslandWorld;

/** OC01 shares Minecraft's live animated atlas; neither source pixels nor terrain are changed. */
public final class LowerOceanDh {
    public static final int TILE=1;
    public static final LongAdder TAGGED=new LongAdder(),UNIFORMS=new LongAdder(),PATCHES=new LongAdder();
    public static final ThreadLocal<Integer> DETAIL=new ThreadLocal<>();
    public static volatile boolean VERIFY_MASK;
    public static final String SHORE="float erezMask(vec2 xz){vec2 delta=mod(xz-uErezCamera+2048.0,4096.0)-2048.0;if(max(abs(delta.x),abs(delta.y))>uErezShoreRadius)return 1.0;ivec2 a=ivec2(mod(floor(xz),1024.0));ivec2 b=(a+ivec2(1,0))%1024;ivec2 c=(a+ivec2(1))%1024;ivec2 d=(a+ivec2(0,1))%1024;float aa=texelFetch(uErezShore,a,0).r,bb=texelFetch(uErezShore,b,0).r,cc=texelFetch(uErezShore,c,0).r,dd=texelFetch(uErezShore,d,0).r;vec2 f=fract(xz);return f.x>=f.y?aa+f.x*(bb-aa)+f.y*(cc-bb):aa+f.y*(dd-aa)+f.x*(cc-dd);}\n";
    public static final String WAVES="float erezWave(vec2 p,float t){return .20*sin(6.28318530718*(p.x/64.0+t/240.0))+.14*sin(6.28318530718*(p.y/128.0-t/384.0))+.08*sin(6.28318530718*((p.x+p.y)/32.0+t/640.0));}\n";
    public static boolean active(){var w=Minecraft.getInstance().level;return w!=null&&w.dimension().equals(IslandWorld.TENSION_WORLD);}
    public static boolean heavy(Object wrapper){
        try{return wrapper.getClass().getMethod("getWrappedMcObject").invoke(wrapper) instanceof BlockState s&&s.is(Interstice.HEAVY_BLOCK.get());}
        catch(ReflectiveOperationException e){throw new IllegalStateException("Pinned DH block wrapper changed",e);}
    }
    public static void detail(Object source){try{DETAIL.set(((Number)source.getClass().getMethod("getDataDetailLevel").invoke(source)).intValue());}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    public static boolean ordinaryTextures(){
        Integer detail=DETAIL.get();if(detail==null)return true;
        try{var c=Class.forName("com.seibel.distanthorizons.core.config.Config$Client$Advanced$Graphics$Texture");Object enabled=c.getField("enableTexturedLods").get(null),max=c.getField("maxTexturedLodDetailLevel").get(null);return (boolean)enabled.getClass().getMethod("get").invoke(enabled)&&detail<=((Number)max.getClass().getMethod("get").invoke(max)).intValue();}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    public static String shader(String path,String source){
        if(source==null||!path.contains("shaders/terrain/gl/"))return source;
        String s=source;
        if(path.endsWith("vert.vert")){
            if(!s.contains("vertexColor *= color;")||!s.contains("vertexYPos = vPosition.y + uWorldYOffset;"))throw new IllegalStateException("Pinned DH vertex shader changed");
            s=s.replace("out vec4 vertexColor;","out vec4 vertexColor;\nout vec3 erezLight;\nout vec3 erezWorld;\nuniform vec2 uErezCamera;");
            s=s.replace("vertexYPos = vPosition.y + uWorldYOffset;","vertexYPos = vPosition.y + uWorldYOffset;\nerezWorld = vec3(vertexWorldPos.x + uErezCamera.x, vertexYPos, vertexWorldPos.z + uErezCamera.y);");
            s=s.replace("if (!uIsWhiteWorld)","erezLight = vertexColor.rgb;\n    if (!uIsWhiteWorld)");
        }else if(path.endsWith("frag.frag")){
            if(!s.contains("fragColor = vertexColor;")||!s.contains("if (vTextureTileId != 0u)"))throw new IllegalStateException("Pinned DH fragment shader changed");
            s=s.replace("in vec4 vertexColor;","in vec4 vertexColor;\nin vec3 erezLight;\nin vec3 erezWorld;\nuniform sampler2D uErezAtlas;\nuniform vec4 uErezSprite;\nuniform float uErezSurface;\nuniform bool uErezEnabled;\nuniform bool uErezMask;\nuniform bool uIsWhiteWorld;\nuniform float uErezTime;\nuniform float uErezWaveScale;\nuniform mat4 uCombinedMatrix;\nuniform sampler2D uErezShore;\nuniform float uErezShoreRadius;\nuniform vec2 uErezCamera;\n"+WAVES+SHORE);
            s=s.replace("fragColor = vertexColor;","""
                fragColor = vertexColor;
                gl_FragDepth = gl_FragCoord.z;
                vec3 erezHit = erezWorld;
                bool erezHeavy = uErezEnabled && !uIsWhiteWorld && vTextureTileId == 1u;
                if (erezHeavy) {
                    if (uErezWaveScale>.5 && vNormalIndex == 1u && abs(erezWorld.y-uErezSurface-1.0/9.0)<.02 && abs(vertexWorldPos.y) > .001) {
                        float cameraY = erezWorld.y-vertexWorldPos.y;
                        float t = (uErezSurface-cameraY)/vertexWorldPos.y;
                        for(int i=0;i<5;i++){
                            vec2 xz=erezWorld.xz+vertexWorldPos.xz*(t-1.0);
                            float target=uErezSurface+erezWave(xz,uErezTime)*erezMask(xz);
                            t=clamp(t+(target-cameraY-vertexWorldPos.y*t)/vertexWorldPos.y,.3,1.7);
                        }
                        erezHit=vec3(erezWorld.xz+vertexWorldPos.xz*(t-1.0),0.0).xzy;
                        erezHit.y=cameraY+vertexWorldPos.y*t;
                        vec4 clip=uCombinedMatrix*vec4(vertexWorldPos*t,1.0);
                        gl_FragDepth=clamp(.5*clip.z/clip.w+.5,0.0,1.0);
                    }
                    float h = erezHit.y - uErezSurface - (vNormalIndex == 1u ? 1.0/9.0 : 0.0);
                    vec2 p = vNormalIndex == 1u ? fract(erezHit.xz/64.0) : fract(vec2(erezHit.x-.5*h,erezHit.z-h)/64.0);
                    vec2 localUv = vec2((.5+p.x)*640.0+.5, (1.5-p.y)*640.0-.5)/1280.0;
                    vec2 uv = mix(uErezSprite.xy,uErezSprite.zw,localUv);
                    float shade = vNormalIndex == 1u ? 1.0 : vNormalIndex == 0u ? .5 : vNormalIndex < 4u ? .8 : .6;
                    fragColor = vec4(texture(uErezAtlas,uv).rgb * erezLight * shade,1.0);
                    if (uErezMask) fragColor = vec4(0.0,1.0,0.0,1.0);
                }
                """);
            s=s.replace("if (vTextureTileId != 0u)","if (vTextureTileId != 0u && !erezHeavy)");
        }
        if(!s.equals(source))PATCHES.increment();return s;
    }
    public record Binding(int[] units,int[] textures){}
    private static int uniform(int id,String name){return GL20.glGetUniformLocation(id,name);}
    public static Binding bind(int id){return bind(id,false);}
    public static Binding bindIris(int id){return bind(id,true);}
    private static Binding bind(int id,boolean iris){
        int enabled=uniform(id,"uErezEnabled");if(enabled<0){if(iris)return null;throw new IllegalStateException("OC01 uniforms missing in DH program "+id);}
        boolean active=active()&&(!iris||V6ShaderBridge.materialsActive());GL20.glUniform1i(enabled,active?1:0);if(!active)return null;
        var mc=Minecraft.getInstance();var sprite=mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.fromNamespaceAndPath(Interstice.ID,"block/heavy_oc01"));
        if(sprite.contents().width()!=1280||sprite.contents().height()!=1280)throw new IllegalStateException("OC01 atlas frame missing");
        int[] units=iris?new int[]{30,31,29}:new int[]{2,3};int[] old=new int[units.length];
        int[] textures=iris?new int[]{mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getId(),LowerToxinShoreMask.texture(),com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(2)}:new int[]{mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getId(),LowerToxinShoreMask.texture()};
        int activeUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);for(int i=0;i<units.length;i++){GL13.glActiveTexture(GL13.GL_TEXTURE0+units[i]);old[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[i]);}GL13.glActiveTexture(activeUnit);
        GL20.glUniform1i(uniform(id,"uErezAtlas"),units[0]);GL20.glUniform1i(uniform(id,"uErezShore"),units[1]);if(iris)GL20.glUniform1i(uniform(id,"uErezLightMap"),units[2]);
        GL20.glUniform1i(uniform(id,"uErezMask"),VERIFY_MASK?1:0);GL20.glUniform1f(uniform(id,"uErezWaveScale"),LowerToxinRenderer.VERIFY_FLAT?0:1);GL20.glUniform1f(uniform(id,"uErezShoreRadius"),Math.min(496,mc.options.getEffectiveRenderDistance()*16+16));
        GL20.glUniform4f(uniform(id,"uErezSprite"),sprite.getU0(),sprite.getV0(),sprite.getU1(),sprite.getV1());
        var camera=mc.gameRenderer.getMainCamera().getPosition();GL20.glUniform2f(uniform(id,"uErezCamera"),(float)(camera.x-Math.floor(camera.x/4096)*4096),(float)(camera.z-Math.floor(camera.z/4096)*4096));
        GL20.glUniform1f(uniform(id,"uErezSurface"),(float)pro.erez.interstice.ocean.LowerToxinSurface.base(mc.level));GL20.glUniform1f(uniform(id,"uErezTime"),(float)pro.erez.interstice.ocean.LowerToxinSurface.time(mc.level,mc.getTimer().getGameTimeDeltaPartialTick(false)));
        UNIFORMS.increment();if(iris)IrisOceanAdapter.BINDS.increment();return new Binding(units,old);
    }
    public static void restore(Binding saved){if(saved==null)return;int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);for(int i=0;i<saved.units.length;i++){GL13.glActiveTexture(GL13.GL_TEXTURE0+saved.units[i]);GL11.glBindTexture(GL11.GL_TEXTURE_2D,saved.textures[i]);}GL13.glActiveTexture(active);}
    private LowerOceanDh(){}
}
