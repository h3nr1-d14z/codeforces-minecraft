package com.hieu.cfquest.reward;

import com.hieu.cfquest.CFQuestMod;
import com.hieu.cfquest.config.ModConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class RewardManager {
    private final ModConfig config;

    public RewardManager(ModConfig config) {
        this.config = config;
    }

    public void giveReward(ServerPlayerEntity player, int problemRating, int place) {
        ModConfig.RewardTier tier = config.getRewards().getTierForRating(problemRating);
        double multiplier = config.getRewards().getMultiplierForPlace(place);

        if (tier == null || multiplier <= 0) {
            CFQuestMod.LOGGER.warn("Không tìm thấy tier phần thưởng cho rating {} hoặc vị trí {}",
                    problemRating, place);
            return;
        }

        // Give items
        List<ItemStack> rewards = createRewardItems(tier, multiplier, problemRating, place);
        for (ItemStack stack : rewards) {
            if (!player.getInventory().insertStack(stack)) {
                // Drop item if inventory is full
                player.dropItem(stack, false);
            }
        }

        // Apply effects
        if (tier.hasEffect()) {
            applyEffect(player, tier.getEffect(), place);
        }

        // Play sound and show title
        player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        Text title = Text.literal("HẠNG " + place + "!")
                .formatted(getPlaceFormatting(place), Formatting.BOLD);
        Text subtitle = Text.literal("Phần thưởng đã được thêm vào túi đồ")
                .formatted(Formatting.GREEN);

        player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(title));
        player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(subtitle));

        CFQuestMod.LOGGER.info("Đã trao phần thưởng cho {} (Hạng {}, Rating {})",
                player.getName().getString(), place, problemRating);
    }

    private List<ItemStack> createRewardItems(ModConfig.RewardTier tier, double multiplier,
                                               int problemRating, int place) {
        List<ItemStack> items = new ArrayList<>();

        for (String itemId : tier.getItems()) {
            Item item = Registries.ITEM.get(Identifier.of(itemId));

            if (item == null) {
                CFQuestMod.LOGGER.warn("Không tìm thấy item: {}", itemId);
                continue;
            }

            // Calculate count based on multiplier
            int baseCount = 1;
            int count = Math.max(1, (int) Math.round(baseCount * multiplier));

            ItemStack stack = new ItemStack(item, count);

            // Add custom name and lore
            Text customName = Text.literal("Phần thưởng Quest")
                    .formatted(Formatting.GOLD, Formatting.BOLD);
            stack.set(DataComponentTypes.CUSTOM_NAME, customName);

            List<Text> loreLines = new ArrayList<>();
            loreLines.add(Text.literal("Hạng " + place)
                    .formatted(getPlaceFormatting(place)));
            loreLines.add(Text.literal("Độ khó: " + problemRating)
                    .formatted(getRatingFormatting(problemRating)));
            loreLines.add(Text.literal("Codeforces Quest")
                    .formatted(Formatting.DARK_GRAY, Formatting.ITALIC));

            stack.set(DataComponentTypes.LORE, new LoreComponent(loreLines));

            items.add(stack);
        }

        return items;
    }

    private void applyEffect(ServerPlayerEntity player, String effect, int place) {
        int durationTicks = 20 * 60 * 5; // 5 minutes base

        // Longer duration for higher places
        switch (place) {
            case 1:
                durationTicks = 20 * 60 * 10; // 10 minutes
                break;
            case 2:
                durationTicks = 20 * 60 * 7; // 7 minutes
                break;
            case 3:
                durationTicks = 20 * 60 * 5; // 5 minutes
                break;
        }

        switch (effect.toLowerCase()) {
            case "glowing":
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.GLOWING, durationTicks, 0, false, false, true));
                break;
            case "speed":
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SPEED, durationTicks, place == 1 ? 1 : 0, false, false, true));
                break;
            case "strength":
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.STRENGTH, durationTicks, 0, false, false, true));
                break;
            case "resistance":
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.RESISTANCE, durationTicks, 0, false, false, true));
                break;
            case "hero":
                // Multiple effects for elite tier
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.GLOWING, durationTicks, 0, false, false, true));
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SPEED, durationTicks, 0, false, false, true));
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.RESISTANCE, durationTicks, 0, false, false, true));
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.REGENERATION, durationTicks, 0, false, false, true));
                break;
            default:
                CFQuestMod.LOGGER.warn("Unknown effect: {}", effect);
        }
    }

    private Formatting getPlaceFormatting(int place) {
        return switch (place) {
            case 1 -> Formatting.GOLD;
            case 2 -> Formatting.GRAY;
            case 3 -> Formatting.RED;
            default -> Formatting.WHITE;
        };
    }

    private Formatting getRatingFormatting(int rating) {
        if (rating >= 2400) return Formatting.RED;
        if (rating >= 2100) return Formatting.GOLD;
        if (rating >= 1900) return Formatting.LIGHT_PURPLE;
        if (rating >= 1600) return Formatting.BLUE;
        if (rating >= 1400) return Formatting.AQUA;
        if (rating >= 1200) return Formatting.GREEN;
        return Formatting.GRAY;
    }
}
