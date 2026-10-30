package pro.erez.interstice.client;
/** Client GUI construction isolated from common payload registration. */
public final class VehicleClientAccess {public static void receive(pro.erez.interstice.vehicle.VehicleNetwork.Panel p,net.neoforged.neoforge.network.handling.IPayloadContext c){c.enqueueWork(()->net.minecraft.client.Minecraft.getInstance().setScreen(new VehicleAccessScreen(p)));}private VehicleClientAccess(){}}
