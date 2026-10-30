package pro.erez.interstice.ship;
/** Shared immutable local light field: one small atlas for every ship, never a fluid/terrain simulation. */
public final class SkatLightField {
    public static final int X0=-16,Y0=-8,Z0=-28,NX=34,NY=30,NZ=56;
    public static final byte[] ATLAS=build();
    private static byte[] build(){
        var out=new byte[NX*NY*NZ*4];
        for(int z=0;z<NZ;z++)for(int y=0;y<NY;y++)for(int x=0;x<NX;x++){
            double px=X0+x,py=Y0+y,pz=Z0+z;double[] values=new double[4];
            for(var light:SkatData.LIGHTS){
                double[] a=light.a(),b=light.b();int channel=light.channel()==1?(a[0]<.5?0:1):light.channel()==2?2:3;
                double level;
                if(light.channel()==1){
                    double dx=px-a[0],dy=py-a[1],dz=pz-a[2],along=-dz*.9781476-dy*.2079117;
                    if(along<=0||along>=14)continue;
                    double d2=dx*dx+dy*dy+dz*dz,radial2=Math.max(0,d2-along*along),width=.35+along*.48;
                    if(radial2>=width*width)continue;
                    double edge=Math.clamp((1-Math.sqrt(radial2)/width)/.35,0,1);edge=edge*edge*(3-2*edge);
                    level=15*Math.max(0,1-Math.sqrt(d2)/14)*edge;
                }else{
                    if(channel==3&&!((px>=-3.7&&px<=4.7&&py>=1.6&&py<=3.9&&pz>=-6.4&&pz<=12.1)||(px>=-3&&px<=4&&py>=4&&py<=6.6&&pz>=-6.8&&pz<=-2.2)))continue;
                    double vx=b[0]-a[0],vy=b[1]-a[1],vz=b[2]-a[2],length=vx*vx+vy*vy+vz*vz;
                    double t=length==0?0:Math.clamp(((px-a[0])*vx+(py-a[1])*vy+(pz-a[2])*vz)/length,0,1);
                    double dx=px-a[0]-t*vx,dy=py-a[1]-t*vy,dz=pz-a[2]-t*vz;
                    double radius=channel==2?10:14,d2=dx*dx+dy*dy+dz*dz;if(d2>=radius*radius)continue;
                    level=radius-Math.sqrt(d2);
                }
                values[channel]=Math.max(values[channel],level);
            }
            int index=((z*NY+y)*NX+x)*4;for(int c=0;c<4;c++)out[index+c]=(byte)Math.round(Math.clamp(values[c]/15,0,1)*255);
        }return out;
    }
    private static double value(int x,int y,int z,int channel){return Byte.toUnsignedInt(ATLAS[((z*NY+y)*NX+x)*4+channel])/17.0;}
    public static double channel(double x,double y,double z,int channel){
        x-=X0;y-=Y0;z-=Z0;if(x<0||y<0||z<0||x>NX-1||y>NY-1||z>NZ-1)return 0;
        int ix=(int)x,iy=(int)y,iz=(int)z,jx=Math.min(ix+1,NX-1),jy=Math.min(iy+1,NY-1),jz=Math.min(iz+1,NZ-1);double fx=x-ix,fy=y-iy,fz=z-iz;
        double low=lerp(fy,lerp(fx,value(ix,iy,iz,channel),value(jx,iy,iz,channel)),lerp(fx,value(ix,jy,iz,channel),value(jx,jy,iz,channel)));
        double high=lerp(fy,lerp(fx,value(ix,iy,jz,channel),value(jx,iy,jz,channel)),lerp(fx,value(ix,jy,jz,channel),value(jx,jy,jz,channel)));return lerp(fz,low,high);
    }
    public static double sample(double x,double y,double z,int mask){double v=0;if((mask&1)!=0)v=Math.max(channel(x,y,z,0),channel(x,y,z,1));if((mask&2)!=0)v=Math.max(v,channel(x,y,z,2));if((mask&4)!=0)v=Math.max(v,channel(x,y,z,3));return v;}
    private static double lerp(double t,double a,double b){return a+(b-a)*t;}
    private SkatLightField(){}
}
