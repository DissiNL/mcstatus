package de.maxhenkel.status.mixin;

import de.maxhenkel.status.Status;
import de.maxhenkel.status.net.NetManager;
import de.maxhenkel.status.net.PlayerStatePacket;
import de.maxhenkel.status.playerstate.PlayerState;
import java.util.function.BooleanSupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class PlayerSleepDailyReset {

    private boolean lastTickIsDay = true;

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick(BooleanSupplier booleanSupplier, CallbackInfo info) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (level.isDay()) {
            if (!lastTickIsDay) {
                resetSleepCycle(level.getServer());
            }
            lastTickIsDay = true;
        } else {
            lastTickIsDay = false;
        }
    }

    private void resetSleepCycle(MinecraftServer server) {
        Status.STATE_MANAGER.getStates()
            .stream()
            .filter(PlayerState::isNoSleep)
            .forEach(state -> {
                // Reset after night.
                state.setNoSleep(false);
                broadcastState(server, state);
                server.getPlayerList().getPlayers().stream()
                    // if connected....
                    .filter(serverPlayer -> serverPlayer.getUUID().equals(state.getPlayer()))
                    // send message
                    .forEach(serverPlayer -> {
                        serverPlayer.connection.send(new ClientboundSetTitleTextPacket(
                            Component.literal(Status.SERVER_CONFIG.resetNoSleepCycleTitle.get())));
                        serverPlayer.connection.send(new ClientboundSetSubtitleTextPacket(
                            Component.literal(Status.SERVER_CONFIG.resetNoSleepCycle.get())));
                    });
            });
    }

    private void broadcastState(MinecraftServer server, PlayerState state) {
        PlayerStatePacket packet = new PlayerStatePacket(state);
        server.getPlayerList().getPlayers().forEach(p -> NetManager.sendToClient(p, packet));
    }

}
