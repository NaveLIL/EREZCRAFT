package pro.erez.interstice.ship;

import java.util.UUID;

/** Horizontal ground speed from a fixed hull reference, sampled in server ticks. */
public final class SkatSpeedometer {
    private UUID body;private long lastTick=Long.MIN_VALUE;private double lastX,lastZ;private float speed;
    public void reset(){body=null;lastTick=Long.MIN_VALUE;speed=0;}
    public float value(){return speed;}
    public float sample(long tick,UUID id,double x,double z){
        if(!Double.isFinite(x)||!Double.isFinite(z)||id==null){reset();return 0;}
        if(!id.equals(body)||tick<=lastTick||lastTick==Long.MIN_VALUE||tick-lastTick>20){
            body=id;lastTick=tick;lastX=x;lastZ=z;speed=0;return 0;
        }
        long elapsed=tick-lastTick;if(elapsed<5)return speed;
        double measured=Math.hypot(x-lastX,z-lastZ)*20.0/elapsed;
        // A teleport/rebase is not a sailing speed. Never display invalid numbers.
        speed=!Double.isFinite(measured)||measured>64?0:(float)(speed*.5+measured*.5);
        if(speed<.025F)speed=0;
        lastTick=tick;lastX=x;lastZ=z;return speed;
    }
}
