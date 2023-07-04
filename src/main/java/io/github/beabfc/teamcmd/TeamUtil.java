package io.github.beabfc.teamcmd;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

public abstract class TeamUtil {
    private static final int TPS = 20;
    private static final int TIMEOUT = 120;
    private static final HashMap<UUID, TeamInvite> inviteMap = new HashMap<>();
    public static final HashMap<UUID, Boolean> guildChatToggleMap = new HashMap<>();

    public static void sendToTeammates(ServerPlayerEntity player, Text message) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        for (ServerPlayerEntity otherPlayer : server.getPlayerManager().getPlayerList()) {
            if (otherPlayer.isTeammate(player) && !otherPlayer.equals(player)) {
                otherPlayer.sendMessage(message);
            }
        }

    }

    public static void addInvite(ServerPlayerEntity player, String teamName) {
        inviteMap.put(player.getUuid(), new TeamInvite(teamName));
    }

    public static String getInvitedTeam(ServerPlayerEntity player) {
        TeamInvite invite = inviteMap.get(player.getUuid());
        if (invite != null) return invite.getTeamName();
        return null;
    }

    public static void resetInvite(ServerPlayerEntity player) {
        inviteMap.remove(player.getUuid());
    }


    public static void tick(MinecraftServer server) {
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, TeamInvite> entry : inviteMap.entrySet()) {
            TeamInvite invite = entry.getValue();

            if (invite.isExpired()) {
                UUID playerUuid = entry.getKey();
                toRemove.add(playerUuid);
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
                if (player != null) {
                    Team team = player.getScoreboard().getTeam(invite.getTeamName());
                    if (team != null) {
                        player.sendMessage(Text.translatable("commands.teamcmd.invite_expired",
                            team.getFormattedName()));
                    }
                }
            }

            invite.hasTicked();
        }
        for (UUID uuid : toRemove) {
            inviteMap.remove(uuid);
        }
    }

    public static boolean isOwner(ServerPlayerEntity player, Team team) {

        User user = LuckPermsProvider.get().getUserManager().getUser(player.getUuid());
        List<String> metaTeam = user.getCachedData().getMetaData().getMeta().get("guild-owner");

        if (metaTeam == null) return false;
        return metaTeam.get(0).equals(team.getName());
    }

    public static MutableText getGuildChatFormat(ServerPlayerEntity player, String message) {
        MutableText display = player.getDisplayName().copy().formatted(player.getScoreboardTeam().getColor());

        return display.append(Text.of(" » ").copy().formatted(Formatting.DARK_GRAY).append(Text.literal(message)).formatted(player.getScoreboardTeam().getColor()));
    }

    private static class TeamInvite {
        private final String teamName;
        private int remainingTicks;

        public TeamInvite(String teamName) {
            this.remainingTicks = TIMEOUT * TPS;
            this.teamName = teamName;
        }

        public String getTeamName() {
            return this.teamName;
        }

        public boolean isExpired() {
            return this.remainingTicks <= 0;
        }

        public void hasTicked() {
            this.remainingTicks--;
        }
    }
}
