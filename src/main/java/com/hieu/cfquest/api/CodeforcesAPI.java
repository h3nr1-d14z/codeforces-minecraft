package com.hieu.cfquest.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hieu.cfquest.CFQuestMod;
import com.hieu.cfquest.api.model.Contest;
import com.hieu.cfquest.api.model.Problem;
import com.hieu.cfquest.api.model.Submission;
import com.hieu.cfquest.config.ModConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Codeforces API client với các tối ưu:
 * - Shared HttpClient (thread-safe, connection pooling)
 * - Dedicated executor để không block server thread
 * - Non-blocking rate limiting
 * - Proper resource cleanup
 */
public class CodeforcesAPI {
    private static final String BASE_URL = "https://codeforces.com/api";
    private static final Gson GSON = new Gson();
    private static final long MIN_REQUEST_INTERVAL_MS = 2000; // CF rate limit: 1 req/2s

    // Shared HttpClient - thread-safe, reuses connections
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "CFQuest-HTTP");
                t.setDaemon(true);
                return t;
            }))
            .build();

    // Dedicated executor for API calls - prevents blocking server threads
    private static final ExecutorService API_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CFQuest-API");
        t.setDaemon(true);
        return t;
    });

    // Rate limiting with atomic operations (thread-safe)
    private static final AtomicLong lastRequestTime = new AtomicLong(0);
    private static final Semaphore rateLimitSemaphore = new Semaphore(1);

    private final ModConfig config;
    private volatile boolean shutdown = false;

    public CodeforcesAPI(ModConfig config) {
        this.config = config;
    }

    /**
     * Shutdown API client - call when mod unloads
     */
    public static void shutdown() {
        API_EXECUTOR.shutdown();
        try {
            if (!API_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                API_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            API_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Non-blocking rate limit wait using semaphore
     */
    private CompletableFuture<Void> acquireRateLimit() {
        return CompletableFuture.runAsync(() -> {
            try {
                rateLimitSemaphore.acquire();
                long now = System.currentTimeMillis();
                long lastTime = lastRequestTime.get();
                long waitTime = MIN_REQUEST_INTERVAL_MS - (now - lastTime);

                if (waitTime > 0) {
                    Thread.sleep(waitTime);
                }
                lastRequestTime.set(System.currentTimeMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, API_EXECUTOR);
    }

    private void releaseRateLimit() {
        rateLimitSemaphore.release();
    }

    private String generateApiSig(String methodName, Map<String, String> params) {
        if (!config.getCodeforces().hasCredentials()) {
            return null;
        }

        String rand = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        long time = System.currentTimeMillis() / 1000;

        TreeMap<String, String> sortedParams = new TreeMap<>(params);
        sortedParams.put("apiKey", config.getCodeforces().getApiKey());
        sortedParams.put("time", String.valueOf(time));

        StringBuilder paramString = new StringBuilder();
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            if (paramString.length() > 0) {
                paramString.append("&");
            }
            paramString.append(entry.getKey()).append("=").append(entry.getValue());
        }

        String toHash = rand + "/" + methodName + "?" + paramString + "#" + config.getCodeforces().getApiSecret();

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest(toHash.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                hexString.append(String.format("%02x", b));
            }
            return rand + hexString.toString();
        } catch (Exception e) {
            CFQuestMod.LOGGER.error("Lỗi tạo API signature: {}", e.getMessage());
            return null;
        }
    }

    private String buildUrl(String method, Map<String, String> params) {
        StringBuilder url = new StringBuilder(BASE_URL).append("/").append(method).append("?");

        TreeMap<String, String> allParams = new TreeMap<>(params);

        if (config.getCodeforces().hasCredentials()) {
            long time = System.currentTimeMillis() / 1000;
            allParams.put("apiKey", config.getCodeforces().getApiKey());
            allParams.put("time", String.valueOf(time));

            String apiSig = generateApiSig(method, params);
            if (apiSig != null) {
                allParams.put("apiSig", apiSig);
            }
        }

        boolean first = true;
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            if (!first) {
                url.append("&");
            }
            url.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
               .append("=")
               .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }

        return url.toString();
    }

    private CompletableFuture<JsonObject> makeRequest(String method, Map<String, String> params) {
        if (shutdown) {
            return CompletableFuture.completedFuture(null);
        }

        return acquireRateLimit()
                .thenCompose(v -> {
                    String url = buildUrl(method, params);
                    CFQuestMod.LOGGER.debug("CF API Request: {}", method);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(30))
                            .GET()
                            .build();

                    return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                            .thenApply(response -> {
                                releaseRateLimit();

                                if (response.statusCode() != 200) {
                                    CFQuestMod.LOGGER.error("CF API Error: HTTP {}", response.statusCode());
                                    return null;
                                }

                                try {
                                    JsonObject json = GSON.fromJson(response.body(), JsonObject.class);

                                    if (json == null || !json.has("status")) {
                                        CFQuestMod.LOGGER.error("CF API Error: Invalid response");
                                        return null;
                                    }

                                    if (!"OK".equals(json.get("status").getAsString())) {
                                        String comment = json.has("comment") ? json.get("comment").getAsString() : "Unknown error";
                                        CFQuestMod.LOGGER.error("CF API Error: {}", comment);
                                        return null;
                                    }

                                    return json;
                                } catch (Exception e) {
                                    CFQuestMod.LOGGER.error("CF API Parse Error: {}", e.getMessage());
                                    return null;
                                }
                            });
                })
                .exceptionally(e -> {
                    releaseRateLimit();
                    CFQuestMod.LOGGER.error("CF API Request failed: {}", e.getMessage());
                    return null;
                });
    }

    public CompletableFuture<List<Submission>> getUserSubmissions(String handle, int count) {
        Map<String, String> params = new HashMap<>();
        params.put("handle", handle);
        params.put("count", String.valueOf(count));

        return makeRequest("user.status", params).thenApply(json -> {
            if (json == null) return Collections.emptyList();

            List<Submission> submissions = new ArrayList<>();
            JsonArray result = json.getAsJsonArray("result");

            for (JsonElement element : result) {
                Submission submission = GSON.fromJson(element, Submission.class);
                submissions.add(submission);
            }

            return submissions;
        });
    }

    public CompletableFuture<List<Submission>> getContestSubmissions(int contestId, String handle) {
        Map<String, String> params = new HashMap<>();
        params.put("contestId", String.valueOf(contestId));
        params.put("handle", handle);

        return makeRequest("contest.status", params).thenApply(json -> {
            if (json == null) return Collections.emptyList();

            List<Submission> submissions = new ArrayList<>();
            JsonArray result = json.getAsJsonArray("result");

            for (JsonElement element : result) {
                Submission submission = GSON.fromJson(element, Submission.class);
                submissions.add(submission);
            }

            return submissions;
        });
    }

    public CompletableFuture<Contest> getContestInfo(int contestId) {
        Map<String, String> params = new HashMap<>();
        params.put("contestId", String.valueOf(contestId));

        return makeRequest("contest.standings", params).thenApply(json -> {
            if (json == null) return null;

            JsonObject result = json.getAsJsonObject("result");
            JsonObject contestJson = result.getAsJsonObject("contest");

            return GSON.fromJson(contestJson, Contest.class);
        });
    }

    public CompletableFuture<List<Problem>> getContestProblems(int contestId) {
        Map<String, String> params = new HashMap<>();
        params.put("contestId", String.valueOf(contestId));

        return makeRequest("contest.standings", params).thenApply(json -> {
            if (json == null) return Collections.emptyList();

            List<Problem> problems = new ArrayList<>();
            JsonObject result = json.getAsJsonObject("result");
            JsonArray problemsArray = result.getAsJsonArray("problems");

            for (JsonElement element : problemsArray) {
                Problem problem = GSON.fromJson(element, Problem.class);
                problems.add(problem);
            }

            return problems;
        });
    }

    public CompletableFuture<Problem> getProblem(int contestId, String index) {
        return getContestProblems(contestId).thenApply(problems -> {
            for (Problem problem : problems) {
                if (problem.getIndex().equalsIgnoreCase(index)) {
                    return problem;
                }
            }
            return null;
        });
    }

    public CompletableFuture<Boolean> verifyHandle(String handle) {
        Map<String, String> params = new HashMap<>();
        params.put("handles", handle);

        return makeRequest("user.info", params).thenApply(json -> json != null);
    }

    public CompletableFuture<Map<String, StandingsEntry>> getContestStandings(int contestId, Set<String> handles) {
        Map<String, String> params = new HashMap<>();
        params.put("contestId", String.valueOf(contestId));
        params.put("showUnofficial", "true");

        if (!handles.isEmpty()) {
            params.put("handles", String.join(";", handles));
        }

        return makeRequest("contest.standings", params).thenApply(json -> {
            if (json == null) return Collections.emptyMap();

            Map<String, StandingsEntry> standings = new HashMap<>();
            JsonObject result = json.getAsJsonObject("result");
            JsonArray rows = result.getAsJsonArray("rows");
            JsonArray problems = result.getAsJsonArray("problems");

            for (JsonElement rowElement : rows) {
                JsonObject row = rowElement.getAsJsonObject();
                JsonObject party = row.getAsJsonObject("party");
                JsonArray members = party.getAsJsonArray("members");

                if (members.size() > 0) {
                    String handle = members.get(0).getAsJsonObject().get("handle").getAsString();
                    JsonArray problemResults = row.getAsJsonArray("problemResults");

                    StandingsEntry entry = new StandingsEntry();
                    entry.handle = handle;
                    entry.rank = row.get("rank").getAsInt();
                    entry.points = row.get("points").getAsDouble();
                    entry.penalty = row.get("penalty").getAsInt();

                    entry.problemResults = new HashMap<>();
                    for (int i = 0; i < problemResults.size() && i < problems.size(); i++) {
                        JsonObject pr = problemResults.get(i).getAsJsonObject();
                        String problemIndex = problems.get(i).getAsJsonObject().get("index").getAsString();

                        ProblemResult result1 = new ProblemResult();
                        result1.points = pr.get("points").getAsDouble();
                        result1.rejectedAttemptCount = pr.get("rejectedAttemptCount").getAsInt();
                        result1.bestSubmissionTimeSeconds = pr.has("bestSubmissionTimeSeconds")
                                ? pr.get("bestSubmissionTimeSeconds").getAsLong() : -1;

                        entry.problemResults.put(problemIndex, result1);
                    }

                    standings.put(handle.toLowerCase(), entry);
                }
            }

            return standings;
        });
    }

    public void markShutdown() {
        this.shutdown = true;
    }

    public static class StandingsEntry {
        public String handle;
        public int rank;
        public double points;
        public int penalty;
        public Map<String, ProblemResult> problemResults;

        public boolean hasSolved(String problemIndex) {
            ProblemResult pr = problemResults.get(problemIndex);
            return pr != null && pr.points > 0;
        }

        public ProblemResult getProblemResult(String problemIndex) {
            return problemResults.get(problemIndex);
        }
    }

    public static class ProblemResult {
        public double points;
        public int rejectedAttemptCount;
        public long bestSubmissionTimeSeconds;

        public boolean isSolved() {
            return points > 0;
        }
    }
}
