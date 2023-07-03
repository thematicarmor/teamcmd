package io.github.beabfc.teamcmd;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.MetaNode;
import net.minecraft.command.argument.ColorArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.TeamArgumentType;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

import java.util.Collection;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;


public class CommandBuilder {
    private static final SimpleCommandExceptionType DUPLICATE_NAME =
        new SimpleCommandExceptionType(Text.translatable("commands.teamcmd.add.duplicate"));
    private static final SimpleCommandExceptionType NAME_UNCHANGED =
        new SimpleCommandExceptionType(Text.translatable("commands.teamcmd.option.name.unchanged"));
    private static final SimpleCommandExceptionType COLOR_UNCHANGED =
        new SimpleCommandExceptionType(Text.translatable("commands.teamcmd.option.color.unchanged"));
    private static final SimpleCommandExceptionType FRIENDLY_FIRE_ALREADY_ENABLED =
        new SimpleCommandExceptionType(Text.translatable("commands.teamcmd.option.friendlyfire.alreadyEnabled"));
    private static final SimpleCommandExceptionType FRIENDLY_FIRE_ALREADY_DISABLED =
        new SimpleCommandExceptionType(Text.translatable("commands.teamcmd.option.friendlyfire.alreadyDisabled"));
    private static final SimpleCommandExceptionType FRIENDLY_INVISIBLES_ALREADY_ENABLED =
        new SimpleCommandExceptionType(Text.translatable("commands.teamcmd.option.seeFriendlyInvisibles" +
            ".alreadyEnabled"));
    private static final SimpleCommandExceptionType OPTION_SEE_FRIENDLY_INVISIBLES_ALREADY_DISABLED_EXCEPTION =
        new SimpleCommandExceptionType(Text.translatable("commands.teamcmd.option.seeFriendlyInvisibles" +
            ".alreadyDisabled"));
    private static final SimpleCommandExceptionType ALREADY_IN_TEAM =
        new SimpleCommandExceptionType(Text.translatable("commands.teamcmd.fail.in_team"));
    private static final SimpleCommandExceptionType NOT_IN_TEAM =
        new SimpleCommandExceptionType(Text.translatable("commands.teamcmd.fail.no_team"));
    private static final SimpleCommandExceptionType PLAYER_ALREADY_TEAMMATE =
        new SimpleCommandExceptionType(Text.translatable("commands.teamcmd.fail.already_teammate"));
    private static final SimpleCommandExceptionType NOT_INVITED =
        new SimpleCommandExceptionType(Text.translatable("commands.teamcmd.fail.not_invited"));
    private static final SimpleCommandExceptionType DUPLICATE_COLOR =
        new SimpleCommandExceptionType(Text.translatable("commands.teamcmd.fail.duplicate_color"));
    private static final DynamicCommandExceptionType TEAM_NOT_FOUND =
        new DynamicCommandExceptionType(option -> Text.translatable("team.notFound", option));
    private static final SimpleCommandExceptionType NOT_GUILD_OWNER =
            new SimpleCommandExceptionType(Text.translatable("commands.teamcmd.not_guild_owner"));

