package io.github.beabfc.teamcmd.mixin;

import io.github.beabfc.teamcmd.TeamUtil;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerChatMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Inject(at = @At("HEAD"), method = "onChatMessage", cancellable = true)
    public final void onChatMessage(ChatMessageC2SPacket packet, CallbackInfo ci) {
        UUID uuid = player.getUuid();
        boolean guildChatToggledOn = TeamUtil.guildChatToggleMap.computeIfAbsent(uuid, u -> false);

        if (guildChatToggledOn) {
            MutableText text = TeamUtil.getGuildChatFormat(player, packet.chatMessage());
            TeamUtil.sendToTeammates(player, text);
            player.sendMessage(text.formatted(player.getScoreboardTeam().getColor()));
            ci.cancel();
        }

    }

}
