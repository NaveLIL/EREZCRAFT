package pro.erez.interstice.vehicle;

/** Game chassis tuning in metres/seconds, not real vehicle specifications. */
public final class EitanDynamics {
    public static final double RADIUS=.68,CRUISE=8.5,BOOST=12,REVERSE=3;
    public static int strength(double mass){return (int)Math.max(5,Math.min(180,Math.round(mass*9.81/(8*400*.20))));}
    public static double anchor(double mass,int strength){return Math.max(.80,Math.min(1.30,RADIUS+.65-.1083333333333333-mass*9.81/(8*400*strength)));}
    public static double damping(double mass,int strength){return Math.max(1,Math.min(12,.8*2*Math.sqrt(400*strength*Math.max(.01,mass)/8)/(10*strength)));}
    public static double steering(double input,double speed,double wheelX,int axle,double wheelZ){if(axle>1||Math.abs(input)<.001)return 0;double angle=input*Math.toRadians(30)/(1+speed*speed/100),radius=4.4/Math.tan(angle);return Math.atan((1.72-wheelZ)/(radius+wheelX));}
    /** Maximum support of a circular tyre over a finite terrain-top interval. */
    public static double circleSupport(double y0,double slope,double min,double max,double radius){
        min=Math.max(min,-radius);max=Math.min(max,radius);if(min>max+1e-6)return Double.NEGATIVE_INFINITY;
        double q=Math.max(min,Math.min(max,slope*radius/Math.sqrt(1+slope*slope)));
        return y0+slope*q+Math.sqrt(Math.max(0,radius*radius-q*q))-radius;
    }
    public static float clearanceTarget(float current,double gap){return (float)Math.max(0,Math.min(.60,current+gap));}
    public static float approach(float a,float target,float step){return a<target?Math.min(target,a+step):Math.max(target,a-step);}
    public static int pose(float a,float target,int steps){if(Math.abs(a-target)<.001)return Math.max(0,Math.min(steps,Math.round(a*steps)));int interval=(int)Math.floor(a*steps-(target<a?.00001:0));return steps+1+Math.max(0,Math.min(steps-1,interval));}
    private EitanDynamics(){}
}
