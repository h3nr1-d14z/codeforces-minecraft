package com.hieu.cfquest.api.model;

public class Problem {
    private int contestId;
    private String index;
    private String name;
    private String type;
    private double points;
    private int rating;
    private String[] tags;

    public int getContestId() {
        return contestId;
    }

    public void setContestId(int contestId) {
        this.contestId = contestId;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getPoints() {
        return points;
    }

    public void setPoints(double points) {
        this.points = points;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public String getProblemUrl() {
        return String.format("https://codeforces.com/contest/%d/problem/%s", contestId, index);
    }

    public String getGymUrl() {
        return String.format("https://codeforces.com/gym/%d/problem/%s", contestId, index);
    }

    @Override
    public String toString() {
        return String.format("%d%s - %s (Rating: %d)", contestId, index, name, rating);
    }
}
