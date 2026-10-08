package pro.erez.interstice.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real server players with supported in-memory NeoForge test connections. */
public final class TestPlayers {
    public static ServerPlayer create(GameTestHelper h, BlockPos local, GameType mode) {
        return connect(h, local, mode, UUID.randomUUID());
    }
    public static ServerPlayer reconnect(GameTestHelper h, UUID id) { return connect(h, null, GameType.SURVIVAL, id); }
    private static ServerPlayer connect(GameTestHelper h, BlockPos local, GameType mode, UUID id) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(id, "exp-" + id.toString().substring(0, 8)), false);
        var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection); NetworkRegistry.configureMockConnection(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie); player.setGameMode(mode);
        if (local != null) { BlockPos pos = h.absolutePos(local); player.teleportTo(h.getLevel(), pos.getX() + .5, pos.getY() + .01, pos.getZ() + .5, java.util.Set.of(), 0, 0); }
        player.hasChangedDimension();
        return player;
    }
    public static void remove(ServerPlayer player) { player.server.getPlayerList().remove(player); }
}
