package pro.erez.interstice.vehicle;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;
/** Opt-in bounded in-process measurements. No sampling worker or external upload. */
@EventBusSubscriber(modid=Interstice.ID)
public final class VehicleMetrics {
    private static final class Count {final LongAdder calls=new LongAdder(),nanos=new LongAdder();}
    private static final Map<String,Count> counts=new ConcurrentHashMap<>();
    private static final List<Double> ticks=new ArrayList<>();
    private static volatile boolean active;private static int remaining;private static long tickStart;
    public static long begin(){return active?System.nanoTime():0;}
    public static void end(String key,long start){if(start!=0){var c=counts.computeIfAbsent(key,k->new Count());c.calls.increment();c.nanos.add(System.nanoTime()-start);}}
    public static void hit(String key){if(active)counts.computeIfAbsent(key,k->new Count()).calls.increment();}
    public static void start(int seconds){active=false;counts.clear();ticks.clear();remaining=seconds*20;active=true;}
    @SubscribeEvent public static void pre(ServerTickEvent.Pre event){if(active)tickStart=System.nanoTime();}
    @SubscribeEvent public static void post(ServerTickEvent.Post event){if(!active)return;ticks.add((System.nanoTime()-tickStart)/1e6);if(--remaining<=0)active=false;}
    public static String report(){var out=new com.google.gson.JsonObject();out.addProperty("running",active);out.addProperty("ticks",ticks.size());var sorted=ticks.stream().sorted().toList();if(!sorted.isEmpty()){out.addProperty("mean_ms",ticks.stream().mapToDouble(Double::doubleValue).average().orElse(0));out.addProperty("p95_ms",sorted.get(Math.min(sorted.size()-1,(int)(sorted.size()*.95))));out.addProperty("p99_ms",sorted.get(Math.min(sorted.size()-1,(int)(sorted.size()*.99))));}var values=new com.google.gson.JsonObject();counts.forEach((k,c)->{var v=new com.google.gson.JsonObject();v.addProperty("calls",c.calls.sum());v.addProperty("total_ms",c.nanos.sum()/1e6);values.add(k,v);});out.add("work",values);return out.toString();}
    private VehicleMetrics(){}
}
