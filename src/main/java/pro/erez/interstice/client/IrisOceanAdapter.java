package pro.erez.interstice.client;
import java.util.concurrent.atomic.LongAdder;
/** Pinned Complementary DH water source adaptation, in memory; source ZIP stays original. */
public final class IrisOceanAdapter {
    public static final LongAdder BINDS=new LongAdder();public static int patches;
    public static String adapt(String path,String s){
        if(s==null||!(path.endsWith("dh_water.fsh")||path.endsWith("dh_terrain.fsh")))return s;
        if(!s.contains("in vec3 playerPos;")||!s.contains("gl_FragData[0] = color;"))throw new IllegalStateException("Pinned Complementary DH source changed");
        String material;try{var type=Class.forName("com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial");material=Byte.toString(type.getField("index").getByte(type.getField("WATER").get(null)));}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        String globals="uniform bool uErezEnabled;uniform bool uErezMask;uniform sampler2D uErezAtlas;uniform sampler2D uErezLightMap;uniform sampler2D uErezShore;uniform vec4 uErezSprite;uniform float uErezSurface;uniform float uErezTime;uniform float uErezWaveScale;uniform vec2 uErezCamera;uniform float uErezShoreRadius;\n"+LowerOceanDh.WAVES+LowerOceanDh.SHORE;
        String body="""
            gl_FragDepth=gl_FragCoord.z;
            if(uErezEnabled && mat==DH_BLOCK_WATER && dot(normal,upVec)>.9 && abs(playerPos.y+cameraPosition.y-uErezSurface-1.0/9.0)<.5 && abs(playerPos.y)>.001){
                if(texture2D(depthtex0,gl_FragCoord.xy/vec2(viewWidth,viewHeight)).r<1.0)discard;
                float t=(uErezSurface-cameraPosition.y)/playerPos.y;
                vec2 base=mod(cameraPosition.xz,4096.0)+playerPos.xz;
                if(uErezWaveScale>.5)for(int i=0;i<5;i++){vec2 xz=base+playerPos.xz*(t-1.0);float target=uErezSurface+erezWave(xz,uErezTime)*erezMask(xz);t=clamp(t+(target-cameraPosition.y-playerPos.y*t)/playerPos.y,.3,1.7);}
                vec2 p=fract((base+playerPos.xz*(t-1.0))/64.0);
                vec2 uv=mix(uErezSprite.xy,uErezSprite.zw,vec2((.5+p.x)*640.0+.5,(1.5-p.y)*640.0-.5)/1280.0);
                vec4 color=vec4(texture2D(uErezAtlas,uv).rgb*texture2D(uErezLightMap,clamp(lmCoord,vec2(.03125),vec2(.96875))).rgb,1.0);
                if(uErezMask)color=vec4(0.0,8.0,0.0,1.0);
                vec4 clip=dhProjection*gbufferModelView*vec4(playerPos*t,1.0);gl_FragDepth=clamp(.5*clip.z/clip.w+.5,0.0,1.0);
                gl_FragData[0]=color;return;
            }
            """;
        patches++;return s.replace("void main() {",globals+"\nvoid main() {\n"+body.replace("mat==DH_BLOCK_WATER","mat=="+material).replace("if(texture2D(depthtex0,gl_FragCoord.xy/vec2(viewWidth,viewHeight)).r<1.0)discard;",path.endsWith("dh_water.fsh")?"if(texture2D(depthtex0,gl_FragCoord.xy/vec2(viewWidth,viewHeight)).r<1.0)discard;":""));
    }
    private IrisOceanAdapter(){}
}
