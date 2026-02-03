package com.hieu.cfquest.reward;

import java.util.List;
import java.util.Map;

/**
 * Configuration for quest rewards.
 * This class is primarily for documentation - actual config is in ModConfig.
 */
public class RewardConfig {

    /**
     * Reward tier based on problem difficulty rating.
     */
    public static class Tier {
        private int minRating;
        private List<String> items;
        private String effect;

        public Tier(int minRating, List<String> items, String effect) {
            this.minRating = minRating;
            this.items = items;
            this.effect = effect;
        }

        public int getMinRating() {
            return minRating;
        }

        public List<String> getItems() {
            return items;
        }

        public String getEffect() {
            return effect;
        }
    }

    /**
     * Default reward configuration:
     *
     * Basic (800-1199): Diamond, Golden Apple
     * Mid (1300-1699): Diamond Block
     * High (1700-1999): Netherite Ingot
     * Elite (2000+): Netherite Block + Glowing effect
     *
     * Placement multipliers:
     * 1st place: 100% of items
     * 2nd place: 70% of items
     * 3rd place: 50% of items
     */
    public static Map<String, Tier> getDefaultTiers() {
        return Map.of(
                "basic", new Tier(800, List.of("minecraft:diamond", "minecraft:golden_apple"), null),
                "mid", new Tier(1300, List.of("minecraft:diamond_block"), null),
                "high", new Tier(1700, List.of("minecraft:netherite_ingot"), null),
                "elite", new Tier(2000, List.of("minecraft:netherite_block"), "glowing")
        );
    }

    public static Map<String, Double> getDefaultMultipliers() {
        return Map.of(
                "1", 1.0,
                "2", 0.7,
                "3", 0.5
        );
    }
}
