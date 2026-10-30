package pro.erez.interstice.vehicle;
import net.neoforged.bus.api.IEventBus;
/** Also serves as the narrowly patched registration hook in the published base. */
public final class VehiclesBootstrap {
    public static void register(IEventBus bus){pro.erez.interstice.ship.SkatShips.register(bus);if(net.neoforged.fml.ModList.get().isLoaded("offroad"))EitanVehicles.register(bus);bus.addListener(VehicleNetwork::register);}
    private VehiclesBootstrap(){}
}
