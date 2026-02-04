package com.hieu.cfquest.command;

import com.hieu.cfquest.CFQuestMod;
import com.hieu.cfquest.api.model.Problem;
import com.hieu.cfquest.quest.QuestManager;
import com.hieu.cfquest.storage.PlayerDataManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.function.Predicate;

public class CFAdminCommand {

    /**
     * Permission check that works across MC versions.
     * Checks if source is console OR player with op level >= 2
     */
    private static final Predicate<ServerCommandSource> REQUIRE_OP = source -> {
        // Console always has permission
        if (!source.isExecutedByPlayer()) {
            return true;
        }
        // Check player's op level
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return false;
        }
        return source.getServer().getPlayerManager().isOperator(player.getGameProfile());
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("cf")
                        .then(CommandManager.literal("quest")
                                .then(CommandManager.literal("start")
                                        .requires(REQUIRE_OP)
                                        .then(CommandManager.argument("contestId", IntegerArgumentType.integer(1))
                                                .then(CommandManager.argument("problemIndex", StringArgumentType.word())
                                                        .executes(CFAdminCommand::executeStart)
                                                        .then(CommandManager.argument("timeout", IntegerArgumentType.integer(1, 180))
                                                                .executes(CFAdminCommand::executeStartWithTimeout)))))
                                .then(CommandManager.literal("stop")
                                        .requires(REQUIRE_OP)
                                        .executes(CFAdminCommand::executeStop)))
                        .then(CommandManager.literal("admin")
                                .requires(REQUIRE_OP)
                                .then(CommandManager.literal("reload")
                                        .executes(CFAdminCommand::executeReload))
                                .then(CommandManager.literal("players")
                                        .executes(CFAdminCommand::executeListPlayers))
                                .then(CommandManager.literal("forcepoll")
                                        .executes(CFAdminCommand::executeForcePoll))
                                .then(CommandManager.literal("unlink")
                                        .then(CommandManager.argument("player", StringArgumentType.word())
                                                .executes(CFAdminCommand::executeAdminUnlink))))
        );
    }

    private static int executeStart(CommandContext<ServerCommandSource> context) {
        return startQuest(context, CFQuestMod.getInstance().getConfig().getQuest().getDefaultTimeoutMinutes());
    }

    private static int executeStartWithTimeout(CommandContext<ServerCommandSource> context) {
        int timeout = IntegerArgumentType.getInteger(context, "timeout");
        return startQuest(context, timeout);
    }

    private static int startQuest(CommandContext<ServerCommandSource> context, int timeoutMinutes) {
        ServerCommandSource source = context.getSource();
        int contestId = IntegerArgumentType.getInteger(context, "contestId");
        String problemIndex = StringArgumentType.getString(context, "problemIndex").toUpperCase();

        QuestManager questManager = CFQuestMod.getInstance().getQuestManager();

        if (questManager.hasActiveQuest()) {
            source.sendError(Text.literal("Đã có quest đang chạy! Sử dụng /cf quest stop để kết thúc trước."));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Đang lấy thông tin bài tập từ Codeforces...")
                .formatted(Formatting.GRAY), false);

        CFQuestMod.getInstance().getCodeforcesPoller().getApi()
                .getProblem(contestId, problemIndex)
                .thenAccept(problem -> {
                    if (problem == null) {
                        source.getServer().execute(() -> {
                            source.sendError(Text.literal("Không tìm thấy bài " + problemIndex +
                                    " trong contest " + contestId));
                        });
                        return;
                    }

                    source.getServer().execute(() -> {
                        boolean started = questManager.startQuest(contestId, problem, timeoutMinutes);

                        if (started) {
                            source.sendFeedback(() -> Text.literal("Đã bắt đầu quest thành công!")
                                    .formatted(Formatting.GREEN, Formatting.BOLD), true);
                        } else {
                            source.sendError(Text.literal("Không thể bắt đầu quest!"));
                        }
                    });
                })
                .exceptionally(e -> {
                    source.getServer().execute(() -> {
                        source.sendError(Text.literal("Lỗi khi lấy thông tin bài: " + e.getMessage()));
                    });
                    return null;
                });

        return 1;
    }

    private static int executeStop(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        QuestManager questManager = CFQuestMod.getInstance().getQuestManager();

        if (!questManager.hasActiveQuest()) {
            source.sendError(Text.literal("Không có quest nào đang chạy!"));
            return 0;
        }

        questManager.endQuest(true);
        source.sendFeedback(() -> Text.literal("Đã hủy quest!")
                .formatted(Formatting.YELLOW), true);

        return 1;
    }

    private static int executeReload(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        // Note: Full reload would require restarting managers
        // For now, just inform the user
        source.sendFeedback(() -> Text.literal("Để reload config, hãy restart server.")
                .formatted(Formatting.YELLOW), false);
        source.sendFeedback(() -> Text.literal("Config path: <world>/cfquest/config.json")
                .formatted(Formatting.GRAY), false);

        return 1;
    }

    private static int executeListPlayers(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PlayerDataManager dataManager = CFQuestMod.getInstance().getPlayerDataManager();

        Map<String, String> linkedPlayers = dataManager.getAllLinkedPlayers();

        source.sendFeedback(() -> Text.literal("═══ Người chơi đã liên kết ═══")
                .formatted(Formatting.GOLD, Formatting.BOLD), false);

        if (linkedPlayers.isEmpty()) {
            source.sendFeedback(() -> Text.literal("Chưa có người chơi nào liên kết.")
                    .formatted(Formatting.GRAY), false);
            return 1;
        }

        for (Map.Entry<String, String> entry : linkedPlayers.entrySet()) {
            String identifier = entry.getKey();
            String cfHandle = entry.getValue();
            String playerName = dataManager.getPlayerName(identifier);
            boolean isOffline = dataManager.isOfflinePlayer(identifier);

            String displayId = isOffline ? identifier.substring("offline:".length()) : identifier.substring(0, 8) + "...";
            String typeLabel = isOffline ? "[Offline]" : "[Premium]";

            source.sendFeedback(() -> Text.literal(playerName != null ? playerName : displayId)
                    .formatted(Formatting.GREEN)
                    .append(Text.literal(" " + typeLabel)
                            .formatted(isOffline ? Formatting.YELLOW : Formatting.AQUA))
                    .append(Text.literal(" → ")
                            .formatted(Formatting.GRAY))
                    .append(Text.literal(cfHandle)
                            .formatted(Formatting.AQUA)), false);
        }

        source.sendFeedback(() -> Text.literal("Tổng: " + linkedPlayers.size() + " người chơi")
                .formatted(Formatting.GRAY), false);

        return 1;
    }

    private static int executeForcePoll(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        if (!CFQuestMod.getInstance().getQuestManager().hasActiveQuest()) {
            source.sendError(Text.literal("Không có quest đang chạy!"));
            return 0;
        }

        CFQuestMod.getInstance().getCodeforcesPoller().forcePoll();
        source.sendFeedback(() -> Text.literal("Đã kích hoạt poll Codeforces!")
                .formatted(Formatting.GREEN), false);

        return 1;
    }

    private static int executeAdminUnlink(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String playerName = StringArgumentType.getString(context, "player");

        PlayerDataManager dataManager = CFQuestMod.getInstance().getPlayerDataManager();

        // Try to find player by name in linked players
        Map<String, String> linkedPlayers = dataManager.getAllLinkedPlayers();
        String targetIdentifier = null;

        for (String identifier : linkedPlayers.keySet()) {
            String name = dataManager.getPlayerName(identifier);
            if (name != null && name.equalsIgnoreCase(playerName)) {
                targetIdentifier = identifier;
                break;
            }
            // Also check if it matches the identifier directly (for offline players)
            if (identifier.equals("offline:" + playerName.toLowerCase())) {
                targetIdentifier = identifier;
                break;
            }
        }

        if (targetIdentifier == null) {
            source.sendError(Text.literal("Không tìm thấy người chơi đã liên kết: " + playerName));
            return 0;
        }

        PlayerDataManager.PlayerData data = dataManager.getPlayerDataByIdentifier(targetIdentifier);
        if (data != null) {
            String oldHandle = data.getCfHandle();
            data.setCfHandle(null);
            data.setLinkTime(0);
            dataManager.save();

            source.sendFeedback(() -> Text.literal("Đã hủy liên kết của " + playerName + " với " + oldHandle)
                    .formatted(Formatting.YELLOW), true);
            return 1;
        }

        source.sendError(Text.literal("Lỗi khi hủy liên kết!"));
        return 0;
    }
}
