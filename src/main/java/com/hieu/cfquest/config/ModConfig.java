package com.hieu.cfquest.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hieu.cfquest.CFQuestMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private CodeforcesConfig codeforces = new CodeforcesConfig();
    private QuestConfig quest = new QuestConfig();
    private RewardsConfig rewards = new RewardsConfig();
    private ScheduleConfig schedule = new ScheduleConfig();

    public static class CodeforcesConfig {
        private String apiKey = "";
        private String apiSecret = "";
        private int pollIntervalSeconds = 30;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiSecret() {
            return apiSecret;
        }

        public void setApiSecret(String apiSecret) {
            this.apiSecret = apiSecret;
        }

        public int getPollIntervalSeconds() {
            return pollIntervalSeconds;
        }

        public void setPollIntervalSeconds(int pollIntervalSeconds) {
            this.pollIntervalSeconds = pollIntervalSeconds;
        }

        public boolean hasCredentials() {
            return apiKey != null && !apiKey.isEmpty() && apiSecret != null && !apiSecret.isEmpty();
        }
    }

    public static class QuestConfig {
        private int defaultTimeoutMinutes = 60;
        private int penaltyMinutes = 20;
        private int maxWinners = 3;

        public int getDefaultTimeoutMinutes() {
            return defaultTimeoutMinutes;
        }

        public void setDefaultTimeoutMinutes(int defaultTimeoutMinutes) {
            this.defaultTimeoutMinutes = defaultTimeoutMinutes;
        }

        public int getPenaltyMinutes() {
            return penaltyMinutes;
        }

        public void setPenaltyMinutes(int penaltyMinutes) {
            this.penaltyMinutes = penaltyMinutes;
        }

        public int getMaxWinners() {
            return maxWinners;
        }

        public void setMaxWinners(int maxWinners) {
            this.maxWinners = maxWinners;
        }
    }

    public static class RewardsConfig {
        private Map<String, RewardTier> tiers = new HashMap<>();
        private Map<String, Double> placementMultipliers = new HashMap<>();

        public RewardsConfig() {
            // Default tiers
            tiers.put("basic", new RewardTier(800, List.of("minecraft:diamond", "minecraft:golden_apple"), null));
            tiers.put("mid", new RewardTier(1300, List.of("minecraft:diamond_block"), null));
            tiers.put("high", new RewardTier(1700, List.of("minecraft:netherite_ingot"), null));
            tiers.put("elite", new RewardTier(2000, List.of("minecraft:netherite_block"), "glowing"));

            // Default placement multipliers
            placementMultipliers.put("1", 1.0);
            placementMultipliers.put("2", 0.7);
            placementMultipliers.put("3", 0.5);
        }

        public Map<String, RewardTier> getTiers() {
            return tiers;
        }

        public void setTiers(Map<String, RewardTier> tiers) {
            this.tiers = tiers;
        }

        public Map<String, Double> getPlacementMultipliers() {
            return placementMultipliers;
        }

        public void setPlacementMultipliers(Map<String, Double> placementMultipliers) {
            this.placementMultipliers = placementMultipliers;
        }

        public double getMultiplierForPlace(int place) {
            return placementMultipliers.getOrDefault(String.valueOf(place), 0.0);
        }

        public RewardTier getTierForRating(int rating) {
            RewardTier best = null;
            int bestMinRating = -1;

            for (RewardTier tier : tiers.values()) {
                if (rating >= tier.getMinRating() && tier.getMinRating() > bestMinRating) {
                    best = tier;
                    bestMinRating = tier.getMinRating();
                }
            }

            return best != null ? best : tiers.get("basic");
        }
    }

    public static class RewardTier {
        private int minRating;
        private List<String> items;
        private String effect;

        public RewardTier() {
        }

        public RewardTier(int minRating, List<String> items, String effect) {
            this.minRating = minRating;
            this.items = items;
            this.effect = effect;
        }

        public int getMinRating() {
            return minRating;
        }

        public void setMinRating(int minRating) {
            this.minRating = minRating;
        }

        public List<String> getItems() {
            return items;
        }

        public void setItems(List<String> items) {
            this.items = items;
        }

        public String getEffect() {
            return effect;
        }

        public void setEffect(String effect) {
            this.effect = effect;
        }

        public boolean hasEffect() {
            return effect != null && !effect.isEmpty();
        }
    }

    public static class ScheduleConfig {
        private boolean enabled = false;
        private String cron = "0 18 * * SAT";
        private List<Integer> problemPool = List.of();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public List<Integer> getProblemPool() {
            return problemPool;
        }

        public void setProblemPool(List<Integer> problemPool) {
            this.problemPool = problemPool;
        }
    }

    public CodeforcesConfig getCodeforces() {
        return codeforces;
    }

    public QuestConfig getQuest() {
        return quest;
    }

    public RewardsConfig getRewards() {
        return rewards;
    }

    public ScheduleConfig getSchedule() {
        return schedule;
    }

    public static ModConfig load(MinecraftServer server) {
        Path configPath = getConfigPath(server);

        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                ModConfig config = GSON.fromJson(json, ModConfig.class);
                CFQuestMod.LOGGER.info("Đã tải cấu hình từ: {}", configPath);
                return config;
            } catch (IOException e) {
                CFQuestMod.LOGGER.error("Lỗi khi tải cấu hình: {}", e.getMessage());
            }
        }

        // Create default config
        ModConfig config = new ModConfig();
        config.save(server);
        return config;
    }

    public void save(MinecraftServer server) {
        Path configPath = getConfigPath(server);

        try {
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(this);
            Files.writeString(configPath, json);
            CFQuestMod.LOGGER.info("Đã lưu cấu hình vào: {}", configPath);
        } catch (IOException e) {
            CFQuestMod.LOGGER.error("Lỗi khi lưu cấu hình: {}", e.getMessage());
        }
    }

    private static Path getConfigPath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("cfquest").resolve("config.json");
    }
}
