package pro.erez.interstice.test;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import pro.erez.interstice.tether.*;

@GameTestHolder("interstice_tethers") @PrefixGameTestTemplate(false)
public final class WinchGameTests {
    private static final BlockPos ANCHOR=new BlockPos(6,2,6);
    private static WinchBlockEntity node(GameTestHelper h){
        for(int x=1;x<14;x++)for(int z=1;z<14;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        h.setBlock(ANCHOR,RiftTethers.WINCH.get());return (WinchBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(ANCHOR));
    }
    private static ServerPlayer player(GameTestHelper h){return TestPlayers.create(h,new BlockPos(6,2,3),GameType.SURVIVAL);}
    private static boolean attach(GameTestHelper h,ServerPlayer p,int length){return WinchLinks.attach(p,h.getLevel(),h.absolutePos(ANCHOR),length);}
    @GameTest(template="empty",timeoutTicks=100)
    public static void spoolNativeUseOwnsOneLinkAndItemCopiesCarryNoAttachment(GameTestHelper h){
        var node=node(h);var p=player(h);
        try{var spool=new ItemStack(RiftTethers.TETHER_SPOOL.get());p.setItemInHand(InteractionHand.MAIN_HAND,spool);var pos=h.absolutePos(ANCHOR);
            p.gameMode.useItemOn(p,h.getLevel(),spool,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
            h.assertTrue(WinchLinks.link(p)!=null&&WinchLinks.link(p).length()==32&&node.hooks().size()==1&&spool.getDamageValue()==1,"Actual spool interaction did not create exactly one paid self-link");
            p.gameMode.useItemOn(p,h.getLevel(),spool,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
            h.assertTrue(node.hooks().size()==1&&spool.getDamageValue()==1&&spool.copy().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().isEmpty(),"Repeated click or copied spool duplicates a saved attachment");h.succeed();
        }finally{WinchLinks.detach(p);TestPlayers.remove(p);}
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void actualTautPlayerReceivesBoundedAccelerationWithoutPositionTeleport(GameTestHelper h){
        var node=node(h);var p=player(h);
        try{h.assertTrue(attach(h,p,8),"Fixture cannot attach a near local player");
            // GameTest encases its bounds in barriers. A diagonal endpoint stays inside this
            // chamber while genuinely exceeding eight blocks, rather than crossing its wall.
            var at=h.absolutePos(new BlockPos(13,2,1));p.teleportTo(h.getLevel(),at.getX()+.5,at.getY(),at.getZ()+.5,Set.of(),0,0);p.hasChangedDimension();
            var before=p.position();p.setKnownMovement(Vec3.ZERO);var source=WinchLinks.source(node);var end=WinchLinks.endpoint(p);var linkBefore=WinchLinks.link(p);boolean clear=WinchLinks.clear(h.getLevel(),source,end,p);node.process();var impulse=p.getDeltaMovement();
            h.assertTrue(clear&&source.distanceTo(end)>8.05,"Prepared diagonal endpoint is not a real unobstructed taut route");
            h.assertTrue(WinchLinks.link(p)!=null&&impulse.length()>0&&impulse.length()<=WinchLinks.MAX_ACCELERATION+1e-8&&p.position().equals(before),"Taut link exceeds force bound/no pull/teleport; source="+source+", end="+end+", distance="+source.distanceTo(end)+", clear="+clear+", facing="+node.getBlockState().getValue(WinchBlock.FACING)+", hook="+node.hook(p.getUUID())+", linkBefore="+linkBefore+", linkAfter="+WinchLinks.link(p)+", known="+p.getKnownMovement()+", delta="+impulse+", displacement="+p.position().subtract(before));
            h.assertTrue(impulse.dot(WinchLinks.source(node).subtract(WinchLinks.endpoint(p)))>0,"Rope pushes away from its real anchor");h.succeed();
        }finally{WinchLinks.detach(p);TestPlayers.remove(p);}
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void slackLeavesVelocityFallDistanceAndFlightAbilitiesUntouched(GameTestHelper h){
        var node=node(h);var p=player(h);
        try{h.assertTrue(attach(h,p,24),"Slack fixture cannot attach");var velocity=new Vec3(.1,-.2,.03);p.setKnownMovement(velocity);p.setDeltaMovement(velocity);p.fallDistance=17;boolean mayFly=p.getAbilities().mayfly;node.process();
            h.assertTrue(p.getDeltaMovement().equals(velocity)&&p.fallDistance==17&&p.getAbilities().mayfly==mayFly&&!p.getAbilities().flying,"Slack winch rewrites physics or grants flight");
            h.assertTrue(WinchLinks.acceleration(Vec3.ZERO,new Vec3(0,-40,0),new Vec3(0,-9,0),32).length()<=.120000001,"Extreme existing fall makes force unbounded");h.succeed();
        }finally{WinchLinks.detach(p);TestPlayers.remove(p);}
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void sourceIsOutsideOwnShapeObstructionAndBreakingDetach(GameTestHelper h){
        var node=node(h);var p=player(h);
        try{h.assertTrue(WinchLinks.clear(h.getLevel(),WinchLinks.source(node),WinchLinks.endpoint(p),p)&&attach(h,p,24),"Own anchor self-intersects its rope source");
            h.setBlock(new BlockPos(6,2,4),Blocks.STONE);node.process();h.assertTrue(WinchLinks.link(p)==null&&node.hooks().isEmpty(),"Opaque obstruction does not release the local link");
            h.setBlock(new BlockPos(6,2,4),Blocks.AIR);h.assertTrue(attach(h,p,24),"Clear path cannot reattach");h.setBlock(ANCHOR,Blocks.AIR);
            h.assertTrue(WinchLinks.link(p)==null&&node.hooks().isEmpty(),"Removed anchor still controls a player");h.succeed();
        }finally{WinchLinks.detach(p);TestPlayers.remove(p);}
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void twoHookCapacityAndOneAnchorPerPlayerRemainAtomic(GameTestHelper h){
        var first=node(h);var a=player(h);var b=player(h);var c=player(h);
        try{h.assertTrue(attach(h,a,24)&&attach(h,b,24)&&!attach(h,c,24)&&first.hooks().size()==2,"Anchor creates more than two hooks");
            h.setBlock(ANCHOR.west(2),RiftTethers.WINCH.get());var next=(WinchBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(ANCHOR.west(2)));
            h.assertTrue(WinchLinks.attach(a,h.getLevel(),next.getBlockPos(),24)&&first.hook(a.getUUID())==null&&next.hooks().size()==1&&WinchLinks.matches(a,next),"Moving to another anchor leaves a duplicate old hook");h.succeed();
        }finally{WinchLinks.detach(a);WinchLinks.detach(b);WinchLinks.detach(c);TestPlayers.remove(a);TestPlayers.remove(b);TestPlayers.remove(c);}
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void blockAndPlayerSaveRoundTripRetainsMatchingIdentityAndLength(GameTestHelper h){
        var node=node(h);var p=player(h);
        try{h.assertTrue(attach(h,p,24),"Save fixture cannot attach");var encoded=node.saveWithoutMetadata(h.getLevel().registryAccess());var restored=new WinchBlockEntity(node.getBlockPos(),node.getBlockState());restored.loadWithComponents(encoded,h.getLevel().registryAccess());h.getLevel().setBlockEntity(restored);
            var savedPlayer=new CompoundTag();p.saveWithoutId(savedPlayer);
            h.assertTrue(restored.anchorId().equals(node.anchorId())&&restored.hooks().size()==1&&restored.hook(p.getUUID()).length()==24&&savedPlayer.toString().contains(WinchLinks.KEY)&&WinchLinks.matches(p,restored),"Cold-compatible identities/length or player serialized state lost");
            restored.process();h.assertTrue(WinchLinks.link(p)!=null&&restored.hooks().size()==1,"Loading duplicates or discards a still-valid owned hook");h.succeed();
        }finally{WinchLinks.detach(p);TestPlayers.remove(p);}
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void unsupportedSlackActuallyWarnsAndReleasesWithoutChangingFlyingChecks(GameTestHelper h){
        var node=node(h);var p=player(h);h.assertTrue(attach(h,p,32),"Air safety fixture cannot attach");var at=h.absolutePos(ANCHOR.above(10));p.teleportTo(h.getLevel(),at.getX()+.5,at.getY(),at.getZ()+.5,Set.of(),0,0);p.hasChangedDimension();p.setKnownMovement(Vec3.ZERO);
        h.runAtTickTime(43,()->{h.assertTrue(node.hook(p.getUUID())!=null&&node.hook(p.getUUID()).airTicks()>=40&&!p.getAbilities().mayfly&&!p.getAbilities().flying,"Real block ticks neither accumulate unsupported slack grace nor preserve flight flags");});
        h.runAtTickTime(73,()->{try{h.assertTrue(WinchLinks.link(p)==null&&node.hooks().isEmpty()&&!p.getAbilities().mayfly,"Bounded hanging safety can silently persist beyond sixty ticks");h.succeed();}finally{TestPlayers.remove(p);}});
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void dimensionChangeAndActualDeathClearPlayerAndAnchor(GameTestHelper h){
        var node=node(h);var p=player(h);
        try{h.assertTrue(attach(h,p,24),"Dimension fixture cannot attach");var other=h.getLevel().getServer().getLevel(Level.NETHER);h.assertTrue(other!=null,"Disposable server lacks its ordinary Nether");
            p.teleportTo(other,0,90,0,Set.of(),0,0);p.hasChangedDimension();h.assertTrue(WinchLinks.link(p)==null&&node.hooks().isEmpty(),"Cross-dimension link retains force or ownership");
            var at=h.absolutePos(new BlockPos(6,2,3));p.teleportTo(h.getLevel(),at.getX()+.5,at.getY(),at.getZ()+.5,Set.of(),0,0);p.hasChangedDimension();h.assertTrue(attach(h,p,24),"Returned fixture cannot attach");p.setHealth(0);p.die(h.getLevel().damageSources().genericKill());
            node.process();h.assertTrue(WinchLinks.link(p)==null&&node.hooks().isEmpty(),"Dead player retains an anchor hook");h.succeed();
        }finally{WinchLinks.detach(p);TestPlayers.remove(p);}
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void forgedRemoteLengthAndVisualPacketsCannotCreateServerAttachments(GameTestHelper h){
        var node=node(h);var p=player(h);
        try{h.assertTrue(!attach(h,p,0)&&!attach(h,p,33)&&!WinchLinks.attach(p,h.getLevel(),h.absolutePos(ANCHOR.east(1000)),24)&&WinchLinks.link(p)==null&&node.hooks().isEmpty(),"Forged length/remote position can own an anchor");
            var packet=new TetherNetworking.State(p.getId(),p.getUUID(),h.getLevel().dimension().location(),node.getBlockPos(),Direction.NORTH.get3DDataValue(),24,true);
            var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
            try{TetherNetworking.State.STREAM.encode(buffer,packet);var decoded=TetherNetworking.State.STREAM.decode(buffer);h.assertTrue(decoded.equals(packet),"Native visual codec loses its bounded link fields");ClientTetherState.apply(decoded);
                h.assertTrue(WinchLinks.link(p)==null&&node.hooks().isEmpty(),"Client render cache mutates authoritative server link");ClientTetherState.apply(new TetherNetworking.State(p.getId(),p.getUUID(),packet.dimension(),packet.anchor(),2,1000,true));h.assertTrue(ClientTetherState.links().stream().noneMatch(s->s.length()>32),"Unbounded visual state bypasses client rendering limits");
            }finally{buffer.release();ClientTetherState.clear();}h.succeed();
        }finally{WinchLinks.detach(p);TestPlayers.remove(p);}
    }
}
