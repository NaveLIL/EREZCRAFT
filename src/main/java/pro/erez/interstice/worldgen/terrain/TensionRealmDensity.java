package pro.erez.interstice.worldgen.terrain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseSettings;
import pro.erez.interstice.SeaSurface;
import pro.erez.interstice.geometry.GeometryProfile;

/**
 * Revision-six geometry, sampled once by the outer native interpolator. Positive is rock.
 * Grounded basins retain the Y34 coast and deep mineral bases; detached ash plates start
 * above minLand. Every mass ends below the actual upper-sea clearance. Small caves only
 * subtract above lowerSeaTop+5. Broad open vault arches are exterior morphology, not rooms.
 * Native NoiseHolders are wired by RandomState; no world seed or mutable RNG is shared.
 */
public final class TensionRealmDensity implements DensityFunction {
    public static final MapCodec<TensionRealmDensity> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("land").forGetter(TensionRealmDensity::land),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents").forGetter(TensionRealmDensity::continents),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("erosion").forGetter(TensionRealmDensity::erosion),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("vegetation").forGetter(TensionRealmDensity::vegetation),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("ridges").forGetter(TensionRealmDensity::ridges),
            NoiseHolder.CODEC.fieldOf("macro_a").forGetter(TensionRealmDensity::macroA),
            NoiseHolder.CODEC.fieldOf("macro_b").forGetter(TensionRealmDensity::macroB),
            NoiseHolder.CODEC.fieldOf("detail").forGetter(TensionRealmDensity::detail),
            NoiseHolder.CODEC.fieldOf("tunnel_a").forGetter(TensionRealmDensity::tunnelA),
            NoiseHolder.CODEC.fieldOf("tunnel_b").forGetter(TensionRealmDensity::tunnelB),
            NoiseHolder.CODEC.fieldOf("rooms").forGetter(TensionRealmDensity::rooms),
            NoiseSettings.CODEC.fieldOf("sampling").forGetter(TensionRealmDensity::samplingSettings),
            GeometryProfile.CODEC.fieldOf("geometry").forGetter(TensionRealmDensity::geometry),
            Codec.BOOL.optionalFieldOf("carved",true).forGetter(TensionRealmDensity::carved)
    ).apply(i,TensionRealmDensity::new));

    public record Morphology(double ash,double gardens,double vaults,double crimson) {
        public TerrainV2.Weights terrainWeights(){return new TerrainV2.Weights(ash,gardens+crimson,vaults);}
        public String dominant(){
            if(ash>=gardens&&ash>=vaults&&ash>=crimson)return "ash_plates";
            if(vaults>=gardens&&vaults>=crimson)return "stone_arches";
            return gardens>=crimson?"mineral_basins":"root_channels";
        }
    }
    public record Chamber(double x,double y,double z,double radius,double halfHeight) {}
    public record ColumnShape(Morphology morphology,double gardenHeight,double vaultHeight,
                              double crimsonHeight,double ashCenter,double upperAshCenter,
                              double ashThickness,double ashMask,double upperAshMask,
                              double archAcross,double archCenter,double archHalfHeight,
                              double shelfMask,double detail,double tunnelDistanceA,
                              double tunnelDistanceB,double caveCenter,double secondCaveCenter,
                              double upperLimit,List<Chamber> chambers) {
        public double caveFloor(){return caveCenter-2.25;}
    }
    private final DensityFunction land,continents,erosion,vegetation,ridges;
    private final NoiseHolder macroA,macroB,detail,tunnelA,tunnelB,rooms;
    private final NoiseSettings sampling;
    private final GeometryProfile geometry;
    private final boolean carved;
    private final TensionRealmDensity mouthOwner;
    private final V6MouthPlanner mouthPlanner;
    // A native interpolator revisits each X/Z at every Y corner. Immutable derived columns
    // are retained per worker and per seeded field, with a finite memory budget.
    private final ThreadLocal<LinkedHashMap<Long,ColumnShape>> columnCache=
            ThreadLocal.withInitial(()->new LinkedHashMap<>(128,.75F,true));

    public TensionRealmDensity(DensityFunction land,DensityFunction continents,DensityFunction erosion,
            DensityFunction vegetation,DensityFunction ridges,NoiseHolder macroA,NoiseHolder macroB,
            NoiseHolder detail,NoiseHolder tunnelA,NoiseHolder tunnelB,NoiseHolder rooms,
            NoiseSettings sampling,GeometryProfile geometry,boolean carved) {
        this(land,continents,erosion,vegetation,ridges,macroA,macroB,detail,tunnelA,tunnelB,rooms,sampling,geometry,carved,null);
    }
    private TensionRealmDensity(DensityFunction land,DensityFunction continents,DensityFunction erosion,
            DensityFunction vegetation,DensityFunction ridges,NoiseHolder macroA,NoiseHolder macroB,
            NoiseHolder detail,NoiseHolder tunnelA,NoiseHolder tunnelB,NoiseHolder rooms,
            NoiseSettings sampling,GeometryProfile geometry,boolean carved,TensionRealmDensity seededOwner) {
        if(!geometry.equals(GeometryProfile.TALL)||sampling.minY()!=geometry.minY()||sampling.height()!=geometry.height())
            throw new IllegalArgumentException("V6 requires matching TALL256 geometry and sampling settings");
        this.land=land;this.continents=continents;this.erosion=erosion;this.vegetation=vegetation;this.ridges=ridges;
        this.macroA=macroA;this.macroB=macroB;this.detail=detail;this.tunnelA=tunnelA;this.tunnelB=tunnelB;this.rooms=rooms;
        this.sampling=sampling;this.geometry=geometry;this.carved=carved;
        mouthOwner=seededOwner==null?this:seededOwner;
        mouthPlanner=seededOwner==null?new V6MouthPlanner(this):seededOwner.mouthPlanner;
    }
    public DensityFunction land(){return land;}
    public DensityFunction continents(){return continents;}
    public DensityFunction erosion(){return erosion;}
    public DensityFunction vegetation(){return vegetation;}
    public DensityFunction ridges(){return ridges;}
    public NoiseHolder macroA(){return macroA;}
    public NoiseHolder macroB(){return macroB;}
    public NoiseHolder detail(){return detail;}
    public NoiseHolder tunnelA(){return tunnelA;}
    public NoiseHolder tunnelB(){return tunnelB;}
    public NoiseHolder rooms(){return rooms;}
    public NoiseSettings samplingSettings(){return sampling;}
    public GeometryProfile geometry(){return geometry;}
    public boolean carved(){return carved;}
    public V6MouthPlanner mouthPlanner(){return mouthPlanner;}
    public int dryCaveCorner(){return (int)Math.ceil((geometry.lowerSeaTop()+5)/(double)sampling.getCellHeight())*sampling.getCellHeight();}
    private static double clamp(double x,double a,double b){return Math.max(a,Math.min(b,x));}
    private static double smooth(double a,double b,double x){double t=clamp((x-a)/(b-a),0,1);return t*t*(3-2*t);}
    private static double bounded(double x){return clamp(x,-48,48);}
    private static double unit(long key){return (FreeTerraNoise.mix(key)>>>11)*0x1.0p-53;}
    public Morphology morphology(int x,int z){return shape(x,z).morphology();}
    public ColumnShape shape(int x,int z){
        long packed=((long)x<<32)^(z&0xffffffffL);
        var cached=columnCache.get();var found=cached.get(packed);if(found!=null)return found;
        var at=new SinglePointContext(x,0,z);
        double c=continents.compute(at),e=erosion.compute(at),h=vegetation.compute(at),r=ridges.compute(at);
        double ash=1-smooth(-.35,-.20,c),vault=(1-ash)*(1-smooth(-.28,-.12,e));
        double gardens=(1-ash-vault)*smooth(-.23,-.07,h);
        var weights=new Morphology(ash,gardens,vault,Math.max(0,1-ash-vault-gardens));
        // The installed vanilla pre-cave spline is linear in this interior Y range.
        // Its zero crossing retains vanilla continental/erosion height relationships.
        double f0=land.compute(new SinglePointContext(x,64,z)),f1=land.compute(new SinglePointContext(x,72,z));
        double nativeHeight=clamp(Math.abs(f1-f0)<1e-8?62:64-f0*8/(f1-f0),geometry.lowerSeaTop()-3,145);
        double wx=x+macroA.getValue(x*.43,0,z*.43)*29;
        double wz=z+macroB.getValue(x*.43,0,z*.43)*29;
        double a=macroA.getValue(wx*.51,0,wz*.51),b=macroB.getValue(wx*.47,0,wz*.47);
        double broad=macroA.getValue(wx*.19,0,wz*.19),local=detail.getValue(wx*.78,0,wz*.78);
        double coast=smooth(-.31,.22,c);
        double basin=geometry.lowerSeaTop()+2+coast*29+(nativeHeight-50)*.20+18*(a*a-.09)+local*2.1;
        // Gentle mineral terraces remain walkable rather than forming repeated flat plates.
        basin+=Math.sin(basin*.34)*1.1;
        double vaultHeight=geometry.lowerSeaTop()+47+(nativeHeight-50)*.38+broad*22+Math.abs(r)*8+local*2.8;
        double channel=clamp((.14-Math.abs(b))*82,0,12);
        double crimson=geometry.lowerSeaTop()+8+coast*17+(nativeHeight-50)*.11+broad*10+local*1.8-channel;
        double plate=geometry.lowerSeaTop()+53+broad*22+(nativeHeight-50)*.11+a*8;
        double upperPlate=plate+43+b*13;
        double thickness=14+clamp(local*3,-2,3);
        double plateMask=(.46-Math.abs(a))*77+local*3;
        double upperMask=(.32-Math.abs(b))*68+local*2;
        double archAcross=Math.abs(macroB.getValue(wx*.69,0,wz*.69))*45-8.5;
        double archCenter=vaultHeight-21+broad*3;
        double shelf=(.19-Math.abs(a+b*.35))*80;
        double caveCenter=(geometry.lowerSeaTop()+15+broad*6)*(1-ash)+plate*ash;
        double second=(geometry.lowerSeaTop()+38+a*8)*(1-ash)+upperPlate*ash;
        var chambers=new ArrayList<Chamber>();
        int cellX=Math.floorDiv(x,96),cellZ=Math.floorDiv(z,96);
        for(int cx=cellX-1;cx<=cellX+1;cx++)for(int cz=cellZ-1;cz<=cellZ+1;cz++){
            long key=Double.doubleToLongBits(rooms.getValue(cx*23.71+17,0,cz*23.71-31));
            if(unit(key^0x524F4F4D5636L)>=.12)continue;
            double rx=cx*96.+24+unit(key^0x58L)*48,rz=cz*96.+24+unit(key^0x5AL)*48;
            double radius=4+unit(key^0x524144495553L)*3;
            if(Math.abs(x-rx)>radius+4||Math.abs(z-rz)>radius+4)continue;
            double ry=geometry.lowerSeaTop()+17+macroA.getValue(rx*.19,0,rz*.19)*7;
            chambers.add(new Chamber(rx,ry,rz,radius,3+unit(key^0x484549474854L)));
        }
        var result=new ColumnShape(weights,basin,vaultHeight,crimson,plate,upperPlate,thickness,plateMask,upperMask,
                archAcross,archCenter,11+clamp(b*3,-2,3),shelf,local,
                ridgeDistance(tunnelA,wx,wz,1.65),ridgeDistance(tunnelB,wx+71,wz-43,1.42),
                caveCenter,second,SeaSurface.cellMinimum(geometry,x,z,true)-geometry.clearance()-1,List.copyOf(chambers));
        cached.put(packed,result);if(cached.size()>1024)cached.remove(cached.keySet().iterator().next());return result;
    }
    /** A local gradient normalizes tunnel widths in blocks, avoiding wide low-gradient noise halls. */
    private static double ridgeDistance(NoiseHolder noise,double x,double z,double scale){
        double n=noise.getValue(x*scale,0,z*scale);
        double dx=(noise.getValue((x+1)*scale,0,z*scale)-noise.getValue((x-1)*scale,0,z*scale))*.5;
        double dz=(noise.getValue(x*scale,0,(z+1)*scale)-noise.getValue(x*scale,0,(z-1)*scale))*.5;
        double gradient=Math.hypot(dx,dz);
        return gradient<.018?12:Math.abs(n)/Math.max(.025,gradient)-2.05;
    }
    public double uncarvedDensity(int x,int y,int z){return densityWithoutMouths(x,y,z,false);}
    /** Pure corner function used by the planner. It never queries an entrance cache. */
    public double densityWithoutMouths(int x,int y,int z,boolean carve){
        if(y<geometry.minY()||y>=geometry.maxYExclusive())return -4;
        var s=shape(x,z);var w=s.morphology();
        double lowerPlate=Math.min(s.ashThickness()-Math.abs(y-s.ashCenter()),s.ashMask());
        double upperPlate=Math.min(s.ashThickness()*.72-Math.abs(y-s.upperAshCenter()),s.upperAshMask());
        double ash=Math.min(Math.max(lowerPlate,upperPlate),y-geometry.minLand()+.75);
        double gardens=s.gardenHeight()-y;
        double vault=s.vaultHeight()-y;
        // A roofed cut opens to the outside through a coherent horizontal field; a second
        // ledge adds genuinely separated rock intervals. Deep supports retain rare ore habitat.
        double arch=Math.max(s.archAcross(),Math.abs(y-s.archCenter())-s.archHalfHeight());
        if(y>geometry.lowerSeaTop()+8)vault=Math.min(vault,arch);
        double upperShelf=Math.min(7.5-Math.abs(y-s.vaultHeight()-30),s.shelfMask());
        vault=Math.max(vault,upperShelf);
        double result=w.ash()*bounded(ash)+w.gardens()*bounded(gardens)
                +w.vaults()*bounded(vault)+w.crimson()*bounded(s.crimsonHeight()-y);
        result=Math.min(result,s.upperLimit()-y+.5);
        // Keep both bounding native Y corners untouched below the dry-cave boundary.
        // This prevents interpolation from extending a carved corner down into the sea band.
        int dryCorner=dryCaveCorner();
        if(carve&&result>0&&y>dryCorner&&y<geometry.maxLand()-5){
            double passage=Math.max(s.tunnelDistanceA(),Math.abs(y-s.caveCenter())-2.25);
            double extraActivity=smooth(.22,.72,w.ash()+w.vaults());
            double second=Math.max(s.tunnelDistanceB()+(1-extraActivity)*5,
                    Math.abs(y-s.secondCaveCenter())-2.15*extraActivity);
            double cave=Math.min(passage,second);
            for(var chamber:s.chambers()){
                double dy=(y-chamber.y())*chamber.radius()/chamber.halfHeight();
                double distance=Math.sqrt((x-chamber.x())*(x-chamber.x())+(z-chamber.z())*(z-chamber.z())+dy*dy)-chamber.radius();
                cave=Math.min(cave,distance);
            }
            result=Math.min(result,cave);
        }
        return clamp(result/12,-4,4);
    }
    @Override public double compute(FunctionContext context){
        int x=context.blockX(),y=context.blockY(),z=context.blockZ();double value=densityWithoutMouths(x,y,z,carved);
        return carved&&value>0?mouthPlanner.carve(x,y,z,value):value;
    }
    @Override public void fillArray(double[] values,ContextProvider provider){provider.fillAllDirectly(values,this);}
    @Override public DensityFunction mapAll(Visitor visitor){
        var a=visitor.visitNoise(macroA);var b=visitor.visitNoise(macroB);var d=visitor.visitNoise(detail);
        var ca=visitor.visitNoise(tunnelA);var cb=visitor.visitNoise(tunnelB);var r=visitor.visitNoise(rooms);
        // RandomState creates a fresh seed owner. Subsequent NoiseChunk visitors only wrap
        // caches: share its immutable plan coordinates instead of recomputing each chunk.
        boolean sameSeed=macroA.noise()!=null&&a.noise()==macroA.noise()&&b.noise()==macroB.noise()
                &&d.noise()==detail.noise()&&ca.noise()==tunnelA.noise()&&cb.noise()==tunnelB.noise()&&r.noise()==rooms.noise();
        return visitor.apply(new TensionRealmDensity(land.mapAll(visitor),continents.mapAll(visitor),erosion.mapAll(visitor),
                vegetation.mapAll(visitor),ridges.mapAll(visitor),a,b,d,ca,cb,r,
                sampling,geometry,carved,sameSeed?mouthOwner:null));
    }
    @Override public double minValue(){return -4;}
    @Override public double maxValue(){return 4;}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return KeyDispatchDataCodec.of(CODEC);}
}
