package pro.erez.interstice.worldgen.terrain;

import java.util.ArrayList;
import java.util.List;
import pro.erez.interstice.geometry.GeometryProfile;

/** V4's smoothly warped compound masses. No rectangular clipping or hard-cleft removal branch. */
public final class FloatingLandformsV4 {
    private static final int SPACING=320;
    private record Slice(double top,double bottom,double side) {
        double density(double y){return Math.min(side,Math.min(top-y,y-bottom))/12;}
    }
    public static final class Column {
        private final List<Slice> slices;
        private final int minimum;
        private final double maximum;
        private Column(List<Slice> slices,int minimum,double maximum){this.slices=List.copyOf(slices);this.minimum=minimum;this.maximum=maximum;}
        public double density(double y){if(y<minimum||y>=maximum)return -8;double value=-8;for(var slice:slices)value=Math.max(value,slice.density(y));return value;}
        public int highestSolidY(){return slices.stream().filter(s->s.side>0&&s.top>s.bottom).mapToInt(s->(int)Math.ceil(s.top)-1).max().orElse(-1);}
        public int lowestSolidY(){return slices.stream().filter(s->s.side>0&&s.top>s.bottom).mapToInt(s->(int)Math.floor(s.bottom)+1).min().orElse(-1);}
    }
    private FloatingLandformsV4() {}
    public static Column column(long seed,GeometryProfile profile,int x,int z) {
        int field=FreeTerraNoise.fieldSeed(seed,0x5634464C4F41544CL);
        double wx=x+FreeTerraNoise.warpOffset(field,x,z,260,2,58),wz=z+FreeTerraNoise.warpOffset(field+1,x,z,260,2,58);
        int cellX=(int)Math.floor(wx/SPACING),cellZ=(int)Math.floor(wz/SPACING);
        double cap=Math.min(profile.minY()+190,profile.maxLand()-15);var slices=new ArrayList<Slice>(3);
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            int cx=cellX+dx,cz=cellZ+dz;
            long key=FreeTerraNoise.mix(seed^cx*0x9E3779B97F4A7C15L^cz*0xC2B2AE3D27D4EB4FL^0x563449534C414E44L);
            if(unit(key,0)>.7)continue;
            double centerX=(cx+.25+unit(key,1)*.5)*SPACING,centerZ=(cz+.25+unit(key,2)*.5)*SPACING;
            // Radius+lobes+domain displacement stay under240; only this surrounding3x3 can contribute.
            if(Math.abs(wx-centerX)>240||Math.abs(wz-centerZ)>240)continue;
            double angle=unit(key,3)*Math.PI*2,cos=Math.cos(angle),sin=Math.sin(angle);
            double u=(wx-centerX)*cos+(wz-centerZ)*sin,v=-(wx-centerX)*sin+(wz-centerZ)*cos;
            double rx=85+unit(key,4)*45,rz=72+unit(key,5)*40;
            double phi=1-square(u/rx)-square(v/rz);
            int lobes=2+(int)(unit(key,6)*3);
            for(int lobe=0;lobe<lobes;lobe++) {
                int salt=20+lobe*5;double direction=unit(key,salt)*Math.PI*2;
                double pu=Math.cos(direction)*rx*(.45+unit(key,salt+1)*.5),pv=Math.sin(direction)*rz*(.45+unit(key,salt+2)*.5);
                double a=(u-pu)/(rx*(.4+unit(key,salt+3)*.2)),b=(v-pv)/(rz*(.4+unit(key,salt+4)*.2));
                phi=smoothMaximum(phi,1-a*a-b*b,.18);
            }
            phi+=(FreeTerraNoise.fractal(seed^key,0x563445444745L,wx,wz,48,2)-.5)*.27;
            double shape=FreeTerraNoise.clamp(phi,0,1);
            double depth=36+unit(key,7)*24;
            double center=profile.minLand()+44+unit(key,8)*57;
            double tilt=(unit(key,9)-.5)*10*u/rx+(unit(key,10)-.5)*8*v/rz;
            double relief=(FreeTerraNoise.fractal(seed^key,0x5634544F5053L,wx,wz,72,2)-.5)*12
                    +(FreeTerraNoise.fractal(seed^key,0x5634544F50464EL,wx,wz,22,2)-.5)*5;
            double top=center+depth*.32+tilt+relief-(1-shape)*5;
            double bottom=center-depth*(.40+.52*Math.sqrt(shape))+tilt;
            double fangs=0;
            for(int fang=0;fang<3;fang++) {
                int salt=60+fang*5;
                double fu=(u-(unit(key,salt)-.5)*rx*1.3)/(17+unit(key,salt+1)*18);
                double fv=(v-(unit(key,salt+2)-.5)*rz*1.3)/(17+unit(key,salt+3)*18);
                fangs=smoothMaximum(fangs,Math.max(0,1-Math.hypot(fu,fv))*(12+unit(key,salt+4)*20),1.5);
            }
            bottom-=fangs;
            // A wandering soft fault changes relief and thickness continuously. Unlike V3's
            // abs(v-cleft)<width / continue, it cannot remove a column with a straight cut plane.
            double wandering=(unit(key,11)-.5)*rz*.45
                    +(FreeTerraNoise.fractal(seed^key,0x56344641554C54L,u,v*.3,78,2)-.5)*34;
            double width=10+unit(key,12)*14;
            double fault=1-FreeTerraNoise.smoothBetween(width*.35,width*1.6,Math.abs(v-wandering));
            double along=FreeTerraNoise.smoothBetween(.22,.70,FreeTerraNoise.fractal(seed^key,0x5634425245414BL,u,v,105,2));
            double incision=fault*along*(9+unit(key,13)*16);
            top-=incision;bottom+=incision*.45;
            top=softCeiling(top,cap-8,cap);
            // At the envelope phi=0 the signed side field crosses zero smoothly; slices with
            // negative phi remain harmless negative density rather than being hard-cut off.
            slices.add(new Slice(top,Math.max(profile.minLand()-.01,bottom),phi*38));
        }
        return new Column(slices,profile.minLand(),cap);
    }
    private static double square(double value){return value*value;}
    private static double smoothMaximum(double a,double b,double k){double h=FreeTerraNoise.clamp(.5+.5*(a-b)/k,0,1);return FreeTerraNoise.lerp(b,a,h)+k*h*(1-h);}
    private static double softCeiling(double value,double start,double cap){if(value<=start)return value;double span=cap-start,excess=value-start;return start+span*excess/(span+excess);}
    private static double unit(long key,int salt){return (FreeTerraNoise.mix(key+salt*0x9E3779B97F4A7C15L)>>>11)*0x1.0p-53;}
}
