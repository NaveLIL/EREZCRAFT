#version 150
uniform sampler2D Sampler0;
uniform vec4 ErezSprite;
in vec2 worldXZ;
in vec4 lighting;
in float vertexDistance;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
in vec3 toxinRelative;
uniform sampler2D ErezHullMask;
uniform int ErezHullCount;
uniform mat4 ErezHullM0;uniform vec4 ErezHullS0;
uniform mat4 ErezHullM1;uniform vec4 ErezHullS1;
uniform mat4 ErezHullM2;uniform vec4 ErezHullS2;
uniform mat4 ErezHullM3;uniform vec4 ErezHullS3;
uniform mat4 ErezHullM4;uniform vec4 ErezHullS4;
uniform mat4 ErezHullM5;uniform vec4 ErezHullS5;
uniform mat4 ErezHullM6;uniform vec4 ErezHullS6;
uniform mat4 ErezHullM7;uniform vec4 ErezHullS7;
uniform mat4 ErezHullM8;uniform vec4 ErezHullS8;
uniform mat4 ErezHullM9;uniform vec4 ErezHullS9;
uniform mat4 ErezHullM10;uniform vec4 ErezHullS10;
uniform mat4 ErezHullM11;uniform vec4 ErezHullS11;
uniform mat4 ErezHullM12;uniform vec4 ErezHullS12;
uniform mat4 ErezHullM13;uniform vec4 ErezHullS13;
uniform mat4 ErezHullM14;uniform vec4 ErezHullS14;
uniform mat4 ErezHullM15;uniform vec4 ErezHullS15;
bool erezDry(mat4 m,vec4 size,int slot){vec3 p=(m*vec4(toxinRelative,1.0)).xyz;if(any(lessThan(p,vec3(0)))||any(greaterThanEqual(p,size.xyz)))return false;ivec3 cell=ivec3(floor(p));return texelFetch(ErezHullMask,ivec2(slot*128+cell.x,cell.z*32+cell.y),0).r>.5;}
uniform sampler2D ErezShipLights;
uniform int ErezShipLightCount;
uniform mat4 ErezShipLightM0;uniform vec4 ErezShipLightS0;
uniform mat4 ErezShipLightM1;uniform vec4 ErezShipLightS1;
uniform mat4 ErezShipLightM2;uniform vec4 ErezShipLightS2;
uniform mat4 ErezShipLightM3;uniform vec4 ErezShipLightS3;
vec4 erezShipSample(vec3 p){
    vec3 g=p-vec3(-16.0,-8.0,-28.0);
    if(any(lessThan(g,vec3(0)))||any(greaterThan(g,vec3(33,29,55))))return vec4(0);
    float a=floor(g.z),b=min(a+1.0,55.0);
    vec2 uv=vec2(g.x+.5,a*30.0+g.y+.5)/vec2(34,1680);
    vec2 uv2=vec2(g.x+.5,b*30.0+g.y+.5)/vec2(34,1680);
    return mix(texture(ErezShipLights,uv),texture(ErezShipLights,uv2),fract(g.z));
}
vec3 erezShipLight(mat4 m,vec4 enabled){
    vec4 v=erezShipSample((m*vec4(toxinRelative,1)).xyz)*enabled;
    float warm=max(v.r,max(v.g,v.b));return max(warm*vec3(1.0,.82,.58),v.a*vec3(.45,.8,.83));
}
in float skyLight;
uniform sampler2D Sampler2;
uniform int ErezEntityLightCount;
uniform vec4 ErezEntityLight0;
uniform vec4 ErezEntityLight1;
uniform vec4 ErezEntityLight2;
uniform vec4 ErezEntityLight3;
uniform vec4 ErezEntityLight4;
uniform vec4 ErezEntityLight5;
uniform vec4 ErezEntityLight6;
uniform vec4 ErezEntityLight7;
float erezEntityLight(vec4 p){return max(0.0,1.0-distance(toxinRelative,p.xyz)/7.75)*p.w;}
out vec4 fragColor;
void main(){if(ErezHullCount>0&&erezDry(ErezHullM0,ErezHullS0,0))discard;if(ErezHullCount>1&&erezDry(ErezHullM1,ErezHullS1,1))discard;if(ErezHullCount>2&&erezDry(ErezHullM2,ErezHullS2,2))discard;if(ErezHullCount>3&&erezDry(ErezHullM3,ErezHullS3,3))discard;if(ErezHullCount>4&&erezDry(ErezHullM4,ErezHullS4,4))discard;if(ErezHullCount>5&&erezDry(ErezHullM5,ErezHullS5,5))discard;if(ErezHullCount>6&&erezDry(ErezHullM6,ErezHullS6,6))discard;if(ErezHullCount>7&&erezDry(ErezHullM7,ErezHullS7,7))discard;if(ErezHullCount>8&&erezDry(ErezHullM8,ErezHullS8,8))discard;if(ErezHullCount>9&&erezDry(ErezHullM9,ErezHullS9,9))discard;if(ErezHullCount>10&&erezDry(ErezHullM10,ErezHullS10,10))discard;if(ErezHullCount>11&&erezDry(ErezHullM11,ErezHullS11,11))discard;if(ErezHullCount>12&&erezDry(ErezHullM12,ErezHullS12,12))discard;if(ErezHullCount>13&&erezDry(ErezHullM13,ErezHullS13,13))discard;if(ErezHullCount>14&&erezDry(ErezHullM14,ErezHullS14,14))discard;if(ErezHullCount>15&&erezDry(ErezHullM15,ErezHullS15,15))discard;vec2 p=fract(worldXZ/64.0);vec2 localUv=vec2((.5+p.x)*640.0+.5,(1.5-p.y)*640.0-.5)/1280.0;vec2 uv=mix(ErezSprite.xy,ErezSprite.zw,localUv);vec3 illumination=lighting.rgb;if(ErezShipLightCount>0)illumination=max(illumination,erezShipLight(ErezShipLightM0,ErezShipLightS0));if(ErezShipLightCount>1)illumination=max(illumination,erezShipLight(ErezShipLightM1,ErezShipLightS1));if(ErezShipLightCount>2)illumination=max(illumination,erezShipLight(ErezShipLightM2,ErezShipLightS2));if(ErezShipLightCount>3)illumination=max(illumination,erezShipLight(ErezShipLightM3,ErezShipLightS3));float entityLight=0.0;if(ErezEntityLightCount>0)entityLight=max(entityLight,erezEntityLight(ErezEntityLight0));if(ErezEntityLightCount>1)entityLight=max(entityLight,erezEntityLight(ErezEntityLight1));if(ErezEntityLightCount>2)entityLight=max(entityLight,erezEntityLight(ErezEntityLight2));if(ErezEntityLightCount>3)entityLight=max(entityLight,erezEntityLight(ErezEntityLight3));if(ErezEntityLightCount>4)entityLight=max(entityLight,erezEntityLight(ErezEntityLight4));if(ErezEntityLightCount>5)entityLight=max(entityLight,erezEntityLight(ErezEntityLight5));if(ErezEntityLightCount>6)entityLight=max(entityLight,erezEntityLight(ErezEntityLight6));if(ErezEntityLightCount>7)entityLight=max(entityLight,erezEntityLight(ErezEntityLight7));if(entityLight>0.0)illumination=max(illumination,texture(Sampler2,vec2(entityLight+.5,skyLight+.5)/16.0).rgb);fragColor=vec4(texture(Sampler0,uv).rgb*illumination,1);fragColor.rgb=mix(fragColor.rgb,FogColor.rgb,smoothstep(FogStart,FogEnd,vertexDistance)*FogColor.a);}
