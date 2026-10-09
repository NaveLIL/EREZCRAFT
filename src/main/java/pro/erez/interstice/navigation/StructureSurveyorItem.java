package pro.erez.interstice.navigation;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import pro.erez.interstice.worldgen.RealmStructuresV5;

/** A saved survey coordinate is a memory, not a ticket or a claim that an old ruin still exists. */
public final class StructureSurveyorItem extends Item {
    public static final String MARK="interstice_structure_survey";
    public static final String JOB="interstice_structure_survey_job";
    public record Mark(ResourceLocation dimension,BlockPos position,RealmStructuresV5.Family family,long surveyedAt) {}
    public StructureSurveyorItem(){super(new Properties().durability(32));}
    public static Mark mark(ItemStack stack){
        var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound(MARK);
        var dimension=ResourceLocation.tryParse(tag.getString("Dimension"));if(dimension==null||!tag.contains("Position"))return null;
        try{return new Mark(dimension,BlockPos.of(tag.getLong("Position")),RealmStructuresV5.Family.valueOf(tag.getString("Family")),tag.getLong("SurveyedAt"));}
        catch(IllegalArgumentException malformed){return null;}
    }
    static void store(ItemStack stack,StructureSearchJobs.Result found,ResourceLocation dimension,long now){
        var all=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();var tag=new CompoundTag();
        tag.putString("Dimension",dimension.toString());tag.putLong("Position",found.position().asLong());tag.putString("Family",found.family().name());
        tag.putString("Template",found.template().toString());tag.putLong("SurveyedAt",now);all.put(MARK,tag);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(all));
    }
    static Component family(RealmStructuresV5.Family family){return Component.translatable("navigation.interstice.family."+family.name().toLowerCase(java.util.Locale.ROOT));}
    public static Component direction(BlockPos from,BlockPos to){
        double angle=Math.atan2(to.getX()-from.getX(),-(to.getZ()-from.getZ()));
        int octant=Math.floorMod((int)Math.round(angle/(Math.PI/4)),8);
        return Component.translatable("navigation.interstice.direction."+new String[]{"n","ne","e","se","s","sw","w","nw"}[octant]);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(!level.isClientSide&&player instanceof ServerPlayer server){
            if(server.isShiftKeyDown()){
                var mark=mark(stack);
                if(mark==null)server.displayClientMessage(Component.translatable("message.interstice.survey.no_mark"),true);
                else if(!mark.dimension().equals(level.dimension().location()))server.displayClientMessage(Component.translatable("message.interstice.survey.other_dimension",mark.dimension().toString()),true);
                else server.displayClientMessage(Component.translatable("message.interstice.survey.saved",family(mark.family()),mark.position().getX(),mark.position().getY(),mark.position().getZ(),direction(server.blockPosition(),mark.position())),false);
                return InteractionResultHolder.success(stack);
            }
            if(!StructureSearchJobs.begin(server,hand))return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("tooltip.interstice.structure_surveyor",StructureSearchJobs.RADIUS,StructureSearchJobs.FULL_BUDGET));
        var mark=mark(stack);if(mark!=null)lines.add(Component.translatable("tooltip.interstice.survey_mark",family(mark.family()),mark.position().getX(),mark.position().getY(),mark.position().getZ()));
    }
}
