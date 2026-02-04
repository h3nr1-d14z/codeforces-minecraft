package com.hieu.cfquest.command;

import com.hieu.cfquest.CFQuestMod;
import com.hieu.cfquest.storage.PlayerDataManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;

import java.net.URI;
import net.minecraft.util.Formatting;

public class CFLinkCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("cf")
                        .then(CommandManager.literal("link")
                                .then(CommandManager.argument("handle", StringArgumentType.word())
                                        .executes(CFLinkCommand::executeLink)))
                        .then(CommandManager.literal("unlink")
                                .executes(CFLinkCommand::executeUnlink))
                        .then(CommandManager.literal("status")
                                .executes(CFLinkCommand::executeStatus))
        );
    }

    private static int executeLink(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("Lệnh này chỉ có thể sử dụng trong game!"));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        String handle = StringArgumentType.getString(context, "handle");
        PlayerDataManager dataManager = CFQuestMod.getInstance().getPlayerDataManager();

        // Check if already linked
        if (dataManager.isLinked(player)) {
            String currentHandle = dataManager.getCfHandle(player);
            source.sendFeedback(() -> Text.literal("Bạn đã liên kết với tài khoản: ")
                    .formatted(Formatting.YELLOW)
                    .append(Text.literal(currentHandle)
                            .formatted(Formatting.AQUA, Formatting.BOLD))
                    .append(Text.literal("\nSử dụng /cf unlink để hủy liên kết trước.")
                            .formatted(Formatting.GRAY)), false);
            return 0;
        }

        // Check if handle is already linked to another player
        if (dataManager.isHandleLinked(handle)) {
            source.sendError(Text.literal("Tài khoản Codeforces này đã được liên kết với người chơi khác!"));
            return 0;
        }

        // Verify handle exists on Codeforces
        source.sendFeedback(() -> Text.literal("Đang xác minh tài khoản Codeforces...")
                .formatted(Formatting.GRAY), false);

        CFQuestMod.getInstance().getCodeforcesPoller().getApi().verifyHandle(handle)
                .thenAccept(valid -> {
                    if (valid) {
                        // Link the account
                        dataManager.linkPlayer(player, handle);

                        source.getServer().execute(() -> {
                            Text successMsg = Text.literal("Đã liên kết thành công với tài khoản Codeforces: ")
                                    .formatted(Formatting.GREEN)
                                    .append(Text.literal(handle)
                                            .formatted(Formatting.AQUA, Formatting.BOLD)
                                            .styled(style -> style
                                                    .withClickEvent(new ClickEvent.OpenUrl(URI.create("https://codeforces.com/profile/" + handle)))
                                                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click để xem profile")))));

                            player.sendMessage(successMsg, false);
                        });
                    } else {
                        source.getServer().execute(() -> {
                            player.sendMessage(Text.literal("Không tìm thấy tài khoản Codeforces: " + handle)
                                    .formatted(Formatting.RED), false);
                        });
                    }
                })
                .exceptionally(e -> {
                    source.getServer().execute(() -> {
                        player.sendMessage(Text.literal("Lỗi khi xác minh tài khoản: " + e.getMessage())
                                .formatted(Formatting.RED), false);
                    });
                    return null;
                });

        return 1;
    }

    private static int executeUnlink(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("Lệnh này chỉ có thể sử dụng trong game!"));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        PlayerDataManager dataManager = CFQuestMod.getInstance().getPlayerDataManager();

        if (!dataManager.isLinked(player)) {
            source.sendError(Text.literal("Bạn chưa liên kết tài khoản Codeforces nào!"));
            return 0;
        }

        String oldHandle = dataManager.getCfHandle(player);
        dataManager.unlinkPlayer(player);

        source.sendFeedback(() -> Text.literal("Đã hủy liên kết với tài khoản: ")
                .formatted(Formatting.YELLOW)
                .append(Text.literal(oldHandle)
                        .formatted(Formatting.AQUA)), false);

        return 1;
    }

    private static int executeStatus(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("Lệnh này chỉ có thể sử dụng trong game!"));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        PlayerDataManager dataManager = CFQuestMod.getInstance().getPlayerDataManager();
        PlayerDataManager.PlayerData data = dataManager.getPlayerData(player);

        Text header = Text.literal("═══ Trạng Thái CF Quest ═══")
                .formatted(Formatting.GOLD, Formatting.BOLD);
        player.sendMessage(header, false);

        // Link status
        if (data.isLinked()) {
            Text linkStatus = Text.literal("Liên kết: ")
                    .formatted(Formatting.WHITE)
                    .append(Text.literal(data.getCfHandle())
                            .formatted(Formatting.AQUA, Formatting.BOLD)
                            .styled(style -> style
                                    .withClickEvent(new ClickEvent.OpenUrl(URI.create("https://codeforces.com/profile/" + data.getCfHandle())))
                                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click để xem profile")))));
            player.sendMessage(linkStatus, false);
        } else {
            Text notLinked = Text.literal("Liên kết: ")
                    .formatted(Formatting.WHITE)
                    .append(Text.literal("Chưa liên kết")
                            .formatted(Formatting.RED))
                    .append(Text.literal(" (sử dụng /cf link <handle>)")
                            .formatted(Formatting.GRAY));
            player.sendMessage(notLinked, false);
        }

        // Player type (premium vs offline)
        String playerType = data.isPremium() ? "Premium" : "Offline";
        Text typeStatus = Text.literal("Loại tài khoản: ")
                .formatted(Formatting.WHITE)
                .append(Text.literal(playerType)
                        .formatted(data.isPremium() ? Formatting.GREEN : Formatting.YELLOW));
        player.sendMessage(typeStatus, false);

        // Stats
        Text stats = Text.literal("Thống kê: ")
                .formatted(Formatting.WHITE)
                .append(Text.literal(data.getTotalSolves() + " bài giải")
                        .formatted(Formatting.AQUA))
                .append(Text.literal(" | ")
                        .formatted(Formatting.GRAY))
                .append(Text.literal(data.getTotalWins() + " lần thắng")
                        .formatted(Formatting.GOLD));
        player.sendMessage(stats, false);

        // Active quest status
        if (CFQuestMod.getInstance().getQuestManager().hasActiveQuest()) {
            var quest = CFQuestMod.getInstance().getQuestManager().getActiveQuest();
            Text questStatus = Text.literal("Quest đang chạy: ")
                    .formatted(Formatting.WHITE)
                    .append(Text.literal(quest.getProblemDisplayName())
                            .formatted(Formatting.GREEN))
                    .append(Text.literal(" (còn " + quest.getRemainingTimeFormatted() + ")")
                            .formatted(Formatting.YELLOW));
            player.sendMessage(questStatus, false);

            if (data.isLinked() && quest.hasWon(data.getCfHandle())) {
                player.sendMessage(Text.literal("Bạn đã giải bài này!")
                        .formatted(Formatting.GREEN, Formatting.BOLD), false);
            }
        } else {
            player.sendMessage(Text.literal("Không có quest nào đang chạy")
                    .formatted(Formatting.GRAY), false);
        }

        return 1;
    }
}