    private static final int MAX_NUMBER_IN_GUILD = 3;

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> teamCmd = literal(TeamCommand.CONFIG.commandName);
        teamCmd
            .then(literal("create").then(argument("name", StringArgumentType.word()).then(argument("color",
                ColorArgumentType.color()).executes(ctx -> executeCreate(ctx.getSource(),
                StringArgumentType.getString(ctx, "name"), ColorArgumentType.getColor(ctx, "color"))))))
            .then(literal("list")
                .executes(ctx -> executeListTeams(ctx.getSource()))
                .then(argument("team", TeamArgumentType.team()).executes(ctx -> executeListMembers(ctx.getSource(),
                    TeamArgumentType.getTeam(ctx, "team")))))
            .then(literal("leave").executes(ctx -> executeLeave(ctx.getSource())))
            .then(literal("invite").then(argument("player", EntityArgumentType.player()).executes(ctx -> executeInvitePlayer(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))))
            .then(literal("accept").executes(ctx -> executeAcceptInvite(ctx.getSource())))
            .then(literal("passOwnership").then((argument("player", EntityArgumentType.player()).executes(ctx -> executePassOwnership(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"))))))
            .then(literal("disband").then(literal("confirm").executes(ctx -> executeDisband(ctx.getSource()))))
            .then(literal("msg").then(argument("message", StringArgumentType.greedyString()).executes(ctx -> executeTeamMsg(ctx.getSource(), StringArgumentType.getString(ctx, "message")))));

        LiteralArgumentBuilder<ServerCommandSource> setCommand = literal("set")
            .then(literal("color").then(argument("color", ColorArgumentType.color()).executes(ctx -> executeSetColor(ctx.getSource(), ColorArgumentType.getColor(ctx, "color")))))
            .then(literal("friendlyFire").then(argument("allowed", BoolArgumentType.bool()).executes(ctx -> executeSetFriendlyFire(ctx.getSource(), BoolArgumentType.getBool(ctx, "allowed")))))

            .then(literal("seeInvisibles").then(argument("allowed", BoolArgumentType.bool()).executes(ctx -> executeSetShowFriendlyInvisibles(ctx.getSource(), BoolArgumentType.getBool(ctx, "allowed")))))
            .then(literal("displayName").then(argument("displayName", StringArgumentType.word()).executes(ctx -> executeSetDisplayName(ctx.getSource(), StringArgumentType.getString(ctx, "displayName")))));

        teamCmd.then(setCommand);
        dispatcher.register(teamCmd);
    }

    private static int executeCreate(ServerCommandSource source, String displayName, Formatting color) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String name = displayName.toLowerCase();
        Scoreboard scoreboard = source.getServer().getScoreboard();

        if (color == Formatting.RESET) color = Formatting.WHITE;
        if (player.getScoreboardTeam() != null) {
            throw ALREADY_IN_TEAM.create();
        } else if (scoreboard.getTeam(name) != null || duplicateName(scoreboard.getTeams(), name)) {
            throw DUPLICATE_NAME.create();
        } else if (duplicateColor(scoreboard.getTeams(), color)) {
            throw DUPLICATE_COLOR.create();
        }
        Team newTeam = scoreboard.addTeam(name);
        newTeam.setDisplayName(Text.literal(displayName));
        scoreboard.addPlayerToTeam(player.getEntityName(), newTeam);
        newTeam.setColor(color);
        setPrefix(newTeam);
        setSuffix(newTeam);

        User user = LuckPermsProvider.get().getUserManager().getUser(player.getUuid());
        MetaNode metaNode = MetaNode.builder("guild-owner", name).build();

        user.data().clear(NodeType.META.predicate(mn -> mn.getMetaKey().equals("guild-owner")));
        user.data().add(metaNode);

        LuckPermsProvider.get().getUserManager().saveUser(user);

        source.sendFeedback(() -> Text.translatable("commands.teamcmd.add.success", newTeam.getFormattedName()), false);
        return 1;

    }

    private static int executeSetDisplayName(ServerCommandSource source, String displayName) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Team team = (Team) player.getScoreboardTeam();
        Scoreboard scoreboard = source.getServer().getScoreboard();

        if (team == null) {
            throw NOT_IN_TEAM.create();
        } else if (!TeamUtil.isOwner(player, team)) {
            throw NOT_GUILD_OWNER.create();
        } else if (team.getDisplayName().getString().equals(displayName)) {
            throw NAME_UNCHANGED.create();
        } else if (duplicateName(scoreboard.getTeams(), displayName)) {
            throw DUPLICATE_NAME.create();
        }

        team.setDisplayName(Text.literal(displayName));
        setPrefix(team);
        setSuffix(team);


        source.sendFeedback(() -> Text.translatable("commands.teamcmd.option.name.success", team.getFormattedName()), false);
        return 0;
    }

    private static int executeSetColor(ServerCommandSource source, Formatting color) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Team team = (Team) player.getScoreboardTeam();
        Scoreboard scoreboard = source.getServer().getScoreboard();

        if (color == Formatting.RESET) color = Formatting.WHITE;
        if (team == null) {
            throw NOT_IN_TEAM.create();
        } else if (!TeamUtil.isOwner(player, team)) {
            throw NOT_GUILD_OWNER.create();
        } else if (team.getColor().equals(color)) {
            throw COLOR_UNCHANGED.create();
        } else if (duplicateColor(scoreboard.getTeams(), color)) {
            throw DUPLICATE_COLOR.create();
        }

        team.setColor(color);
        setPrefix(team);
        setSuffix(team);

        Formatting finalColor = color;
        source.sendFeedback(() -> Text.translatable("commands.teamcmd.option.color.success", team.getFormattedName(),
            finalColor.getName()), false);
        return 0;
    }

    private static int executeSetFriendlyFire(ServerCommandSource source, boolean allowed) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Team team = (Team) player.getScoreboardTeam();
        if (team == null) {
            throw NOT_IN_TEAM.create();
        } else if (!TeamUtil.isOwner(player, team)) {
            throw NOT_GUILD_OWNER.create();
        } else if (team.isFriendlyFireAllowed() == allowed) {
            throw allowed ? FRIENDLY_FIRE_ALREADY_ENABLED.create() :
                FRIENDLY_FIRE_ALREADY_DISABLED.create();
        }
        team.setFriendlyFireAllowed(allowed);
        source.sendFeedback(() -> Text.translatable("commands.teamcmd.option.friendlyfire." + (allowed ? "enabled" :
            "disabled"), team.getFormattedName()), false);
        return 0;
    }

