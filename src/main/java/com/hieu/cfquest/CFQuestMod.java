package com.hieu.cfquest;

import com.hieu.cfquest.api.CodeforcesPoller;
import com.hieu.cfquest.command.CFAdminCommand;
import com.hieu.cfquest.command.CFLinkCommand;
import com.hieu.cfquest.command.CFQuestCommand;
import com.hieu.cfquest.config.ModConfig;
import com.hieu.cfquest.quest.QuestManager;
import com.hieu.cfquest.quest.QuestScheduler;
import com.hieu.cfquest.reward.RewardManager;
import com.hieu.cfquest.storage.PlayerDataManager;
import com.hieu.cfquest.storage.QuestHistory;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CFQuestMod implements DedicatedServerModInitializer {
    public static final String MOD_ID = "cfquest";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static CFQuestMod instance;
    private MinecraftServer server;

    private ModConfig config;
    private PlayerDataManager playerDataManager;
    private QuestManager questManager;
    private QuestScheduler questScheduler;
    private CodeforcesPoller codeforcesPoller;
    private RewardManager rewardManager;
    private QuestHistory questHistory;

    @Override
    public void onInitializeServer() {
        instance = this;
        LOGGER.info("Đang khởi tạo Codeforces Quest Mod...");

        // Register server lifecycle events
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CFLinkCommand.register(dispatcher);
            CFQuestCommand.register(dispatcher);
            CFAdminCommand.register(dispatcher);
        });

        LOGGER.info("Codeforces Quest Mod đã khởi tạo thành công!");
    }

    private void onServerStarting(MinecraftServer server) {
        this.server = server;

        // Load configuration
        this.config = ModConfig.load(server);

        // Initialize managers
        this.playerDataManager = new PlayerDataManager(server);
        this.questHistory = new QuestHistory(server);
        this.rewardManager = new RewardManager(config);
        this.questManager = new QuestManager(server, playerDataManager, rewardManager, questHistory, config);
        this.codeforcesPoller = new CodeforcesPoller(config, questManager, playerDataManager);
        this.questScheduler = new QuestScheduler(server, questManager, config);

        LOGGER.info("Đã tải cấu hình và khởi tạo các manager.");
    }

    private void onServerStarted(MinecraftServer server) {
        // Start the poller
        codeforcesPoller.start();

        // Start scheduler if enabled
        if (config.getSchedule().isEnabled()) {
            questScheduler.start();
            LOGGER.info("Đã bật lịch trình quest tự động.");
        }

        LOGGER.info("Codeforces Quest Mod đã sẵn sàng!");
    }

    private void onServerStopping(MinecraftServer server) {
        LOGGER.info("Đang dừng Codeforces Quest Mod...");

        // Stop poller and scheduler
        if (codeforcesPoller != null) {
            codeforcesPoller.stop();
        }
        if (questScheduler != null) {
            questScheduler.stop();
        }

        // Save data
        if (playerDataManager != null) {
            playerDataManager.save();
        }
        if (questHistory != null) {
            questHistory.save();
        }
        if (questManager != null && questManager.hasActiveQuest()) {
            questManager.saveActiveQuest();
        }

        LOGGER.info("Codeforces Quest Mod đã dừng.");
    }

    public static CFQuestMod getInstance() {
        return instance;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public ModConfig getConfig() {
        return config;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public QuestManager getQuestManager() {
        return questManager;
    }

    public QuestScheduler getQuestScheduler() {
        return questScheduler;
    }

    public CodeforcesPoller getCodeforcesPoller() {
        return codeforcesPoller;
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }

    public QuestHistory getQuestHistory() {
        return questHistory;
    }
}
