import pro.erez.interstice.vehicle.EitanDynamics;
import pro.erez.interstice.client.EitanCameraOptics;
/** Pure numerical contracts; native traction, input and rendering are separate gates. */
public class CheckEitanDynamics {
    static int checks;
    static void require(boolean value,String detail){checks++;if(!value)throw new AssertionError(detail);}
    public static void main(String[] args){
        for(int i=0;i<=1000;i++){float p=i/1000f;int open=EitanDynamics.pose(p,1,16),close=EitanDynamics.pose(p,0,16);require(open>=0&&open<33&&close>=0&&close<33,"Bounded collision pose");}
        require(EitanDynamics.pose(0,0,16)==0&&EitanDynamics.pose(1,1,16)==16,"Stationary endpoints");
        require(EitanDynamics.pose(.5f,1,16)!=EitanDynamics.pose(.5f,0,16),"Correct swept interval when direction reverses at a boundary");
        for(double mass:new double[]{.1,22.6393945125,100,1000,10000}){int s=EitanDynamics.strength(mass);require(s>=5&&s<=180,"Pinned Offroad slider limits");double a=EitanDynamics.anchor(mass,s);require(Double.isFinite(a)&&a>=.8&&a<=1.3,"Physical anchor range");require(EitanDynamics.damping(mass,s)>=1&&EitanDynamics.damping(mass,s)<=12,"Bounded damping");}
        double left=EitanDynamics.steering(1,0,-1.26,0,-2.68),right=EitanDynamics.steering(1,0,1.26,0,-2.68);
        require(left>right&&right>0,"Ackermann inner front wheel turns more");require(EitanDynamics.steering(1,12,-1.26,0,-2.68)<left,"High speed steering attenuation");require(EitanDynamics.steering(1,0,-1.26,2,.84)==0,"Rear axles remain straight");
        require(EitanDynamics.CRUISE>4.3&&EitanDynamics.REVERSE>0&&EitanDynamics.BOOST>EitanDynamics.CRUISE,"Driving speed tiers");
        for(int sector=0;sector<3;sector++){var samples=EitanCameraOptics.SAMPLES[sector];for(int i=0;i<samples.length;i++){var p=samples[i];require(Float.isFinite(p.u())&&p.u()>=-.00001&&p.u()<=1.00001&&p.bottom()>0&&p.top()<1,"All monitor samples inside sensor texture");if(i>0)require(p.u()>samples[i-1].u(),"No mirrored or folded camera view");}if(sector<2)require(Math.abs(samples[32].u()-EitanCameraOptics.SAMPLES[sector+1][0].u())<.00001,"Adjacent angular views meet without gaps");}
        require(Math.abs(EitanCameraOptics.SAMPLES[1][16].u()-.5)<.00001,"Forward view is centred");
        double previous=0,maxRise=0;for(int i=0;i<=680;i++){double gap=.68-i*.001,height=Math.max(0,EitanDynamics.circleSupport(.5,0,gap,gap+1,.68));require(height+1e-9>=previous,"Continuous wheel ascent is monotonic");maxRise=Math.max(maxRise,height-previous);previous=height;}
        require(Math.abs(previous-.5)<1e-8&&maxRise<.005,"No half-block jump at first tyre contact");
        require(Math.abs(EitanDynamics.circleSupport(0,0,-1,1,.68))<1e-9,"Flat terrain unchanged");
        require(Math.abs(EitanDynamics.circleSupport(0,.2,-1,1,.68)-.68*(Math.sqrt(1.04)-1))<1e-9,"Analytic tilted plane support");
        for(int i=0;i<=600;i++){float lift=i/1000f;require(EitanDynamics.clearanceTarget(lift,10)==.60f,"Ride-height hard upper stop");require(EitanDynamics.clearanceTarget(lift,-10)==0,"Ride-height lower stop");require(Math.abs(EitanDynamics.clearanceTarget(lift,0)-lift)<1e-6,"Ride height holds when clearance is satisfied");float next=EitanDynamics.approach(lift,.60f,.02f);require(next>=lift&&next-lift<=.020001,"Suspension lift rate bounded");}
        System.out.println("EITAN_NUMERICAL_CONTRACTS_PASSED "+checks);
    }
}
