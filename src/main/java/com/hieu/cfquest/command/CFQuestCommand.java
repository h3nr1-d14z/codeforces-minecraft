package com.hieu.cfquest.command;

import com.hieu.cfquest.CFQuestMod;
import com.hieu.cfquest.quest.Quest;
import com.hieu.cfquest.quest.QuestManager;
import com.hieu.cfquest.storage.QuestHistory;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;

import java.net.URI;
import net.minecraft.util.Formatting;

import java.util.List;

public class CFQuestCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("cf")
                        .then(CommandManager.literal("quest")
                                .then(CommandManager.literal("info")
                                        .executes(CFQuestCommand::executeInfo)))
                        .then(CommandManager.literal("leaderboard")
                                .executes(CFQuestCommand::executeLeaderboard))
                        .then(CommandManager.literal("history")
                                .executes(CFQuestCommand::executeHistory))
        );
    }

    private static int executeInfo(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        QuestManager questManager = CFQuestMod.getInstance().getQuestManager();

        if (!questManager.hasActiveQuest()) {
            source.sendFeedback(() -> Text.literal("Hiện không có quest nào đang diễn ra.")
                    .formatted(Formatting.YELLOW), false);
            return 0;
        }

        Quest quest = questManager.getActiveQuest();

        // Header
        source.sendFeedback(() -> Text.literal("═══ Thông Tin Quest ═══")
                .formatted(Formatting.GOLD, Formatting.BOLD), false);

        // Problem info
        source.sendFeedback(() -> Text.literal("Bài: ")
                .formatted(Formatting.WHITE)
                .append(Text.literal(quest.getProblemDisplayName())
                        .formatted(Formatting.AQUA, Formatting.BOLD)), false);

        // Problem link
        source.sendFeedback(() -> Text.literal("Link: ")
                .formatted(Formatting.WHITE)
                .append(Text.literal(quest.getProblemUrl())
                        .formatted(Formatting.BLUE, Formatting.UNDERLINE)
                        .styled(style -> style
                                .withClickEvent(new ClickEvent.OpenUrl(URI.create(quest.getProblemUrl())))
                                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click để mở bài"))))), false);

        // Rating
        int rating = quest.getProblemRating();
        Formatting ratingColor = getRatingColor(rating);
        source.sendFeedback(() -> Text.literal("Độ khó: ")
                .formatted(Formatting.WHITE)
                .append(Text.literal(String.valueOf(rating))
                        .formatted(ratingColor, Formatting.BOLD)), false);

        // Time remaining
        source.sendFeedback(() -> Text.literal("Thời gian còn lại: ")
                .formatted(Formatting.WHITE)
                .append(Text.literal(quest.getRemainingTimeFormatted())
                        .formatted(Formatting.GREEN, Formatting.BOLD)), false);

        // Current winners
        List<Quest.Winner> winners = quest.getWinners();
        if (winners.isEmpty()) {
            source.sendFeedback(() -> Text.literal("Chưa có ai giải được!")
                    .formatted(Formatting.GRAY), false);
        } else {
            source.sendFeedback(() -> Text.literal("Đã giải:")
                    .formatted(Formatting.WHITE), false);

            for (Quest.Winner winner : winners) {
                Formatting placeColor = getPlaceColor(winner.getPlace());
                source.sendFeedback(() -> Text.literal("  " + winner.getPlace() + ". ")
                        .formatted(placeColor, Formatting.BOLD)
                        .append(Text.literal(winner.getPlayerName())
                                .formatted(Formatting.GREEN))
                        .append(Text.literal(" - " + winner.getFormattedTotalTime())
                                .formatted(Formatting.AQUA)), false);
            }
        }

        // Remaining slots
        int maxWinners = CFQuestMod.getInstance().getConfig().getQuest().getMaxWinners();
        int remaining = maxWinners - winners.size();
        if (remaining > 0) {
            source.sendFeedback(() -> Text.literal("Còn " + remaining + " vị trí nhận thưởng!")
                    .formatted(Formatting.YELLOW), false);
        }

        return 1;
    }

    private static int executeLeaderboard(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        QuestHistory history = CFQuestMod.getInstance().getQuestHistory();

        List<QuestHistory.LeaderboardEntry> leaderboard = history.getLeaderboard();

        source.sendFeedback(() -> Text.literal("═══ Bảng Xếp Hạng CF Quest ═══")
                .formatted(Formatting.GOLD, Formatting.BOLD), false);

        if (leaderboard.isEmpty()) {
            source.sendFeedback(() -> Text.literal("Chưa có dữ liệu xếp hạng.")
                    .formatted(Formatting.GRAY), false);
            return 1;
        }

        int rank = 1;
        for (QuestHistory.LeaderboardEntry entry : leaderboard) {
            if (rank > 10) break; // Show top 10

            Formatting rankColor = rank <= 3 ? getPlaceColor(rank) : Formatting.WHITE;
            final int finalRank = rank;

            source.sendFeedback(() -> Text.literal(String.format("%2d. ", finalRank))
                    .formatted(rankColor, Formatting.BOLD)
                    .append(Text.literal(entry.playerName)
                            .formatted(Formatting.GREEN))
                    .append(Text.literal(" - ")
                            .formatted(Formatting.GRAY))
                    .append(Text.literal(entry.firstPlaces + " Vàng")
                            .formatted(Formatting.GOLD))
                    .append(Text.literal(" | ")
                            .formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(entry.secondPlaces + " Bạc")
                            .formatted(Formatting.GRAY))
                    .append(Text.literal(" | ")
                            .formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(entry.thirdPlaces + " Đồng")
                            .formatted(Formatting.RED))
                    .append(Text.literal(" (" + entry.totalSolves + " bài)")
                            .formatted(Formatting.AQUA)), false);

            rank++;
        }

        // Show player's rank if they're a player and not in top 10
        if (source.isExecutedByPlayer()) {
            ServerPlayerEntity player = source.getPlayer();
            String playerId = CFQuestMod.getInstance().getPlayerDataManager().getPlayerIdentifier(player);

            int playerRank = 1;
            QuestHistory.LeaderboardEntry playerEntry = null;

            for (QuestHistory.LeaderboardEntry entry : leaderboard) {
                if (entry.playerIdentifier.equals(playerId)) {
                    playerEntry = entry;
                    break;
                }
                playerRank++;
            }

            if (playerEntry != null && playerRank > 10) {
                final int finalPlayerRank = playerRank;
                final QuestHistory.LeaderboardEntry finalEntry = playerEntry;

                source.sendFeedback(() -> Text.literal("...")
                        .formatted(Formatting.GRAY), false);
                source.sendFeedback(() -> Text.literal(String.format("%2d. ", finalPlayerRank))
                        .formatted(Formatting.YELLOW, Formatting.BOLD)
                        .append(Text.literal(finalEntry.playerName + " (Bạn)")
                                .formatted(Formatting.GREEN))
                        .append(Text.literal(" - " + finalEntry.totalSolves + " bài")
                                .formatted(Formatting.AQUA)), false);
            }
        }

        return 1;
    }

    private static int executeHistory(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        QuestHistory history = CFQuestMod.getInstance().getQuestHistory();

        List<QuestHistory.QuestRecord> recent = history.getRecentHistory(5);

        source.sendFeedback(() -> Text.literal("═══ Lịch Sử Quest Gần Đây ═══")
                .formatted(Formatting.GOLD, Formatting.BOLD), false);

        if (recent.isEmpty()) {
            source.sendFeedback(() -> Text.literal("Chưa có quest nào hoàn thành.")
                    .formatted(Formatting.GRAY), false);
            return 1;
        }

        for (int i = recent.size() - 1; i >= 0; i--) {
            QuestHistory.QuestRecord record = recent.get(i);

            source.sendFeedback(() -> Text.literal("• ")
                    .formatted(Formatting.WHITE)
                    .append(Text.literal(record.getProblemName())
                            .formatted(Formatting.AQUA))
                    .append(Text.literal(" (Rating: " + record.getProblemRating() + ")")
                            .formatted(getRatingColor(record.getProblemRating()))), false);

            if (record.getWinners().isEmpty()) {
                source.sendFeedback(() -> Text.literal("  Không có người thắng")
                        .formatted(Formatting.GRAY), false);
            } else {
                for (QuestHistory.WinnerRecord winner : record.getWinners()) {
                    source.sendFeedback(() -> Text.literal("  " + winner.getPlace() + ". ")
                            .formatted(getPlaceColor(winner.getPlace()))
                            .append(Text.literal(winner.getPlayerName())
                                    .formatted(Formatting.GREEN)), false);
                }
            }
        }

        return 1;
    }

    private static Formatting getRatingColor(int rating) {
        if (rating >= 2400) return Formatting.RED;
        if (rating >= 2100) return Formatting.GOLD;
        if (rating >= 1900) return Formatting.LIGHT_PURPLE;
        if (rating >= 1600) return Formatting.BLUE;
        if (rating >= 1400) return Formatting.AQUA;
        if (rating >= 1200) return Formatting.GREEN;
        return Formatting.GRAY;
    }

    private static Formatting getPlaceColor(int place) {
        return switch (place) {
            case 1 -> Formatting.GOLD;
            case 2 -> Formatting.GRAY;
            case 3 -> Formatting.RED;
            default -> Formatting.WHITE;
        };
    }
}
