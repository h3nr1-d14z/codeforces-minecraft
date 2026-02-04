package com.hieu.cfquest.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hieu.cfquest.CFQuestMod;
import com.hieu.cfquest.api.model.Problem;
import com.hieu.cfquest.config.ModConfig;
import com.hieu.cfquest.reward.RewardManager;
import com.hieu.cfquest.storage.PlayerDataManager;
import com.hieu.cfquest.storage.QuestHistory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class QuestManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final MinecraftServer server;
    private final PlayerDataManager playerDataManager;
    private final RewardManager rewardManager;
    private final QuestHistory questHistory;
    private final ModConfig config;

    private Quest activeQuest;

    public QuestManager(MinecraftServer server, PlayerDataManager playerDataManager,
                        RewardManager rewardManager, QuestHistory questHistory, ModConfig config) {
        this.server = server;
        this.playerDataManager = playerDataManager;
        this.rewardManager = rewardManager;
        this.questHistory = questHistory;
        this.config = config;

        // Load any saved active quest
        loadActiveQuest();
    }

    public boolean hasActiveQuest() {
        return activeQuest != null;
    }

    public Quest getActiveQuest() {
        return activeQuest;
    }

    public boolean startQuest(int contestId, Problem problem, int timeoutMinutes) {
        if (hasActiveQuest()) {
            return false;
        }

        activeQuest = new Quest(contestId, problem, timeoutMinutes);
        saveActiveQuest();

        // Announce quest to all players
        announceQuestStart();

        CFQuestMod.LOGGER.info("Đã bắt đầu quest: {} (Contest: {}, Timeout: {}m)",
                problem.getName(), contestId, timeoutMinutes);

        return true;
    }

    public void endQuest(boolean cancelled) {
        if (!hasActiveQuest()) {
            return;
        }

        Quest quest = activeQuest;
        activeQuest = null;

        // Delete saved quest file
        deleteSavedQuest();

        if (cancelled) {
            announceCancelled();
        } else {
            // Distribute rewards
            distributeRewards(quest);

            // Announce results
            announceQuestEnd(quest);

            // Save to history
            questHistory.addQuest(quest);
        }

        CFQuestMod.LOGGER.info("Đã kết thúc quest: {} (cancelled: {})",
                quest.getProblemDisplayName(), cancelled);
    }

    public int recordSolve(String playerUuid, String cfHandle, long solveTimeSeconds, int penaltyMinutes) {
        if (!hasActiveQuest()) {
            return -1;
        }

        if (activeQuest.hasWon(cfHandle)) {
            return -1;
        }

        // Get player name - support both UUID and username-based players
        String playerName = getPlayerName(playerUuid);

        Quest.Winner winner = activeQuest.addWinner(playerUuid, playerName, cfHandle,
                solveTimeSeconds, penaltyMinutes);

        if (winner == null) {
            return -1;
        }

        saveActiveQuest();

        // Announce solve
        announceSolve(winner);

        // Check if quest should end
        if (activeQuest.getWinners().size() >= config.getQuest().getMaxWinners()) {
            server.execute(() -> endQuest(false));
        }

        return winner.getPlace();
    }

    public void notifyWrongAnswer(String playerUuid, String cfHandle, int newWrongs, int totalWrongs) {
        ServerPlayerEntity player = getPlayer(playerUuid);

        if (player != null) {
            int penaltyMinutes = config.getQuest().getPenaltyMinutes();

            Text message = Text.literal("[QUEST] ")
                    .formatted(Formatting.GOLD)
                    .append(Text.literal("Bạn nộp sai! ")
                            .formatted(Formatting.RED))
                    .append(Text.literal("Phạt +" + (newWrongs * penaltyMinutes) + " phút. ")
                            .formatted(Formatting.YELLOW))
                    .append(Text.literal("Tổng phạt: " + (totalWrongs * penaltyMinutes) + " phút. Cố gắng lên!")
                            .formatted(Formatting.GRAY));

            server.execute(() -> {
                player.sendMessage(message, false);
                player.playSound(SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            });
        }
    }

    private void distributeRewards(Quest quest) {
        for (Quest.Winner winner : quest.getWinners()) {
            ServerPlayerEntity player = getPlayer(winner.getPlayerUuid());

            if (player != null) {
                rewardManager.giveReward(player, quest.getProblemRating(), winner.getPlace());
            }
        }
    }

    private void announceQuestStart() {
        Text header = Text.literal("═══════════════════════════════════════")
                .formatted(Formatting.GOLD);

        Text title = Text.literal("[QUEST] ")
                .formatted(Formatting.GOLD)
                .append(Text.literal("NHIỆM VỤ MỚI!")
                        .formatted(Formatting.YELLOW, Formatting.BOLD));

        Text problemInfo = Text.literal("[QUEST] ")
                .formatted(Formatting.GOLD)
                .append(Text.literal("Bài: ")
                        .formatted(Formatting.WHITE))
                .append(Text.literal(activeQuest.getProblemDisplayName())
                        .formatted(Formatting.AQUA, Formatting.BOLD));

        Text linkInfo = Text.literal("[QUEST] ")
                .formatted(Formatting.GOLD)
                .append(Text.literal("Link: ")
                        .formatted(Formatting.WHITE))
                .append(Text.literal(activeQuest.getProblemUrl())
                        .formatted(Formatting.BLUE, Formatting.UNDERLINE));

        int rating = activeQuest.getProblemRating();
        Formatting ratingColor = getRatingColor(rating);

        Text ratingInfo = Text.literal("[QUEST] ")
                .formatted(Formatting.GOLD)
                .append(Text.literal("Độ khó: ")
                        .formatted(Formatting.WHITE))
                .append(Text.literal(String.valueOf(rating))
                        .formatted(ratingColor, Formatting.BOLD))
                .append(Text.literal(" | Thời gian: ")
                        .formatted(Formatting.WHITE))
                .append(Text.literal(activeQuest.getTimeoutMinutes() + " phút")
                        .formatted(Formatting.GREEN));

        Text instruction = Text.literal("[QUEST] ")
                .formatted(Formatting.GOLD)
                .append(Text.literal("Giải bài và nộp trên Codeforces để nhận phần thưởng!")
                        .formatted(Formatting.GRAY));

        server.execute(() -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.sendMessage(header, false);
                player.sendMessage(title, false);
                player.sendMessage(problemInfo, false);
                player.sendMessage(linkInfo, false);
                player.sendMessage(ratingInfo, false);
                player.sendMessage(instruction, false);
                player.sendMessage(header, false);

                // Play sound
                player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        });
    }

    private void announceSolve(Quest.Winner winner) {
        Formatting placeColor;
        String placeEmoji;
        switch (winner.getPlace()) {
            case 1:
                placeColor = Formatting.GOLD;
                placeEmoji = "1";
                break;
            case 2:
                placeColor = Formatting.GRAY;
                placeEmoji = "2";
                break;
            case 3:
                placeColor = Formatting.RED;
                placeEmoji = "3";
                break;
            default:
                placeColor = Formatting.WHITE;
                placeEmoji = String.valueOf(winner.getPlace());
        }

        Text message = Text.literal("[QUEST] ")
                .formatted(Formatting.GOLD)
                .append(Text.literal(winner.getPlayerName())
                        .formatted(Formatting.GREEN, Formatting.BOLD))
                .append(Text.literal(" đã giải được bài!")
                        .formatted(Formatting.YELLOW))
                .append(Text.literal(" Hạng " + placeEmoji)
                        .formatted(placeColor, Formatting.BOLD))
                .append(Text.literal(" | Thời gian: ")
                        .formatted(Formatting.WHITE))
                .append(Text.literal(winner.getFormattedTime())
                        .formatted(Formatting.AQUA))
                .append(Text.literal(" | Phạt: ")
                        .formatted(Formatting.WHITE))
                .append(Text.literal(winner.getPenaltyMinutes() + " phút")
                        .formatted(Formatting.RED));

        server.execute(() -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.sendMessage(message, false);
                player.playSound(SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.0f);
            }
        });
    }

    private void announceQuestEnd(Quest quest) {
        Text header = Text.literal("═══════════════════════════════════════")
                .formatted(Formatting.GOLD);

        Text title = Text.literal("[QUEST] ")
                .formatted(Formatting.GOLD)
                .append(Text.literal("KẾT THÚC NHIỆM VỤ!")
                        .formatted(Formatting.YELLOW, Formatting.BOLD));

        Text problemInfo = Text.literal("[QUEST] ")
                .formatted(Formatting.GOLD)
                .append(Text.literal("Bài: ")
                        .formatted(Formatting.WHITE))
                .append(Text.literal(quest.getProblemDisplayName())
                        .formatted(Formatting.AQUA));

        server.execute(() -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.sendMessage(header, false);
                player.sendMessage(title, false);
                player.sendMessage(problemInfo, false);

                if (quest.getWinners().isEmpty()) {
                    Text noWinners = Text.literal("[QUEST] ")
                            .formatted(Formatting.GOLD)
                            .append(Text.literal("Không có ai giải được bài!")
                                    .formatted(Formatting.RED));
                    player.sendMessage(noWinners, false);
                } else {
                    Text leaderboard = Text.literal("[QUEST] ")
                            .formatted(Formatting.GOLD)
                            .append(Text.literal("Bảng xếp hạng:")
                                    .formatted(Formatting.WHITE, Formatting.BOLD));
                    player.sendMessage(leaderboard, false);

                    for (Quest.Winner winner : quest.getWinners()) {
                        Formatting placeColor;
                        switch (winner.getPlace()) {
                            case 1:
                                placeColor = Formatting.GOLD;
                                break;
                            case 2:
                                placeColor = Formatting.GRAY;
                                break;
                            case 3:
                                placeColor = Formatting.RED;
                                break;
                            default:
                                placeColor = Formatting.WHITE;
                        }

                        Text winnerLine = Text.literal("  " + winner.getPlace() + ". ")
                                .formatted(placeColor, Formatting.BOLD)
                                .append(Text.literal(winner.getPlayerName())
                                        .formatted(Formatting.GREEN))
                                .append(Text.literal(" - " + winner.getFormattedTotalTime())
                                        .formatted(Formatting.AQUA))
                                .append(Text.literal(" (Phạt: " + winner.getPenaltyMinutes() + "m)")
                                        .formatted(Formatting.GRAY));
                        player.sendMessage(winnerLine, false);
                    }
                }

                player.sendMessage(header, false);
                player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        });
    }

    private void announceCancelled() {
        Text message = Text.literal("[QUEST] ")
                .formatted(Formatting.GOLD)
                .append(Text.literal("Nhiệm vụ đã bị hủy bởi admin.")
                        .formatted(Formatting.RED));

        server.execute(() -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.sendMessage(message, false);
            }
        });
    }

    private Formatting getRatingColor(int rating) {
        if (rating >= 2400) return Formatting.RED;
        if (rating >= 2100) return Formatting.GOLD;
        if (rating >= 1900) return Formatting.LIGHT_PURPLE;
        if (rating >= 1600) return Formatting.BLUE;
        if (rating >= 1400) return Formatting.AQUA;
        if (rating >= 1200) return Formatting.GREEN;
        return Formatting.GRAY;
    }

    /**
     * Get player by identifier (supports both UUID and offline username)
     */
    private ServerPlayerEntity getPlayer(String playerIdentifier) {
        return playerDataManager.getOnlinePlayer(playerIdentifier);
    }

    /**
     * Get player name by identifier
     */
    private String getPlayerName(String playerIdentifier) {
        ServerPlayerEntity player = getPlayer(playerIdentifier);
        if (player != null) {
            return player.getName().getString();
        }

        // If player is offline, try to get from stored data
        String storedName = playerDataManager.getPlayerName(playerIdentifier);
        if (storedName != null) {
            return storedName;
        }

        // Fallback for offline players: extract username from identifier
        if (playerDataManager.isOfflinePlayer(playerIdentifier)) {
            return playerIdentifier.substring("offline:".length());
        }

        return "Unknown";
    }

    public void saveActiveQuest() {
        if (activeQuest == null) {
            return;
        }

        Path questPath = getActiveQuestPath();

        try {
            Files.createDirectories(questPath.getParent());
            String json = GSON.toJson(activeQuest);
            Files.writeString(questPath, json);
        } catch (IOException e) {
            CFQuestMod.LOGGER.error("Lỗi khi lưu active quest: {}", e.getMessage());
        }
    }

    private void loadActiveQuest() {
        Path questPath = getActiveQuestPath();

        if (Files.exists(questPath)) {
            try {
                String json = Files.readString(questPath);
                activeQuest = GSON.fromJson(json, Quest.class);

                if (activeQuest.isExpired()) {
                    CFQuestMod.LOGGER.info("Quest đã lưu đã hết hạn, bỏ qua.");
                    activeQuest = null;
                    deleteSavedQuest();
                } else {
                    CFQuestMod.LOGGER.info("Đã tải quest đang hoạt động: {}",
                            activeQuest.getProblemDisplayName());
                }
            } catch (IOException e) {
                CFQuestMod.LOGGER.error("Lỗi khi tải active quest: {}", e.getMessage());
            }
        }
    }

    private void deleteSavedQuest() {
        Path questPath = getActiveQuestPath();
        try {
            Files.deleteIfExists(questPath);
        } catch (IOException e) {
            CFQuestMod.LOGGER.error("Lỗi khi xóa saved quest: {}", e.getMessage());
        }
    }

    private Path getActiveQuestPath() {
        return server.getSavePath(WorldSavePath.ROOT).resolve("cfquest").resolve("active_quest.json");
    }
}
