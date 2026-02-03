package com.hieu.cfquest.quest;

import com.hieu.cfquest.api.model.Problem;

import java.util.*;

public class Quest {
    private int contestId;
    private String problemIndex;
    private String problemName;
    private int problemRating;
    private String problemUrl;

    private long startTime;
    private long endTime;
    private int timeoutMinutes;

    private List<Winner> winners = new ArrayList<>();
    private Map<String, Integer> penaltyCounts = new HashMap<>(); // cfHandle -> wrongCount
    private Set<String> solvedHandles = new HashSet<>();

    public Quest() {
    }

    public Quest(int contestId, Problem problem, int timeoutMinutes) {
        this.contestId = contestId;
        this.problemIndex = problem.getIndex();
        this.problemName = problem.getName();
        this.problemRating = problem.getRating();
        this.problemUrl = contestId >= 100000 ? problem.getGymUrl() : problem.getProblemUrl();
        this.timeoutMinutes = timeoutMinutes;
        this.startTime = System.currentTimeMillis();
        this.endTime = startTime + (timeoutMinutes * 60 * 1000L);
    }

    public static class Winner implements Comparable<Winner> {
        private String playerUuid;
        private String playerName;
        private String cfHandle;
        private int place;
        private long solveTimeSeconds;
        private int penaltyMinutes;
        private long totalTimeSeconds;

        public Winner() {
        }

        public Winner(String playerUuid, String playerName, String cfHandle, int place,
                      long solveTimeSeconds, int penaltyMinutes) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.cfHandle = cfHandle;
            this.place = place;
            this.solveTimeSeconds = solveTimeSeconds;
            this.penaltyMinutes = penaltyMinutes;
            this.totalTimeSeconds = solveTimeSeconds + (penaltyMinutes * 60L);
        }

        public String getPlayerUuid() {
            return playerUuid;
        }

        public void setPlayerUuid(String playerUuid) {
            this.playerUuid = playerUuid;
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

        public int getPlace() {
            return place;
        }

        public void setPlace(int place) {
            this.place = place;
        }

        public long getSolveTimeSeconds() {
            return solveTimeSeconds;
        }

        public void setSolveTimeSeconds(long solveTimeSeconds) {
            this.solveTimeSeconds = solveTimeSeconds;
        }

        public int getPenaltyMinutes() {
            return penaltyMinutes;
        }

        public void setPenaltyMinutes(int penaltyMinutes) {
            this.penaltyMinutes = penaltyMinutes;
        }

        public long getTotalTimeSeconds() {
            return totalTimeSeconds;
        }

        public void setTotalTimeSeconds(long totalTimeSeconds) {
            this.totalTimeSeconds = totalTimeSeconds;
        }

        public String getFormattedTime() {
            long minutes = solveTimeSeconds / 60;
            long seconds = solveTimeSeconds % 60;
            return String.format("%d:%02d", minutes, seconds);
        }

        public String getFormattedTotalTime() {
            long minutes = totalTimeSeconds / 60;
            long seconds = totalTimeSeconds % 60;
            return String.format("%d:%02d", minutes, seconds);
        }

        @Override
        public int compareTo(Winner other) {
            return Long.compare(this.totalTimeSeconds, other.totalTimeSeconds);
        }
    }

    public int getContestId() {
        return contestId;
    }

    public void setContestId(int contestId) {
        this.contestId = contestId;
    }

    public String getProblemIndex() {
        return problemIndex;
    }

    public void setProblemIndex(String problemIndex) {
        this.problemIndex = problemIndex;
    }

    public String getProblemName() {
        return problemName;
    }

    public void setProblemName(String problemName) {
        this.problemName = problemName;
    }

    public int getProblemRating() {
        return problemRating;
    }

    public void setProblemRating(int problemRating) {
        this.problemRating = problemRating;
    }

    public String getProblemUrl() {
        return problemUrl;
    }

    public void setProblemUrl(String problemUrl) {
        this.problemUrl = problemUrl;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public int getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public void setTimeoutMinutes(int timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
    }

    public List<Winner> getWinners() {
        return new ArrayList<>(winners);
    }

    public void setWinners(List<Winner> winners) {
        this.winners = new ArrayList<>(winners);
    }

    public Map<String, Integer> getPenaltyCounts() {
        return new HashMap<>(penaltyCounts);
    }

    public void setPenaltyCounts(Map<String, Integer> penaltyCounts) {
        this.penaltyCounts = new HashMap<>(penaltyCounts);
    }

    public Set<String> getSolvedHandles() {
        return new HashSet<>(solvedHandles);
    }

    public void setSolvedHandles(Set<String> solvedHandles) {
        this.solvedHandles = new HashSet<>(solvedHandles);
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= endTime;
    }

    public long getRemainingTimeMillis() {
        return Math.max(0, endTime - System.currentTimeMillis());
    }

    public String getRemainingTimeFormatted() {
        long remaining = getRemainingTimeMillis();
        long minutes = remaining / 60000;
        long seconds = (remaining % 60000) / 1000;
        return String.format("%d:%02d", minutes, seconds);
    }

    public int getPenaltyCount(String cfHandle) {
        return penaltyCounts.getOrDefault(cfHandle.toLowerCase(), 0);
    }

    public void setPenaltyCount(String cfHandle, int count) {
        penaltyCounts.put(cfHandle.toLowerCase(), count);
    }

    public boolean hasWon(String cfHandle) {
        return solvedHandles.contains(cfHandle.toLowerCase());
    }

    public Winner addWinner(String playerUuid, String playerName, String cfHandle,
                            long solveTimeSeconds, int penaltyMinutes) {
        if (hasWon(cfHandle)) {
            return null;
        }

        solvedHandles.add(cfHandle.toLowerCase());

        int place = winners.size() + 1;
        Winner winner = new Winner(playerUuid, playerName, cfHandle, place, solveTimeSeconds, penaltyMinutes);
        winners.add(winner);

        // Sort winners by total time and reassign places
        Collections.sort(winners);
        for (int i = 0; i < winners.size(); i++) {
            winners.get(i).setPlace(i + 1);
        }

        return winner;
    }

    public Winner getWinner(int place) {
        for (Winner winner : winners) {
            if (winner.getPlace() == place) {
                return winner;
            }
        }
        return null;
    }

    public String getProblemIdentifier() {
        return contestId + problemIndex;
    }

    public String getProblemDisplayName() {
        return problemName != null ? problemName : (contestId + problemIndex);
    }
}
