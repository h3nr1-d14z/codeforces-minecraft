package com.hieu.cfquest.quest;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks wrong answer penalties for players during a quest (ICPC-style).
 */
public class PenaltyTracker {
    private final Map<String, Integer> penaltyCounts = new HashMap<>();
    private final int penaltyMinutes;

    public PenaltyTracker(int penaltyMinutes) {
        this.penaltyMinutes = penaltyMinutes;
    }

    /**
     * Record a wrong answer for a CF handle.
     * @return the new penalty count
     */
    public int recordWrongAnswer(String cfHandle) {
        String key = cfHandle.toLowerCase();
        int newCount = penaltyCounts.getOrDefault(key, 0) + 1;
        penaltyCounts.put(key, newCount);
        return newCount;
    }

    /**
     * Get the current wrong answer count for a handle.
     */
    public int getWrongAnswerCount(String cfHandle) {
        return penaltyCounts.getOrDefault(cfHandle.toLowerCase(), 0);
    }

    /**
     * Get the total penalty time in minutes for a handle.
     */
    public int getPenaltyMinutes(String cfHandle) {
        return getWrongAnswerCount(cfHandle) * penaltyMinutes;
    }

    /**
     * Get the total penalty time in seconds for a handle.
     */
    public long getPenaltySeconds(String cfHandle) {
        return getPenaltyMinutes(cfHandle) * 60L;
    }

    /**
     * Set the penalty count for a handle (used when loading from API).
     */
    public void setPenaltyCount(String cfHandle, int count) {
        penaltyCounts.put(cfHandle.toLowerCase(), count);
    }

    /**
     * Clear all penalties.
     */
    public void clear() {
        penaltyCounts.clear();
    }

    /**
     * Get all penalty counts.
     */
    public Map<String, Integer> getAllPenalties() {
        return new HashMap<>(penaltyCounts);
    }

    /**
     * Calculate total score (solve time + penalties) in seconds.
     */
    public long calculateTotalScore(String cfHandle, long solveTimeSeconds) {
        return solveTimeSeconds + getPenaltySeconds(cfHandle);
    }
}
