import java.util.UUID;
import pro.erez.interstice.ship.SkatSpeedometer;

/** Black-box units, identity, stale state and teleport contracts; no game or user profile. */
public class VerifySkatSpeed {
    private static int passed;
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);passed++;}
    public static void main(String[] args){
        var s=new SkatSpeedometer();var id=UUID.randomUUID();
        for(int i=0;i<=200;i++)s.sample(i,id,i*.2,0);
        check(Math.abs(s.value()-4)<.001,"4 blocks/s must read 4 m/s");
        check(Math.abs(s.value()*3.6-14.4)<.01,"km/h conversion");
        s.reset();for(int i=0;i<=200;i++)s.sample(i,id,0,-i*.2);
        check(Math.abs(s.value()-4)<.001,"Reverse/sideways sailing must show positive ground speed");
        for(int i=201;i<=400;i++)s.sample(i,id,0,-40);
        check(s.value()==0,"Stationary hull must settle to zero");
        s.sample(401,UUID.randomUUID(),999,999);check(s.value()==0,"New body must reset old speed");
        s.reset();s.sample(0,id,0,0);s.sample(5,id,10000,10000);check(s.value()==0,"Teleport must not appear as sailing speed");
        s.sample(100,id,10001,10001);check(s.value()==0,"Stale measurement must reset");
        s.sample(101,id,Double.NaN,0);check(s.value()==0,"Invalid coordinate must not reach HUD");
        System.out.println("{\"passed\":"+passed+",\"units\":\"m/s\",\"fixture_speed_kmh\":14.4}");
    }
}
