package com.hieu.cfquest.api.model;

public class Submission {
    private long id;
    private int contestId;
    private long creationTimeSeconds;
    private long relativeTimeSeconds;
    private Problem problem;
    private Author author;
    private String programmingLanguage;
    private String verdict;
    private String testset;
    private int passedTestCount;
    private int timeConsumedMillis;
    private int memoryConsumedBytes;

    public static class Author {
        private int contestId;
        private Member[] members;
        private String participantType;
        private boolean ghost;
        private int startTimeSeconds;

        public static class Member {
            private String handle;
            private String name;

            public String getHandle() {
                return handle;
            }

            public void setHandle(String handle) {
                this.handle = handle;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }
        }

        public int getContestId() {
            return contestId;
        }

        public void setContestId(int contestId) {
            this.contestId = contestId;
        }

        public Member[] getMembers() {
            return members;
        }

        public void setMembers(Member[] members) {
            this.members = members;
        }

        public String getParticipantType() {
            return participantType;
        }

        public void setParticipantType(String participantType) {
            this.participantType = participantType;
        }

        public boolean isGhost() {
            return ghost;
        }

        public void setGhost(boolean ghost) {
            this.ghost = ghost;
        }

        public int getStartTimeSeconds() {
            return startTimeSeconds;
        }

        public void setStartTimeSeconds(int startTimeSeconds) {
            this.startTimeSeconds = startTimeSeconds;
        }

        public String getHandle() {
            if (members != null && members.length > 0) {
                return members[0].getHandle();
            }
            return null;
        }
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getContestId() {
        return contestId;
    }

    public void setContestId(int contestId) {
        this.contestId = contestId;
    }

    public long getCreationTimeSeconds() {
        return creationTimeSeconds;
    }

    public void setCreationTimeSeconds(long creationTimeSeconds) {
        this.creationTimeSeconds = creationTimeSeconds;
    }

    public long getRelativeTimeSeconds() {
        return relativeTimeSeconds;
    }

    public void setRelativeTimeSeconds(long relativeTimeSeconds) {
        this.relativeTimeSeconds = relativeTimeSeconds;
    }

    public Problem getProblem() {
        return problem;
    }

    public void setProblem(Problem problem) {
        this.problem = problem;
    }

    public Author getAuthor() {
        return author;
    }

    public void setAuthor(Author author) {
        this.author = author;
    }

    public String getProgrammingLanguage() {
        return programmingLanguage;
    }

    public void setProgrammingLanguage(String programmingLanguage) {
        this.programmingLanguage = programmingLanguage;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public String getTestset() {
        return testset;
    }

    public void setTestset(String testset) {
        this.testset = testset;
    }

    public int getPassedTestCount() {
        return passedTestCount;
    }

    public void setPassedTestCount(int passedTestCount) {
        this.passedTestCount = passedTestCount;
    }

    public int getTimeConsumedMillis() {
        return timeConsumedMillis;
    }

    public void setTimeConsumedMillis(int timeConsumedMillis) {
        this.timeConsumedMillis = timeConsumedMillis;
    }

    public int getMemoryConsumedBytes() {
        return memoryConsumedBytes;
    }

    public void setMemoryConsumedBytes(int memoryConsumedBytes) {
        this.memoryConsumedBytes = memoryConsumedBytes;
    }

    public boolean isAccepted() {
        return "OK".equals(verdict);
    }

    public boolean isWrongAnswer() {
        return "WRONG_ANSWER".equals(verdict);
    }

    public boolean isPending() {
        return verdict == null || "TESTING".equals(verdict);
    }

    public String getHandle() {
        return author != null ? author.getHandle() : null;
    }
}
