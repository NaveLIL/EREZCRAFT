package pro.erez.interstice.gear;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pro.erez.interstice.Interstice;

/** Native tool subclasses; bounded extra work uses ordinary server interaction and loot paths. */
@EventBusSubscriber(modid=Interstice.ID)
public final class RealmWorktools {
    public static final String MODE="interstice_work_area";
    public static final int MAX_LOGS=16;
    public static final float SILVER_RESONANCE=1.25F;
    public enum Kind { PICKAXE, AXE, SHOVEL, HOE }
    public interface Worktool { boolean steel(); Kind kind(); }
    public static final TagKey<Block> RESONANT=tag("riftsilver_resonant_materials"),
            EXCAVATION=tag("excavation_stone"), SOIL=tag("excavation_soil"), LOGS=tag("felling_logs"), TILLABLE=tag("area_tillable");
    private static TagKey<Block> tag(String name){return TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath(Interstice.ID,name));}
    private record Face(BlockPos pos,Direction face,long tick){}
    private record Batch(ServerPlayer player,ServerLevel level,ItemStack tool,BlockPos origin,BlockState original,Direction.Axis axis){}
    private static final Map<ServerPlayer,Face> FACES=new WeakHashMap<>();
    private static final Map<MinecraftServer,Map<UUID,Batch>> QUEUED=new WeakHashMap<>();
    private static final Set<UUID> WORKING=new HashSet<>();
    private RealmWorktools(){}

    public static boolean area(ItemStack stack){return stack.getItem() instanceof Worktool tool&&tool.steel()
            &&stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getBoolean(MODE);}
    public static void setArea(ItemStack stack,boolean enabled){
        if(!(stack.getItem() instanceof Worktool tool)||!tool.steel())return;
        var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(enabled)data.putBoolean(MODE,true);else data.remove(MODE);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
    }
    private static InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(!(stack.getItem() instanceof Worktool tool)||!tool.steel()||!player.isShiftKeyDown())return InteractionResultHolder.pass(stack);
        if(!level.isClientSide){setArea(stack,!area(stack));player.displayClientMessage(Component.translatable("message.interstice.worktool.mode",mode(stack)),true);}
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    public static Component mode(ItemStack stack){
        var tool=(Worktool)stack.getItem();return Component.translatable("worktool.interstice."+(area(stack)?tool.kind().name().toLowerCase(Locale.ROOT):"single"));
    }
    private static void tooltip(ItemStack stack,List<Component> lines){
        var tool=(Worktool)stack.getItem();
        if(tool.steel()){
            lines.add(Component.translatable("tooltip.interstice.worktool.mode",mode(stack)));
            lines.add(Component.translatable("tooltip.interstice.worktool.switch"));
            lines.add(Component.translatable("tooltip.interstice.worktool."+tool.kind().name().toLowerCase(Locale.ROOT)));
        }else lines.add(Component.translatable("tooltip.interstice.worktool.resonance"));
    }
    private static float speed(ItemStack stack,BlockState state,float base,boolean steel){
        return !steel&&base>1&&state.is(RESONANT)&&stack.isCorrectToolForDrops(state)?base*SILVER_RESONANCE:base;
    }
    @SubscribeEvent public static void face(PlayerInteractEvent.LeftClickBlock event){
        if(event.getEntity() instanceof ServerPlayer player&&player.getMainHandItem().getItem() instanceof Worktool)
            FACES.put(player,new Face(event.getPos().immutable(),event.getFace(),player.serverLevel().getGameTime()));
    }
    /** mineBlock runs BEFORE actual removal in 1.21.1. Defer until removal can be verified. */
    private static void mined(ItemStack stack,Level level,BlockState state,BlockPos pos,LivingEntity entity){
        if(!(entity instanceof ServerPlayer player)||!area(stack)||WORKING.contains(player.getUUID())||player.isShiftKeyDown()
                ||player.gameMode.getGameModeForPlayer()!=GameType.SURVIVAL||player.getMainHandItem()!=stack||!player.canInteractWithBlock(pos,0)||state.getDestroySpeed(level,pos)<=0)return;
        var kind=((Worktool)stack.getItem()).kind();
        if(!eligible(kind,state))return;
        var face=FACES.remove(player);Direction.Axis axis;
        if(face!=null&&face.face()!=null&&face.pos().equals(pos)&&level.getGameTime()-face.tick()<200)axis=face.face().getAxis();
        else{var look=player.getLookAngle();axis=Direction.getNearest(look.x,look.y,look.z).getAxis();}
        QUEUED.computeIfAbsent(player.server,key->new LinkedHashMap<>()).put(player.getUUID(),new Batch(player,player.serverLevel(),stack,pos.immutable(),state,axis));
    }
    private static boolean eligible(Kind kind,BlockState state){
        return switch(kind){case PICKAXE->state.is(EXCAVATION);case SHOVEL->state.is(SOIL);case AXE->state.is(LOGS);case HOE->false;};
    }
    public static List<BlockPos> plane(BlockPos center,Direction.Axis normal){
        var result=new ArrayList<BlockPos>(8);
        for(int u=-1;u<=1;u++)for(int v=-1;v<=1;v++)if(u!=0||v!=0)
            result.add(switch(normal){case X->center.offset(0,u,v);case Y->center.offset(u,0,v);case Z->center.offset(u,v,0);});
        return result;
    }
    /** Shared eligibility for the native 3x3 operation and the visor's read-only preview.
     * Per-block cancellable protection hooks still run only on the server when work happens. */
    public static boolean candidate(Kind kind,Level level,BlockPos pos,ItemStack tool){
        boolean loaded=level instanceof ServerLevel server?server.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null:level.hasChunkAt(pos);
        if(!loaded||!level.getWorldBorder().isWithinBounds(pos)||level.getBlockEntity(pos)!=null)return false;
        var state=level.getBlockState(pos);if(kind==Kind.HOE)return state.is(TILLABLE)&&level.getBlockState(pos.above()).isAir();
        return eligible(kind,state)&&state.getDestroySpeed(level,pos)>0&&tool.isCorrectToolForDrops(state)&&tool.getDestroySpeed(state)>1;
    }
    public static List<BlockPos> preview(Player player,ItemStack tool,BlockPos origin,Direction face){
        if(!area(tool)||player.isShiftKeyDown()||player.isCreative()||player.isSpectator()||!player.isAlive())return List.of();
        var kind=((Worktool)tool.getItem()).kind();if(kind==Kind.AXE||!candidate(kind,player.level(),origin,tool))return List.of();
        var all=new ArrayList<BlockPos>();all.add(origin.immutable());all.addAll(plane(origin,kind==Kind.HOE?Direction.Axis.Y:face.getAxis()));
        return all.stream().filter(p->player.canInteractWithBlock(p,0)&&candidate(kind,player.level(),p,tool)).toList();
    }
    private static boolean loaded(ServerLevel level,BlockPos pos){return level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null;}
    private static boolean permitted(ServerPlayer player,ServerLevel level,BlockPos pos){
        return permitted(player,level,pos,0);
    }
    private static boolean permitted(ServerPlayer player,ServerLevel level,BlockPos pos,double extraReach){
        return loaded(level,pos)&&level.getWorldBorder().isWithinBounds(pos)&&player.canInteractWithBlock(pos,extraReach)&&level.mayInteract(player,pos)
                &&!player.blockActionRestricted(level,pos,player.gameMode.getGameModeForPlayer())&&level.getBlockEntity(pos)==null;
    }
    private static boolean ready(Batch batch){
        var p=batch.player();return p.serverLevel()==batch.level()&&p.isAlive()&&!p.isRemoved()&&p.gameMode.getGameModeForPlayer()==GameType.SURVIVAL
                &&p.getMainHandItem()==batch.tool()&&!batch.tool().isEmpty()&&area(batch.tool())&&!p.isShiftKeyDown();
    }
    private static boolean extra(Batch batch,BlockPos pos){
        var player=batch.player();var level=player.serverLevel();
        if(!ready(batch))return false;
        var kind=((Worktool)batch.tool().getItem()).kind();
        // Felling propagates up the connected trunk; only the initial cut needs ordinary reach.
        // All subsequent logs are bounded relative to that cut and still pass individual protection hooks.
        if(!permitted(player,level,pos,kind==Kind.AXE?12:0))return false;
        var state=level.getBlockState(pos);
        if(!candidate(kind,level,pos,batch.tool()))return false;
        if(kind==Kind.AXE&&!state.is(batch.original().getBlock()))return false;
        // destroyBlock posts normal cancellable BlockEvent.BreakEvent and performs vanilla loot/durability.
        if(!player.gameMode.destroyBlock(pos))return false;
        return !level.getBlockState(pos).equals(state);
    }
    private static void perform(Batch batch){
        var p=batch.player();var level=p.serverLevel();
        if(!ready(batch)||!loaded(level,batch.origin()))return;
        // A hook refusing removal must not cause extra blocks to be destroyed.
        var now=level.getBlockState(batch.origin());if(!now.isAir()&&!now.liquid())return;
        if(!WORKING.add(p.getUUID()))return;
        try{
            if(((Worktool)batch.tool().getItem()).kind()!=Kind.AXE){
                for(var pos:plane(batch.origin(),batch.axis()))extra(batch,pos);
            }else{
                var visited=new HashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();visited.add(batch.origin());
                for(var d:Direction.values())queue.add(batch.origin().relative(d));int removed=1,examined=0;
                while(!queue.isEmpty()&&removed<MAX_LOGS&&examined++<128){
                    var pos=queue.removeFirst();if(!visited.add(pos)||pos.getY()<batch.origin().getY()||pos.getY()>batch.origin().getY()+15
                            ||Math.abs(pos.getX()-batch.origin().getX())>4||Math.abs(pos.getZ()-batch.origin().getZ())>4)continue;
                    if(extra(batch,pos)){removed++;for(var d:Direction.values())queue.add(pos.relative(d));}
                }
            }
        }finally{WORKING.remove(p.getUUID());}
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        var jobs=QUEUED.remove(event.getServer());if(jobs==null)return;
        for(var batch:jobs.values())perform(batch);
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event){QUEUED.remove(event.getServer());FACES.keySet().removeIf(p->p.server==event.getServer());}

    private static InteractionResult till(Hoe item,UseOnContext context){
        var player=context.getPlayer();var stack=context.getItemInHand();var level=context.getLevel();var origin=context.getClickedPos();
        boolean batch=area(stack)&&player instanceof ServerPlayer&&!player.isShiftKeyDown()&&!WORKING.contains(player.getUUID())
                &&((ServerPlayer)player).gameMode.getGameModeForPlayer()==GameType.SURVIVAL&&level.getBlockState(origin).is(TILLABLE);
        var before=level.getBlockState(origin);var result=item.single(context);
        if(!batch||!result.consumesAction()||!(level instanceof ServerLevel server)||server.getBlockState(origin).equals(before))return result;
        var p=(ServerPlayer)player;if(!WORKING.add(p.getUUID()))return result;
        try{
            for(var pos:plane(origin,Direction.Axis.Y)){
                if(stack.isEmpty()||p.getItemInHand(context.getHand())!=stack)break;
                if(!permitted(p,server,pos)||!candidate(Kind.HOE,server,pos,stack))continue;
                var hit=new BlockHitResult(Vec3.atCenterOf(pos).add(0,.5,0),Direction.UP,pos,false);
                var next=new UseOnContext(p,context.getHand(),hit);
                if(server.getBlockState(pos).getToolModifiedState(next,ItemAbilities.HOE_TILL,true)==null)continue;
                // Re-enter the ordinary pipeline so per-block RightClickBlock protection can cancel it.
                p.gameMode.useItemOn(p,server,stack,context.getHand(),hit);
            }
        }finally{WORKING.remove(p.getUUID());}
        return result;
    }

    public static final class Pickaxe extends PickaxeItem implements Worktool {
        private final boolean steel;public Pickaxe(Tier tier,Properties properties,boolean steel){super(tier,properties);this.steel=steel;}
        public boolean steel(){return steel;}public Kind kind(){return Kind.PICKAXE;}
        @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand h){return RealmWorktools.use(l,p,h);}
        @Override public float getDestroySpeed(ItemStack s,BlockState b){return speed(s,b,super.getDestroySpeed(s,b),steel);}
        @Override public boolean mineBlock(ItemStack s,Level l,BlockState b,BlockPos p,LivingEntity e){boolean r=super.mineBlock(s,l,b,p,e);mined(s,l,b,p,e);return r;}
        @Override public void appendHoverText(ItemStack s,TooltipContext c,List<Component> t,TooltipFlag f){tooltip(s,t);}
    }
    public static final class Axe extends AxeItem implements Worktool {
        private final boolean steel;public Axe(Tier tier,Properties properties,boolean steel){super(tier,properties);this.steel=steel;}
        public boolean steel(){return steel;}public Kind kind(){return Kind.AXE;}
        @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand h){return RealmWorktools.use(l,p,h);}
        @Override public float getDestroySpeed(ItemStack s,BlockState b){return speed(s,b,super.getDestroySpeed(s,b),steel);}
        @Override public boolean mineBlock(ItemStack s,Level l,BlockState b,BlockPos p,LivingEntity e){boolean r=super.mineBlock(s,l,b,p,e);mined(s,l,b,p,e);return r;}
        @Override public void appendHoverText(ItemStack s,TooltipContext c,List<Component> t,TooltipFlag f){tooltip(s,t);}
    }
    public static final class Shovel extends ShovelItem implements Worktool {
        private final boolean steel;public Shovel(Tier tier,Properties properties,boolean steel){super(tier,properties);this.steel=steel;}
        public boolean steel(){return steel;}public Kind kind(){return Kind.SHOVEL;}
        @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand h){return RealmWorktools.use(l,p,h);}
        @Override public float getDestroySpeed(ItemStack s,BlockState b){return speed(s,b,super.getDestroySpeed(s,b),steel);}
        @Override public boolean mineBlock(ItemStack s,Level l,BlockState b,BlockPos p,LivingEntity e){boolean r=super.mineBlock(s,l,b,p,e);mined(s,l,b,p,e);return r;}
        @Override public void appendHoverText(ItemStack s,TooltipContext c,List<Component> t,TooltipFlag f){tooltip(s,t);}
    }
    public static final class Hoe extends HoeItem implements Worktool {
        private final boolean steel;public Hoe(Tier tier,Properties properties,boolean steel){super(tier,properties);this.steel=steel;}
        public boolean steel(){return steel;}public Kind kind(){return Kind.HOE;}
        @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand h){return RealmWorktools.use(l,p,h);}
        @Override public float getDestroySpeed(ItemStack s,BlockState b){return speed(s,b,super.getDestroySpeed(s,b),steel);}
        public InteractionResult single(UseOnContext c){return super.useOn(c);}
        @Override public InteractionResult useOn(UseOnContext c){return till(this,c);}
        @Override public void appendHoverText(ItemStack s,TooltipContext c,List<Component> t,TooltipFlag f){tooltip(s,t);}
    }
}
