#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ErezOffset;
uniform vec2 ErezWorld;
uniform float ErezTime;
uniform float ErezWaveScale;
uniform sampler2D Sampler2;
out vec3 toxinRelative;
out vec2 worldXZ;
out vec4 lighting;
out float vertexDistance;
out float skyLight;
float waveHeight(vec2 p,float t){return .20*sin(6.28318530718*(p.x/64.0+t/240.0))+.14*sin(6.28318530718*(p.y/128.0-t/384.0))+.08*sin(6.28318530718*((p.x+p.y)/32.0+t/640.0));}
void main(){skyLight=float(UV2.y/16);worldXZ=ErezWorld+Position.xz;vec3 p=Position+ErezOffset;if(ErezWaveScale>.5)p.y+=waveHeight(worldXZ,ErezTime)*Color.a;toxinRelative=p;vertexDistance=length(p);gl_Position=ProjMat*ModelViewMat*vec4(p,1);lighting=vec4(texelFetch(Sampler2,UV2/16,0).rgb,1);}
