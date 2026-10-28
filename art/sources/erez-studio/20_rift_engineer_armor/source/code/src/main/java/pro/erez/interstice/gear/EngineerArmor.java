package pro.erez.interstice.gear;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import pro.erez.interstice.Interstice;

/** Visual acceptance items. Existing iron baseline only; no recipe or special gameplay abilities yet. */
public final class EngineerArmor {
    public static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath(Interstice.ID,"textures/models/armor/engineer.png");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,Interstice.ID);
    public static final Map<String,DeferredHolder<Item,Piece>> PIECES=new LinkedHashMap<>();
    static{for(var type:List.of(ArmorItem.Type.HELMET,ArmorItem.Type.CHESTPLATE,ArmorItem.Type.LEGGINGS,ArmorItem.Type.BOOTS))
        PIECES.put(type.getName(),ITEMS.register("engineer_"+type.getName(),()->new Piece(type)));}
    private EngineerArmor(){}
    public static void register(IEventBus bus){ITEMS.register(bus);}
    public static Item item(String kind){return PIECES.get(kind).get();}
    public static void displayItems(CreativeModeTab.Output output){PIECES.values().forEach(p->output.accept(p.get()));}
    public static boolean worn(net.minecraft.world.entity.LivingEntity entity,EquipmentSlot slot){
        return entity.getItemBySlot(slot).getItem() instanceof Piece p&&p.getEquipmentSlot()==slot;
    }
    public static final class Piece extends ArmorItem{
        Piece(Type type){super(ArmorMaterials.IRON,type,new Item.Properties().durability(type.getDurability(15)));}
        @Override public ResourceLocation getArmorTexture(ItemStack stack,net.minecraft.world.entity.Entity entity,EquipmentSlot slot,ArmorMaterial.Layer layer,boolean inner){return TEXTURE;}
    }
}
