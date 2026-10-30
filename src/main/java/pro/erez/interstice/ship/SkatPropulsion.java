package pro.erez.interstice.ship;

/** Local Skat tuning; shared Aeronautics and other ships keep their settings. */
public final class SkatPropulsion {
    public static final double FORWARD_LIMIT_MPS=50.0/3.6;
    public static final double REVERSE_LIMIT_MPS=20.0/3.6;
    public static final double THRUST_MULTIPLIER=10.0;
    public static final double MIN_AIRFLOW_PER_RPM=.50;

    /** Reduce only thrust aiding motion near the limit. Opposing thrust can brake. */
    public static double governor(double speedAlongThrust,boolean reverse){
        if(!Double.isFinite(speedAlongThrust))return 0;
        double limit=reverse?REVERSE_LIMIT_MPS:FORWARD_LIMIT_MPS;
        double band=reverse?.5:.35;
        return Math.clamp((limit-speedAlongThrust)/band,0,1);
    }
    /** Preserve low-speed maneuvering; reduce the differential at cruising speed. */
    public static float turningRpm(double horizontalSpeed){
        if(!Double.isFinite(horizontalSpeed))return (float)(18/THRUST_MULTIPLIER);
        return (float)((64-46*Math.clamp((horizontalSpeed-3)/(FORWARD_LIMIT_MPS-3),0,1))/THRUST_MULTIPLIER);
    }
    /** Native point impulses share the remaining velocity budget by actual RPM.
     * This preserves differential steering instead of capping both sides equally. */
    public static double maximumImpulse(double along,double mass,double rpm,boolean reverse){
        if(!Double.isFinite(along)||!Double.isFinite(mass)||!Double.isFinite(rpm)||mass<=0)return 0;
        double limit=reverse?REVERSE_LIMIT_MPS:FORWARD_LIMIT_MPS;
        return mass*Math.max(0,limit-along)*Math.clamp(Math.abs(rpm)/192.0,0,2.0/3);
    }
    private SkatPropulsion(){}
}
