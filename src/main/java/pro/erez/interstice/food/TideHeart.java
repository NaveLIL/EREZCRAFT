package pro.erez.interstice.food;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import pro.erez.interstice.Interstice;

/** First realm food: clear timed benefit, delayed weakness/visibility and no repeat-dose timer reset. */
public final class TideHeart {
    public static final int VISION_TICKS=800, AFTERTASTE_TICKS=900;
    private static final String PENDING="interstice.tide_heart_pending";
    private static final DeferredRegister<MobEffect> EFFECTS=DeferredRegister.create(BuiltInRegistries.MOB_EFFECT,Interstice.ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(BuiltInRegistries.ITEM,Interstice.ID);
    public static final DeferredHolder<MobEffect,MobEffect> REACTION=EFFECTS.register("tide_heart_reaction",()->new MobEffect(MobEffectCategory.NEUTRAL,0xd9b78a){});
    public static final DeferredHolder<Item,Item> FRUIT=ITEMS.register("tide_heart",()->new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(6).saturationModifier(.4F).usingConvertsTo(pro.erez.interstice.worldgen.GardenMaterials.CROWN_SAPLING.get()).build())){
        @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity consumer){
            var result=super.finishUsingItem(stack,level,consumer);
            if(!level.isClientSide)beginReaction(consumer);
            return result;
        }
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> text,TooltipFlag flags){
            text.add(Component.translatable("tooltip.interstice.tide_heart.food"));
            text.add(Component.translatable("tooltip.interstice.tide_heart.benefit"));
            text.add(Component.translatable("tooltip.interstice.tide_heart.cost"));
            text.add(Component.translatable("tooltip.interstice.tide_heart.repeat"));
        }
    });
    private TideHeart(){}
    public static void register(IEventBus bus){EFFECTS.register(bus);ITEMS.register(bus);}
    public static void beginReaction(LivingEntity entity){
        if(entity.hasEffect(REACTION))return;
        entity.addEffect(new MobEffectInstance(REACTION,VISION_TICKS+AFTERTASTE_TICKS,0,false,false,true));
        entity.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,VISION_TICKS,0,false,false,true));
        entity.getPersistentData().putBoolean(PENDING,true);
    }
    /** Called after vanilla's effect iteration, so activation cannot invalidate its iterator. */
    public static void tick(EntityTickEvent.Post event){
        if(event.getEntity() instanceof LivingEntity living&&!(living instanceof net.minecraft.world.entity.player.Player))process(living);
    }
    public static void playerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event){process(event.getEntity());}
    private static void process(LivingEntity living){
        if(living.level().isClientSide||!living.isAlive())return;
        var reaction=living.getEffect(REACTION);
        if(reaction==null){living.getPersistentData().remove(PENDING);return;}
        if(reaction.getDuration()<=AFTERTASTE_TICKS&&living.getPersistentData().getBoolean(PENDING)){
            living.getPersistentData().putBoolean(PENDING,false);
            int duration=Math.max(1,reaction.getDuration());
            living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,duration,0,false,true,true));
            living.addEffect(new MobEffectInstance(MobEffects.GLOWING,duration,0,false,false,true));
            if(living instanceof net.minecraft.server.level.ServerPlayer player)player.displayClientMessage(Component.translatable("message.interstice.tide_heart.aftertaste"),true);
        }
    }
    /** Future realm creatures can consume this server-side signal; no current mob AI is altered. */
    public static boolean isMarkedPrey(LivingEntity entity){
        var reaction=entity.getEffect(REACTION);
        return reaction!=null&&reaction.getDuration()<=AFTERTASTE_TICKS&&entity.hasEffect(MobEffects.GLOWING);
    }
}
