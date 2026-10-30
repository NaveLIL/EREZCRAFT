import pro.erez.interstice.ship.SkatPropulsion;
public class VerifySkatPropulsion {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        check(SkatPropulsion.governor(0,false)==1,"Full starting thrust");
        check(SkatPropulsion.governor(10,false)==1,"Cruise acceleration");
        check(SkatPropulsion.governor(13.8,false)>0&&SkatPropulsion.governor(13.8,false)<1,"Smooth cruise band");
        check(SkatPropulsion.governor(50.0/3.6,false)==0,"50 km/h governor");
        check(SkatPropulsion.governor(18,false)==0,"Overspeed adds no forward thrust");
        check(SkatPropulsion.governor(-18,true)==1,"Reverse remains available for braking");
        check(SkatPropulsion.governor(20.0/3.6,true)==0,"Separate reverse limit");
        check(SkatPropulsion.governor(Double.NaN,false)==0,"Invalid velocity fails closed");
        check(SkatPropulsion.governor(Double.POSITIVE_INFINITY,false)==0,"Infinite velocity fails closed");
        check(Math.abs(SkatPropulsion.turningRpm(0)-64/SkatPropulsion.THRUST_MULTIPLIER)<.0001,"Low-speed steering torque preserved");
        check(Math.abs(SkatPropulsion.turningRpm(50.0/3.6)-18/SkatPropulsion.THRUST_MULTIPLIER)<.0001,"Cruise turn differential reduced");
        check(SkatPropulsion.turningRpm(5)>SkatPropulsion.turningRpm(10),"Turn response decreases with speed");
        check(SkatPropulsion.maximumImpulse(50.0/3.6,45,96,false)==0,"No impulse aiding overspeed");
        check(SkatPropulsion.maximumImpulse(-10,45,-96,true)>0,"Impulse budget permits reverse braking");
        check(SkatPropulsion.maximumImpulse(0,45,112,false)>SkatPropulsion.maximumImpulse(0,45,80,false),"Impulse budget preserves steering");
        check(SkatPropulsion.maximumImpulse(0,0,96,false)==0,"Invalid mass fails closed");
        System.out.println("SKAT_PROPULSION_CONTRACTS "+checks+" passed; native force/motion acceptance is separate");
    }
}
