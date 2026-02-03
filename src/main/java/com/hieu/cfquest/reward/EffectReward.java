package com.hieu.cfquest.reward;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles special effect rewards for quest winners.
 */
public class EffectReward {

    public enum EffectType {
        GLOWING("glowing", StatusEffects.GLOWING),
        SPEED("speed", StatusEffects.SPEED),
        STRENGTH("strength", StatusEffects.STRENGTH),
        RESISTANCE("resistance", StatusEffects.RESISTANCE),
        REGENERATION("regeneration", StatusEffects.REGENERATION),
        FIRE_RESISTANCE("fire_resistance", StatusEffects.FIRE_RESISTANCE),
        NIGHT_VISION("night_vision", StatusEffects.NIGHT_VISION),
        HERO("hero", null); // Special combined effect

        private final String name;
        private final RegistryEntry<StatusEffect> effect;

        EffectType(String name, RegistryEntry<StatusEffect> effect) {
            this.name = name;
            this.effect = effect;
        }

        public String getName() {
            return name;
        }

        public RegistryEntry<StatusEffect> getEffect() {
            return effect;
        }

        public static EffectType fromName(String name) {
            for (EffectType type : values()) {
                if (type.name.equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * Apply an effect to a player based on their placement.
     *
     * @param player The player to receive the effect
     * @param effectName The name of the effect
     * @param place The player's placement (1st, 2nd, 3rd)
     */
    public static void applyEffect(ServerPlayerEntity player, String effectName, int place) {
        EffectType type = EffectType.fromName(effectName);
        if (type == null) {
            return;
        }

        // Duration based on placement (in ticks, 20 ticks = 1 second)
        int baseDuration = 20 * 60 * 5; // 5 minutes
        int duration = switch (place) {
            case 1 -> baseDuration * 2; // 10 minutes for 1st
            case 2 -> (int) (baseDuration * 1.5); // 7.5 minutes for 2nd
            case 3 -> baseDuration; // 5 minutes for 3rd
            default -> baseDuration / 2;
        };

        // Amplifier based on placement
        int amplifier = switch (place) {
            case 1 -> 1;
            case 2 -> 0;
            default -> 0;
        };

        List<StatusEffectInstance> effects = getEffects(type, duration, amplifier);
        for (StatusEffectInstance effect : effects) {
            player.addStatusEffect(effect);
        }
    }

    private static List<StatusEffectInstance> getEffects(EffectType type, int duration, int amplifier) {
        List<StatusEffectInstance> effects = new ArrayList<>();

        if (type == EffectType.HERO) {
            // Hero is a special combined effect
            effects.add(createEffect(StatusEffects.GLOWING, duration, 0));
            effects.add(createEffect(StatusEffects.SPEED, duration, amplifier));
            effects.add(createEffect(StatusEffects.STRENGTH, duration, amplifier));
            effects.add(createEffect(StatusEffects.RESISTANCE, duration, amplifier));
            effects.add(createEffect(StatusEffects.REGENERATION, duration, 0));
            effects.add(createEffect(StatusEffects.FIRE_RESISTANCE, duration, 0));
        } else if (type.getEffect() != null) {
            effects.add(createEffect(type.getEffect(), duration, amplifier));
        }

        return effects;
    }

    private static StatusEffectInstance createEffect(RegistryEntry<StatusEffect> effect, int duration, int amplifier) {
        return new StatusEffectInstance(
                effect,
                duration,
                amplifier,
                false,  // ambient
                false,  // showParticles
                true    // showIcon
        );
    }

    /**
     * Get the display name for an effect type in Vietnamese.
     */
    public static String getDisplayName(String effectName) {
        EffectType type = EffectType.fromName(effectName);
        if (type == null) {
            return effectName;
        }

        return switch (type) {
            case GLOWING -> "Phát sáng";
            case SPEED -> "Tốc độ";
            case STRENGTH -> "Sức mạnh";
            case RESISTANCE -> "Kháng cự";
            case REGENERATION -> "Hồi phục";
            case FIRE_RESISTANCE -> "Kháng lửa";
            case NIGHT_VISION -> "Nhìn đêm";
            case HERO -> "Anh hùng";
        };
    }
}
