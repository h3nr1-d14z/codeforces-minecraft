package com.hieu.cfquest.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hieu.cfquest.CFQuestMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages player data and CF handle linking.
 * Supports both premium players (UUID-based) and crack players (username-based).
 *
 * Optimizations:
 * - Thread-safe ConcurrentHashMap
 * - Async save with dirty flag (không block main thread)
 * - Periodic auto-save thay vì save mỗi thay đổi
 */
public class PlayerDataManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int AUTO_SAVE_INTERVAL_SECONDS = 60; // Auto-save mỗi 60s

    private final MinecraftServer server;
    private final Path dataPath;

    // Thread-safe map
    private final ConcurrentHashMap<String, PlayerData> playerData = new ConcurrentHashMap<>();

    // Dirty flag để biết có cần save không
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    // Async save executor
    private final ScheduledExecutorService saveExecutor;

    public static class PlayerData {
        private String identifier;
        private String playerName;
        private String cfHandle;
        private long linkTime;
        private int totalSolves;
        private int totalWins;
        private boolean isPremium;

        public PlayerData() {
        }

        public PlayerData(String identifier, String playerName, boolean isPremium) {
            this.identifier = identifier;
            this.playerName = playerName;
            this.isPremium = isPremium;
        }

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }

        public String getPlayerName() {
            return playerName;
        }

        public void setPlayerName(String playerName) {
            this.playerName = playerName;
        }

        public String getCfHandle() {
            return cfHandle;
        }

        public void setCfHandle(String cfHandle) {
            this.cfHandle = cfHandle;
        }

        public long getLinkTime() {
            return linkTime;
        }

        public void setLinkTime(long linkTime) {
            this.linkTime = linkTime;
        }

        public int getTotalSolves() {
            return totalSolves;
        }

        public void setTotalSolves(int totalSolves) {
            this.totalSolves = totalSolves;
        }

        public int getTotalWins() {
            return totalWins;
        }

        public void setTotalWins(int totalWins) {
            this.totalWins = totalWins;
        }

        public boolean isPremium() {
            return isPremium;
        }

        public void setPremium(boolean premium) {
            isPremium = premium;
        }

        public boolean isLinked() {
            return cfHandle != null && !cfHandle.isEmpty();
        }
    }

    public PlayerDataManager(MinecraftServer server) {
        this.server = server;
        this.dataPath = server.getSavePath(WorldSavePath.ROOT).resolve("cfquest").resolve("players.json");

        // Create save executor with daemon thread
        this.saveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CFQuest-DataSaver");
            t.setDaemon(true);
            return t;
        });

        // Load data synchronously on startup
        load();

        // Schedule periodic auto-save
        saveExecutor.scheduleAtFixedRate(
                this::saveIfDirty,
                AUTO_SAVE_INTERVAL_SECONDS,
                AUTO_SAVE_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    /**
     * Mark data as dirty (needs save)
     */
    private void markDirty() {
        dirty.set(true);
    }

    /**
     * Save only if dirty flag is set
     */
    private void saveIfDirty() {
        if (dirty.compareAndSet(true, false)) {
            saveInternal();
        }
    }

    /**
     * Get the unique identifier for a player.
     */
    public String getPlayerIdentifier(ServerPlayerEntity player) {
        if (server.isOnlineMode()) {
            return player.getUuid().toString();
        } else {
            return "offline:" + player.getName().getString().toLowerCase();
        }
    }

    /**
     * Check if a player identifier is for an offline/crack player
     */
    public boolean isOfflinePlayer(String identifier) {
        return identifier.startsWith("offline:");
    }

    /**
     * Get the player data for a player
     */
    public PlayerData getPlayerData(ServerPlayerEntity player) {
        String identifier = getPlayerIdentifier(player);
        return playerData.computeIfAbsent(identifier, k ->
            new PlayerData(identifier, player.getName().getString(), server.isOnlineMode())
        );
    }

    /**
     * Get player data by identifier
     */
    public PlayerData getPlayerDataByIdentifier(String identifier) {
        return playerData.get(identifier);
    }

    /**
     * Link a player to a Codeforces handle
     */
    public void linkPlayer(ServerPlayerEntity player, String cfHandle) {
        PlayerData data = getPlayerData(player);
        data.setCfHandle(cfHandle);
        data.setLinkTime(System.currentTimeMillis());
        data.setPlayerName(player.getName().getString());
        markDirty();
    }

    /**
     * Unlink a player from their Codeforces handle
     */
    public void unlinkPlayer(ServerPlayerEntity player) {
        PlayerData data = getPlayerData(player);
        data.setCfHandle(null);
        data.setLinkTime(0);
        markDirty();
    }

    /**
     * Check if a player is linked to a CF handle
     */
    public boolean isLinked(ServerPlayerEntity player) {
        PlayerData data = playerData.get(getPlayerIdentifier(player));
        return data != null && data.isLinked();
    }

    /**
     * Get the CF handle for a player
     */
    public String getCfHandle(ServerPlayerEntity player) {
        PlayerData data = playerData.get(getPlayerIdentifier(player));
        return data != null ? data.getCfHandle() : null;
    }

    /**
     * Get player name by identifier
     */
    public String getPlayerName(String identifier) {
        PlayerData data = playerData.get(identifier);
        return data != null ? data.getPlayerName() : null;
    }

    /**
     * Get all linked players as a map of identifier -> cfHandle
     * Returns a snapshot copy (thread-safe)
     */
    public Map<String, String> getAllLinkedPlayers() {
        Map<String, String> result = new HashMap<>();
        playerData.forEach((key, value) -> {
            if (value.isLinked()) {
                result.put(key, value.getCfHandle());
            }
        });
        return result;
    }

    /**
     * Find player identifier by CF handle
     */
    public String findPlayerByHandle(String cfHandle) {
        String handleLower = cfHandle.toLowerCase();
        for (Map.Entry<String, PlayerData> entry : playerData.entrySet()) {
            if (entry.getValue().isLinked() &&
                    entry.getValue().getCfHandle().equalsIgnoreCase(handleLower)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Check if a CF handle is already linked to another player
     */
    public boolean isHandleLinked(String cfHandle) {
        return findPlayerByHandle(cfHandle) != null;
    }

    /**
     * Increment solve count for a player
     */
    public void incrementSolves(String identifier) {
        PlayerData data = playerData.get(identifier);
        if (data != null) {
            data.setTotalSolves(data.getTotalSolves() + 1);
            markDirty();
        }
    }

    /**
     * Increment win count for a player
     */
    public void incrementWins(String identifier) {
        PlayerData data = playerData.get(identifier);
        if (data != null) {
            data.setTotalWins(data.getTotalWins() + 1);
            markDirty();
        }
    }

    /**
     * Get a ServerPlayerEntity by identifier
     */
    public ServerPlayerEntity getOnlinePlayer(String identifier) {
        if (isOfflinePlayer(identifier)) {
            String username = identifier.substring("offline:".length());
            return server.getPlayerManager().getPlayer(username);
        } else {
            try {
                UUID uuid = UUID.fromString(identifier);
                return server.getPlayerManager().getPlayer(uuid);
            } catch (IllegalArgumentException e) {
                return server.getPlayerManager().getPlayer(identifier);
            }
        }
    }

    /**
     * Public save method - schedules async save
     */
    public void save() {
        markDirty();
        // Force immediate save on shutdown
        saveExecutor.execute(this::saveIfDirty);
    }

    /**
     * Internal save implementation
     */
    private void saveInternal() {
        try {
            Files.createDirectories(dataPath.getParent());
            // Create snapshot of data for thread-safety
            Map<String, PlayerData> snapshot = new HashMap<>(playerData);
            String json = GSON.toJson(snapshot);
            Files.writeString(dataPath, json);
            CFQuestMod.LOGGER.debug("Đã lưu {} player data", snapshot.size());
        } catch (IOException e) {
            CFQuestMod.LOGGER.error("Lỗi khi lưu player data: {}", e.getMessage());
        }
    }

    private void load() {
        if (Files.exists(dataPath)) {
            try {
                String json = Files.readString(dataPath);
                Type type = new TypeToken<Map<String, PlayerData>>() {}.getType();
                Map<String, PlayerData> loaded = GSON.fromJson(json, type);
                if (loaded != null) {
                    playerData.putAll(loaded);
                }
                CFQuestMod.LOGGER.info("Đã tải {} player data", playerData.size());
            } catch (IOException e) {
                CFQuestMod.LOGGER.error("Lỗi khi tải player data: {}", e.getMessage());
            }
        }
    }

    /**
     * Shutdown - save and cleanup
     */
    public void shutdown() {
        // Force final save
        saveInternal();

        // Shutdown executor
        saveExecutor.shutdown();
        try {
            if (!saveExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                saveExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            saveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
