package com.hieu.cfquest.quest;

import com.hieu.cfquest.CFQuestMod;
import com.hieu.cfquest.api.CodeforcesAPI;
import com.hieu.cfquest.api.model.Problem;
import com.hieu.cfquest.config.ModConfig;
import net.minecraft.server.MinecraftServer;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class QuestScheduler {
    private final MinecraftServer server;
    private final QuestManager questManager;
    private final ModConfig config;
    private final CodeforcesAPI api;
    private final Random random = new Random();

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> scheduledTask;

    public QuestScheduler(MinecraftServer server, QuestManager questManager, ModConfig config) {
        this.server = server;
        this.questManager = questManager;
        this.config = config;
        this.api = new CodeforcesAPI(config);
    }

    public void start() {
        if (!config.getSchedule().isEnabled()) {
            return;
        }

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CFQuest-Scheduler");
            t.setDaemon(true);
            return t;
        });

        scheduleNextQuest();

        CFQuestMod.LOGGER.info("Đã bắt đầu Quest Scheduler");
    }

    public void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
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

        CFQuestMod.LOGGER.info("Đã dừng Quest Scheduler");
    }

    private void scheduleNextQuest() {
        if (executor == null || executor.isShutdown()) {
            return;
        }

        long delayMs = calculateNextRunDelay();

        if (delayMs > 0) {
            scheduledTask = executor.schedule(this::runScheduledQuest, delayMs, TimeUnit.MILLISECONDS);

            LocalDateTime nextRun = LocalDateTime.now().plus(delayMs, ChronoUnit.MILLIS);
            CFQuestMod.LOGGER.info("Quest tiếp theo được lên lịch vào: {}", nextRun);
        }
    }

    private void runScheduledQuest() {
        if (questManager.hasActiveQuest()) {
            CFQuestMod.LOGGER.info("Đã có quest đang chạy, bỏ qua quest theo lịch");
            scheduleNextQuest();
            return;
        }

        List<Integer> problemPool = config.getSchedule().getProblemPool();

        if (problemPool.isEmpty()) {
            CFQuestMod.LOGGER.warn("Problem pool trống, không thể bắt đầu quest theo lịch");
            scheduleNextQuest();
            return;
        }

        // Select random contest from pool
        int contestId = problemPool.get(random.nextInt(problemPool.size()));

        // Get problems from contest and select randomly
        api.getContestProblems(contestId).thenAccept(problems -> {
            if (problems.isEmpty()) {
                CFQuestMod.LOGGER.warn("Không thể lấy bài từ contest {}", contestId);
                scheduleNextQuest();
                return;
            }

            Problem problem = problems.get(random.nextInt(problems.size()));

            server.execute(() -> {
                boolean started = questManager.startQuest(
                        contestId,
                        problem,
                        config.getQuest().getDefaultTimeoutMinutes()
                );

                if (started) {
                    CFQuestMod.LOGGER.info("Đã bắt đầu quest theo lịch: {} từ contest {}",
                            problem.getName(), contestId);
                }

                scheduleNextQuest();
            });
        }).exceptionally(e -> {
            CFQuestMod.LOGGER.error("Lỗi khi bắt đầu quest theo lịch: {}", e.getMessage());
            scheduleNextQuest();
            return null;
        });
    }

    private long calculateNextRunDelay() {
        String cron = config.getSchedule().getCron();

        // Simple cron parser: "minute hour dayOfMonth month dayOfWeek"
        // Example: "0 18 * * SAT" = Every Saturday at 18:00
        String[] parts = cron.split("\\s+");

        if (parts.length != 5) {
            CFQuestMod.LOGGER.error("Invalid cron format: {}", cron);
            return -1;
        }

        try {
            int minute = parts[0].equals("*") ? 0 : Integer.parseInt(parts[0]);
            int hour = parts[1].equals("*") ? 0 : Integer.parseInt(parts[1]);
            String dayOfMonth = parts[2];
            String month = parts[3];
            String dayOfWeek = parts[4];

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nextRun = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);

            // Handle day of week
            if (!dayOfWeek.equals("*")) {
                DayOfWeek targetDay = parseDayOfWeek(dayOfWeek);

                if (targetDay != null) {
                    // Find next occurrence of this day
                    while (nextRun.getDayOfWeek() != targetDay || !nextRun.isAfter(now)) {
                        nextRun = nextRun.plusDays(1);
                    }
                }
            } else {
                // Daily at specified time
                if (!nextRun.isAfter(now)) {
                    nextRun = nextRun.plusDays(1);
                }
            }

            return ChronoUnit.MILLIS.between(now, nextRun);
        } catch (Exception e) {
            CFQuestMod.LOGGER.error("Lỗi parse cron: {}", e.getMessage());
            return -1;
        }
    }

    private DayOfWeek parseDayOfWeek(String day) {
        return switch (day.toUpperCase()) {
            case "MON", "1" -> DayOfWeek.MONDAY;
            case "TUE", "2" -> DayOfWeek.TUESDAY;
            case "WED", "3" -> DayOfWeek.WEDNESDAY;
            case "THU", "4" -> DayOfWeek.THURSDAY;
            case "FRI", "5" -> DayOfWeek.FRIDAY;
            case "SAT", "6" -> DayOfWeek.SATURDAY;
            case "SUN", "0", "7" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    public void triggerNow() {
        if (executor != null && !executor.isShutdown()) {
            executor.execute(this::runScheduledQuest);
        }
    }
}
