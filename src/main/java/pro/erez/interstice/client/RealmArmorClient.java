package pro.erez.interstice.client;

import java.util.*;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.*;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import pro.erez.interstice.Interstice;
import pro.erez.interstice.gear.*;

@EventBusSubscriber(modid=Interstice.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class RealmArmorClient {
    private static final Map<String,RealmArmorModel> MODELS=new HashMap<>();
    private RealmArmorClient(){}
    public static RealmArmorModel model(boolean steel,EquipmentSlot slot){
        String tier=steel?"riftsteel":"riftsilver";
        return model(tier,slot);
    }
    public static RealmArmorModel model(String tier,EquipmentSlot slot){
        return MODELS.computeIfAbsent(tier+"/"+slot.getName(),key->new RealmArmorModel(tier)).slot(slot);
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event){event.registerReloadListener((ResourceManagerReloadListener)manager->MODELS.clear());}
    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent event){
        for(String tier:List.of("riftsilver","riftsteel","aegis","kotomyaf"))for(var slot:RealmArmor.SLOTS){
            String kind=switch(slot){case HEAD->"helmet";case CHEST->"chestplate";case LEGS->"leggings";case FEET->"boots";default->throw new IllegalStateException();};
            event.registerItem(new IClientItemExtensions(){
                private RealmArmorModel active;
                @Override public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity,ItemStack stack,EquipmentSlot worn,HumanoidModel<?> original){active=model(tier,worn);active.powered=AegisPower.powered(entity,worn);return active;}
                @Override public int getArmorLayerTintColor(ItemStack stack,LivingEntity entity,ArmorMaterial.Layer layer,int index,int fallback){
                    int color=active.material(index);
                    var dye=stack.get(DataComponents.DYED_COLOR);
                    if(index==0&&dye!=null)color=0xff000000|dye.rgb();
                    return color;
                }
            },tier.equals("aegis")?AegisArmor.item("aegis_"+kind):tier.equals("kotomyaf")?KotomyafArmor.item("kotomyaf_"+kind):RealmArmaments.item(tier+"_"+kind));
        }
    }
}
