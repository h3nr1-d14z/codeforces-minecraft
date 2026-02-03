package com.hieu.cfquest.api;

import com.hieu.cfquest.CFQuestMod;
import com.hieu.cfquest.config.ModConfig;
import com.hieu.cfquest.quest.Quest;
import com.hieu.cfquest.quest.QuestManager;
import com.hieu.cfquest.storage.PlayerDataManager;
import net.minecraft.server.MinecraftServer;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Background poller cho Codeforces API.
 *
 * Optimizations:
 * - Chạy trên dedicated daemon thread
 * - Sử dụng server.execute() cho Minecraft operations
 * - Không block server tick
 */
public class CodeforcesPoller {
    private final ModConfig config;
    private final QuestManager questManager;
    private final PlayerDataManager playerDataManager;
    private final CodeforcesAPI api;
    private final MinecraftServer server;

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> pollTask;
    private volatile boolean running = false;

    public CodeforcesPoller(ModConfig config, QuestManager questManager, PlayerDataManager playerDataManager) {
        this.config = config;
        this.questManager = questManager;
        this.playerDataManager = playerDataManager;
        this.api = new CodeforcesAPI(config);
        this.server = CFQuestMod.getInstance().getServer();
    }

    public void start() {
        if (running) {
            return;
        }

        running = true;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CFQuest-Poller");
            t.setDaemon(true);
            return t;
        });

        int intervalSeconds = config.getCodeforces().getPollIntervalSeconds();
        pollTask = executor.scheduleAtFixedRate(this::poll, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);

        CFQuestMod.LOGGER.info("Đã bắt đầu Codeforces Poller (interval: {}s)", intervalSeconds);
    }

    public void stop() {
        running = false;

        if (pollTask != null) {
            pollTask.cancel(false);
            pollTask = null;
        }

        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executor = null;
        }

        CFQuestMod.LOGGER.info("Đã dừng Codeforces Poller");
    }

    public CodeforcesAPI getApi() {
        return api;
    }

    private void poll() {
        if (!questManager.hasActiveQuest()) {
            return;
        }

        Quest quest = questManager.getActiveQuest();

        // Check if quest has timed out
        if (quest.isExpired()) {
            CFQuestMod.LOGGER.info("Quest đã hết thời gian, đang kết thúc...");
            questManager.endQuest(false);
            return;
        }

        // Check if all winner slots are filled
        if (quest.getWinners().size() >= config.getQuest().getMaxWinners()) {
            CFQuestMod.LOGGER.info("Đã đủ người thắng, đang kết thúc quest...");
            questManager.endQuest(false);
            return;
        }

        // Get all linked handles that haven't won yet
        Set<String> handlesToCheck = new HashSet<>();
        Map<String, String> linkedPlayers = playerDataManager.getAllLinkedPlayers();

        for (Map.Entry<String, String> entry : linkedPlayers.entrySet()) {
            String cfHandle = entry.getValue();
            if (!quest.hasWon(cfHandle)) {
                handlesToCheck.add(cfHandle);
            }
        }

        if (handlesToCheck.isEmpty()) {
            return;
        }

        // Poll contest standings
        try {
            api.getContestStandings(quest.getContestId(), handlesToCheck)
                    .thenAccept(standings -> processStandings(quest, standings, linkedPlayers))
                    .exceptionally(e -> {
                        CFQuestMod.LOGGER.error("Lỗi khi poll standings: {}", e.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            CFQuestMod.LOGGER.error("Lỗi khi poll Codeforces: {}", e.getMessage());
        }
    }

    private void processStandings(Quest quest, Map<String, CodeforcesAPI.StandingsEntry> standings,
                                  Map<String, String> linkedPlayers) {
        if (standings.isEmpty()) {
            return;
        }

        // Process on server main thread to ensure thread-safety for Minecraft operations
        server.execute(() -> {
            String problemIndex = quest.getProblemIndex();

            for (Map.Entry<String, String> entry : linkedPlayers.entrySet()) {
                String playerUuid = entry.getKey();
                String cfHandle = entry.getValue().toLowerCase();

                // Skip if already won
                if (quest.hasWon(cfHandle)) {
                    continue;
                }

                CodeforcesAPI.StandingsEntry standingsEntry = standings.get(cfHandle);
                if (standingsEntry == null) {
                    continue;
                }

                CodeforcesAPI.ProblemResult problemResult = standingsEntry.getProblemResult(problemIndex);
                if (problemResult == null) {
                    continue;
                }

                // Check for new wrong answers
                int currentWrongCount = quest.getPenaltyCount(cfHandle);
                int newWrongCount = problemResult.rejectedAttemptCount;

                if (newWrongCount > currentWrongCount) {
                    // New wrong answers detected
                    int newWrongs = newWrongCount - currentWrongCount;
                    quest.setPenaltyCount(cfHandle, newWrongCount);

                    // Notify player about wrong answer
                    questManager.notifyWrongAnswer(playerUuid, cfHandle, newWrongs, newWrongCount);
                }

                // Check if solved
                if (problemResult.isSolved()) {
                    long solveTimeSeconds = problemResult.bestSubmissionTimeSeconds;

                    // Check if this submission was made after quest started
                    long questStartTime = quest.getStartTime();
                    long submissionAbsoluteTime = questStartTime + (solveTimeSeconds * 1000);

                    if (submissionAbsoluteTime >= questStartTime) {
                        // Calculate penalty time
                        int penaltyMinutes = newWrongCount * config.getQuest().getPenaltyMinutes();

                        // Record the solve
                        int place = questManager.recordSolve(playerUuid, cfHandle, solveTimeSeconds, penaltyMinutes);

                        if (place > 0) {
                            CFQuestMod.LOGGER.info("Người chơi {} ({}) đã giải bài! Hạng: {}, Thời gian: {}s, Phạt: {}m",
                                    playerUuid, cfHandle, place, solveTimeSeconds, penaltyMinutes);
                        }
                    }
                }
            }
        });
    }

    public void forcePoll() {
        if (running && questManager.hasActiveQuest()) {
            executor.execute(this::poll);
        }
    }
}
