package com.hieu.cfquest.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hieu.cfquest.CFQuestMod;
import com.hieu.cfquest.quest.Quest;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stores history of completed quests.
 */
public class QuestHistory {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_HISTORY_SIZE = 100;

    private final MinecraftServer server;
    private final Path historyPath;

    private List<QuestRecord> history = new ArrayList<>();

    public static class QuestRecord {
        private int contestId;
        private String problemIndex;
        private String problemName;
        private int problemRating;
        private long startTime;
        private long endTime;
        private List<WinnerRecord> winners = new ArrayList<>();

        public QuestRecord() {
        }

        public QuestRecord(Quest quest) {
            this.contestId = quest.getContestId();
            this.problemIndex = quest.getProblemIndex();
            this.problemName = quest.getProblemName();
            this.problemRating = quest.getProblemRating();
            this.startTime = quest.getStartTime();
            this.endTime = System.currentTimeMillis();

            for (Quest.Winner winner : quest.getWinners()) {
                winners.add(new WinnerRecord(winner));
            }
        }

        public int getContestId() {
            return contestId;
        }

        public String getProblemIndex() {
            return problemIndex;
        }

        public String getProblemName() {
            return problemName;
        }

        public int getProblemRating() {
            return problemRating;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getEndTime() {
            return endTime;
        }

        public List<WinnerRecord> getWinners() {
            return winners;
        }

        public String getProblemIdentifier() {
            return contestId + problemIndex;
        }
    }

    public static class WinnerRecord {
        private String playerIdentifier;
        private String playerName;
        private String cfHandle;
        private int place;
        private long solveTimeSeconds;
        private int penaltyMinutes;

        public WinnerRecord() {
        }

        public WinnerRecord(Quest.Winner winner) {
            this.playerIdentifier = winner.getPlayerUuid();
            this.playerName = winner.getPlayerName();
            this.cfHandle = winner.getCfHandle();
            this.place = winner.getPlace();
            this.solveTimeSeconds = winner.getSolveTimeSeconds();
            this.penaltyMinutes = winner.getPenaltyMinutes();
        }

        public String getPlayerIdentifier() {
            return playerIdentifier;
        }

        public String getPlayerName() {
            return playerName;
        }

        public String getCfHandle() {
            return cfHandle;
        }

        public int getPlace() {
            return place;
        }

        public long getSolveTimeSeconds() {
            return solveTimeSeconds;
        }

        public int getPenaltyMinutes() {
            return penaltyMinutes;
        }
    }

    public QuestHistory(MinecraftServer server) {
        this.server = server;
        this.historyPath = server.getSavePath(WorldSavePath.ROOT).resolve("cfquest").resolve("history.json");
        load();
    }

    public void addQuest(Quest quest) {
        QuestRecord record = new QuestRecord(quest);
        history.add(record);

        // Trim history if too large
        while (history.size() > MAX_HISTORY_SIZE) {
            history.remove(0);
        }

        save();
    }

    public List<QuestRecord> getHistory() {
        return new ArrayList<>(history);
    }

    public List<QuestRecord> getRecentHistory(int count) {
        int start = Math.max(0, history.size() - count);
        return new ArrayList<>(history.subList(start, history.size()));
    }

    public QuestRecord getLastQuest() {
        if (history.isEmpty()) {
            return null;
        }
        return history.get(history.size() - 1);
    }

    /**
     * Get leaderboard stats for all players
     */
    public List<LeaderboardEntry> getLeaderboard() {
        List<LeaderboardEntry> leaderboard = new ArrayList<>();

        // Aggregate stats from history
        java.util.Map<String, LeaderboardEntry> stats = new java.util.HashMap<>();

        for (QuestRecord quest : history) {
            for (WinnerRecord winner : quest.getWinners()) {
                String id = winner.getPlayerIdentifier();
                LeaderboardEntry entry = stats.computeIfAbsent(id, k -> {
                    LeaderboardEntry e = new LeaderboardEntry();
                    e.playerIdentifier = id;
                    e.playerName = winner.getPlayerName();
                    return e;
                });

                entry.totalSolves++;
                if (winner.getPlace() == 1) {
                    entry.firstPlaces++;
                } else if (winner.getPlace() == 2) {
                    entry.secondPlaces++;
                } else if (winner.getPlace() == 3) {
                    entry.thirdPlaces++;
                }

                // Update player name to most recent
                entry.playerName = winner.getPlayerName();
            }
        }

        leaderboard.addAll(stats.values());

        // Sort by first places, then second places, then third places, then total solves
        leaderboard.sort(Comparator
                .comparingInt((LeaderboardEntry e) -> e.firstPlaces).reversed()
                .thenComparingInt(e -> e.secondPlaces).reversed()
                .thenComparingInt(e -> e.thirdPlaces).reversed()
                .thenComparingInt(e -> e.totalSolves).reversed());

        return leaderboard;
    }

    public static class LeaderboardEntry {
        public String playerIdentifier;
        public String playerName;
        public int totalSolves;
        public int firstPlaces;
        public int secondPlaces;
        public int thirdPlaces;

        public int getScore() {
            return firstPlaces * 3 + secondPlaces * 2 + thirdPlaces;
        }
    }

    public void save() {
        try {
            Files.createDirectories(historyPath.getParent());
            String json = GSON.toJson(history);
            Files.writeString(historyPath, json);
        } catch (IOException e) {
            CFQuestMod.LOGGER.error("Lỗi khi lưu quest history: {}", e.getMessage());
        }
    }

    private void load() {
        if (Files.exists(historyPath)) {
            try {
                String json = Files.readString(historyPath);
                Type type = new TypeToken<List<QuestRecord>>() {}.getType();
                List<QuestRecord> loaded = GSON.fromJson(json, type);
                if (loaded != null) {
                    history = loaded;
                }
                CFQuestMod.LOGGER.info("Đã tải {} quest records", history.size());
            } catch (IOException e) {
                CFQuestMod.LOGGER.error("Lỗi khi tải quest history: {}", e.getMessage());
            }
        }
    }
}