    private static int executeSetShowFriendlyInvisibles(ServerCommandSource source, boolean allowed) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Team team = (Team) player.getScoreboardTeam();
        if (team == null) {
            throw NOT_IN_TEAM.create();
        } else if (!TeamUtil.isOwner(player, team)) {
            throw NOT_GUILD_OWNER.create();
        } else if (team.shouldShowFriendlyInvisibles() == allowed) {
            throw allowed ? FRIENDLY_INVISIBLES_ALREADY_ENABLED.create() :
                OPTION_SEE_FRIENDLY_INVISIBLES_ALREADY_DISABLED_EXCEPTION.create();
        }
        team.setShowFriendlyInvisibles(allowed);
        source.sendFeedback(() -> Text.translatable("commands.teamcmd.option.seeFriendlyInvisibles." + (allowed ?
            "enabled" : "disabled"), team.getFormattedName()), false);
        return 0;
    }

    private static int executeInvitePlayer(ServerCommandSource source, ServerPlayerEntity newPlayer) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Team team = (Team) player.getScoreboardTeam();
        if (team == null) {
            throw NOT_IN_TEAM.create();
        } else if (newPlayer.isTeammate(player)) {
            throw PLAYER_ALREADY_TEAMMATE.create();
        } else if (team.getPlayerList().size() > (MAX_NUMBER_IN_GUILD - 1)) {
            source.sendFeedback(() -> Text.translatable("commands.teamcmd.invite.guild_too_big", String.valueOf(MAX_NUMBER_IN_GUILD)), false);
            return 0;
        }

        TeamUtil.addInvite(newPlayer, team.getName());
        TeamUtil.sendToTeammates(player, Text.translatable("commands.teamcmd.teammates.invite",
            player.getDisplayName(), newPlayer.getDisplayName()));
        source.sendFeedback(() -> Text.translatable("commands.teamcmd.invite.success", newPlayer.getDisplayName()), false);

        Text inviteText =
            Text.translatable("commands.teamcmd.invite", player.getDisplayName(), team.getFormattedName())
                .formatted(Formatting.GRAY)
                .styled((style) -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/guild accept"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("/guild accept")))
                    .withInsertion("/guild accept"));


        newPlayer.sendMessage(inviteText);
        return 0;
    }

    private static int executeAcceptInvite(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String teamName = TeamUtil.getInvitedTeam(player);

        if (player.getScoreboardTeam() != null) {
            throw ALREADY_IN_TEAM.create();
        } else if (teamName == null) {
            throw NOT_INVITED.create();
        }

        Team team = player.getScoreboard().getTeam(teamName);
        if (team == null) {
            throw TEAM_NOT_FOUND.create(teamName);
        }

        player.getScoreboard().addPlayerToTeam(player.getEntityName(), team);
        source.sendFeedback(() -> Text.translatable("commands.teamcmd.joined", team.getFormattedName()), false);
        TeamUtil.resetInvite(player);

        TeamUtil.sendToTeammates(player, Text.translatable("commands.teamcmd.teammates.joined",
            player.getDisplayName()));
        return 1;
    }

    private static int executeLeave(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Team team = (Team) player.getScoreboardTeam();
        if (team == null) {
            throw NOT_IN_TEAM.create();
        }

        if (team.getPlayerList().size() > 1 && TeamUtil.isOwner(player, team)) {
            source.sendFeedback(() -> Text.translatable("commands.teamcmd.fail.owner_cant_leave"), false);
            return 0;
        }

        TeamUtil.sendToTeammates(player, Text.translatable("commands.teamcmd.teammates.left",
            player.getDisplayName()));

        // remove them as the owner of the guild (owners are allowed to leave if they are the only member of the guild)
        if (TeamUtil.isOwner(player, team)) {
            User user = LuckPermsProvider.get().getUserManager().getUser(player.getUuid());
            user.data().clear(NodeType.META.predicate(mn -> mn.getMetaKey().equals("guild-owner")));
            LuckPermsProvider.get().getUserManager().saveUser(user);
        }

        player.getScoreboard().clearPlayerTeam(player.getEntityName());
        if (team.getPlayerList().size() == 0) {
            player.getScoreboard().removeTeam(team);
        }

        source.sendFeedback(() -> Text.translatable("commands.teamcmd.left", team.getFormattedName()), false);
        return 1;
    }

    private static int executeListMembers(ServerCommandSource source, Team team) {
        Collection<String> collection = team.getPlayerList();
        if (collection.isEmpty()) {
            source.sendFeedback(() -> Text.translatable("commands.teamcmd.list.members.empty", team.getFormattedName()),
                false);
        } else {
            source.sendFeedback(() -> Text.translatable("commands.teamcmd.list.members.success", team.getFormattedName(),
                collection.size(), Texts.joinOrdered(collection)), false);
        }
        return collection.size();
    }

    private static int executeListTeams(ServerCommandSource source) {
        Collection<Team> collection = source.getServer().getScoreboard().getTeams();
        if (collection.isEmpty()) {
            source.sendFeedback(() -> Text.translatable("commands.teamcmd.list.teams.empty"), false);
        } else {
            source.sendFeedback(() -> Text.translatable("commands.teamcmd.list.teams.success", collection.size(),
                Texts.join(collection, Team::getFormattedName)), false);
        }
        return collection.size();
    }

    private static int executePassOwnership(ServerCommandSource source, ServerPlayerEntity newOwner) throws CommandSyntaxException {

        ServerPlayerEntity player = source.getPlayerOrThrow();
        Team team = (Team) player.getScoreboardTeam();

        if (team == null) {
            throw NOT_IN_TEAM.create();
        } else if (!TeamUtil.isOwner(player, team)) {
            throw NOT_GUILD_OWNER.create();
        }

        if (player == newOwner) {
            source.sendFeedback(() -> Text.translatable("commands.teamcmd.pass_ownership.yourself"), false);
            return 0;
        }

        if (newOwner.getScoreboardTeam() == null || !player.getScoreboardTeam().getName().equals(newOwner.getScoreboardTeam().getName())) {
            source.sendFeedback(() -> Text.translatable("commands.teamcmd.pass_ownership.not_in_team"), false);
            return 0;
        }

        // remove owner from old owner
        User user = LuckPermsProvider.get().getUserManager().getUser(player.getUuid());
        user.data().clear(NodeType.META.predicate(mn -> mn.getMetaKey().equals("guild-owner")));
        LuckPermsProvider.get().getUserManager().saveUser(user);

        // assign the owner meta to the new owner
        User newOwnerUser = LuckPermsProvider.get().getUserManager().getUser(newOwner.getUuid());
        MetaNode metaNode = MetaNode.builder("guild-owner", team.getName()).build();
        newOwnerUser.data().add(metaNode);
        LuckPermsProvider.get().getUserManager().saveUser(newOwnerUser);

        source.sendFeedback(() -> Text.translatable("commands.teamcmd.pass_ownership.success", player.getName()), false);

        return 1;
    }

    private static int executeDisband(ServerCommandSource source) throws CommandSyntaxException {

        ServerPlayerEntity player = source.getPlayerOrThrow();
        Team team = (Team) player.getScoreboardTeam();

        if (!TeamUtil.isOwner(player, team)) {
            throw NOT_GUILD_OWNER.create();
        }

        player.getScoreboard().removeTeam(team);
        source.sendFeedback(() -> Text.translatable("commands.teamcmd.disband.success"), false);

        return 1;
    }

    private static int executeTeamMsg(ServerCommandSource source, String message) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Team team = (Team) player.getScoreboardTeam();

        Text text = Text.literal(""); // todo implement formatting

        TeamUtil.sendToTeammates(player, text);

        return 1;
    }

    private static boolean duplicateName(Collection<Team> teams, String name) {
        return !TeamCommand.CONFIG.allowDuplicateDisplaynames && teams.stream().anyMatch(other -> other
            .getDisplayName()
            .getString()
            .equalsIgnoreCase(name));
    }

    private static boolean duplicateColor(Collection<Team> teams, Formatting color) {
        return !TeamCommand.CONFIG.allowDuplicateColors && teams.stream().anyMatch(other -> other
            .getColor()
            .equals(color));
    }


    private static void setPrefix(Team team) {
        Formatting teamColor = team.getColor();
        team.setPrefix(
            Text.literal(String.format(TeamCommand.CONFIG.prefixFormat, team.getDisplayName().getString()))
                .formatted(TeamCommand.CONFIG.prefixUseTeamColor ? teamColor : getSecondaryColor(teamColor)));
    }

    private static void setSuffix(Team team) {
        Formatting teamColor = team.getColor();
        team.setSuffix(
            Text.literal(String.format(TeamCommand.CONFIG.suffixFormat, team.getDisplayName().getString()))
                .formatted(TeamCommand.CONFIG.suffixUseTeamColor ? teamColor : getSecondaryColor(teamColor)));
    }

    private static Formatting getSecondaryColor(Formatting primary) {
        Formatting secondary = Formatting.RESET;
        switch (primary) {
            case AQUA -> secondary = Formatting.DARK_AQUA;
            case DARK_AQUA -> secondary = Formatting.AQUA;
            case BLUE -> secondary = Formatting.DARK_BLUE;
            case DARK_BLUE -> secondary = Formatting.BLUE;
            case WHITE -> secondary = Formatting.GRAY;
            case GRAY -> secondary = Formatting.WHITE;
            case DARK_GRAY -> secondary = Formatting.BLACK;
            case BLACK -> secondary = Formatting.DARK_GRAY;
            case RED -> secondary = Formatting.DARK_RED;
            case DARK_RED -> secondary = Formatting.RED;
            case GREEN -> secondary = Formatting.DARK_GREEN;
            case DARK_GREEN -> secondary = Formatting.GREEN;
            case LIGHT_PURPLE -> secondary = Formatting.DARK_PURPLE;
            case DARK_PURPLE -> secondary = Formatting.LIGHT_PURPLE;
            case YELLOW -> secondary = Formatting.GOLD;
            case GOLD -> secondary = Formatting.YELLOW;
        }
        return secondary;
    }
}
