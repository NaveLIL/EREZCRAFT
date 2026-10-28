package pro.erez.interstice.client;

import java.util.*;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.*;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.gear.EngineerArmor;

@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class EngineerArmorClient{
    private static final Map<EquipmentSlot,EngineerArmorModel> MODELS=new EnumMap<>(EquipmentSlot.class);
    public static EngineerArmorModel model(EquipmentSlot slot){return MODELS.computeIfAbsent(slot,EngineerArmorModel::new);}
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event){event.registerReloadListener((ResourceManagerReloadListener)manager->{MODELS.clear();EngineerArmorModel.reload();});}
    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent event){
        for(var kind:List.of("helmet","chestplate","leggings","boots"))event.registerItem(new IClientItemExtensions(){
            @Override public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity,ItemStack stack,EquipmentSlot slot,HumanoidModel<?> original){return model(slot);}
            @Override public void setupModelAnimations(LivingEntity entity,ItemStack stack,EquipmentSlot slot,Model model,float limbSwing,float limbAmount,float partial,float age,float yaw,float pitch){((EngineerArmorModel)model).prepare(entity,partial);}
        },EngineerArmor.item(kind));
    }
}
