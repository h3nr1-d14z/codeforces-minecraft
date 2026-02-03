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

/**
 * Manages player data and CF handle linking.
 * Supports both premium players (UUID-based) and crack players (username-based).
 */
public class PlayerDataManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final MinecraftServer server;
    private final Path dataPath;

    // Maps player identifier (UUID string or username) to PlayerData
    private Map<String, PlayerData> playerData = new HashMap<>();

    public static class PlayerData {
        private String identifier; // UUID string or username
        private String playerName; // Last known player name
        private String cfHandle;
        private long linkTime;
        private int totalSolves;
        private int totalWins;
        private boolean isPremium; // true if UUID-based, false if username-based

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
        load();
    }

    /**
     * Get the unique identifier for a player.
     * For premium/online-mode players: returns UUID string
     * For crack/offline-mode players: returns username (prefixed with "offline:")
     */
    public String getPlayerIdentifier(ServerPlayerEntity player) {
        // Check if server is in online mode
        if (server.isOnlineMode()) {
            return player.getUuid().toString();
        } else {
            // Offline mode - use username with prefix to distinguish
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
        return playerData.computeIfAbsent(identifier, k -> {
            PlayerData data = new PlayerData(identifier, player.getName().getString(), server.isOnlineMode());
            return data;
        });
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
        data.setPlayerName(player.getName().getString()); // Update name in case it changed
        save();
    }

    /**
     * Unlink a player from their Codeforces handle
     */
    public void unlinkPlayer(ServerPlayerEntity player) {
        PlayerData data = getPlayerData(player);
        data.setCfHandle(null);
        data.setLinkTime(0);
        save();
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
     * Get player name by identifier (useful for offline players)
     */
    public String getPlayerName(String identifier) {
        PlayerData data = playerData.get(identifier);
        return data != null ? data.getPlayerName() : null;
    }

    /**
     * Get all linked players as a map of identifier -> cfHandle
     */
    public Map<String, String> getAllLinkedPlayers() {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, PlayerData> entry : playerData.entrySet()) {
            if (entry.getValue().isLinked()) {
                result.put(entry.getKey(), entry.getValue().getCfHandle());
            }
        }
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
            save();
        }
    }

    /**
     * Increment win count for a player
     */
    public void incrementWins(String identifier) {
        PlayerData data = playerData.get(identifier);
        if (data != null) {
            data.setTotalWins(data.getTotalWins() + 1);
            save();
        }
    }

    /**
     * Get a ServerPlayerEntity by identifier (supports both UUID and offline username)
     */
    public ServerPlayerEntity getOnlinePlayer(String identifier) {
        if (isOfflinePlayer(identifier)) {
            // Offline player - extract username and find by name
            String username = identifier.substring("offline:".length());
            return server.getPlayerManager().getPlayer(username);
        } else {
            // Premium player - parse UUID
            try {
                UUID uuid = UUID.fromString(identifier);
                return server.getPlayerManager().getPlayer(uuid);
            } catch (IllegalArgumentException e) {
                // Fallback: try as username (for backwards compatibility)
                return server.getPlayerManager().getPlayer(identifier);
            }
        }
    }

    public void save() {
        try {
            Files.createDirectories(dataPath.getParent());
            String json = GSON.toJson(playerData);
            Files.writeString(dataPath, json);
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
                    playerData = loaded;
                }
                CFQuestMod.LOGGER.info("Đã tải {} player data", playerData.size());
            } catch (IOException e) {
                CFQuestMod.LOGGER.error("Lỗi khi tải player data: {}", e.getMessage());
            }
        }
    }
}
