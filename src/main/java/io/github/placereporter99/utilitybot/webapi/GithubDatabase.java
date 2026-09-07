package io.github.placereporter99.utilitybot.webapi;

import io.github.placereporter99.utilitybot.Helpers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class GithubDatabase {
    private final String owner;
    private final String repo;

    public static final Raw RAW = new Raw();
    public static final Sequential SEQUENTIAL = new Sequential();
    public static final KeyValue KEY_VALUE = new KeyValue();

    private sealed interface AbstractStorageType<T> permits Raw, Sequential, KeyValue {
        String serialize(T data);
        T deserialize(String data);
    }

    private static final class Raw implements AbstractStorageType<String> {
        public String serialize(String data) {
            return data;
        }

        public String deserialize(String data) {
            return data;
        }
    }
    private static final class Sequential implements AbstractStorageType<List<String>> {
        public String serialize(List<String> data) {
            return "!DATATYPE Sequential\n" + data.stream().map(x -> x.replace("\\", "\\\\").replace("\n", "\\n")).collect(Collectors.joining("\n"));
        }

        public List<String> deserialize(String data) {
            var l = data.split("\n");
            if (!Objects.equals(l[0], "!DATATYPE Sequential")) {
                return null;
            }
            return Arrays.stream(l).skip(1).map(x -> x.replace("\\\\", "\0").replace("\\n", "\n").replace("\0", "\\")).toList();
        }
    }
    private static final class KeyValue implements AbstractStorageType<Map<String, String>> {
        public String serialize(Map<String, String> data) {
            return "!DATATYPE KeyValue\n" + data.entrySet().stream().map(x -> {
                var a = x.getKey();
                var b = x.getValue();
                var f = a.replace("\\", "\\\\").replace("|","\\S").replace("\n","\\n");
                var g = b.replace("\\", "\\\\").replace("|","\\S").replace("\n","\\n");
                return f + "|" + g;
            }).collect(Collectors.joining("\n"));
        }

        public Map<String, String> deserialize(String data) {
            var l = data.split("\n");
            if (!Objects.equals(l[0], "!DATATYPE KeyValue")) {
                return null;
            }
            return new HashMap<>(Arrays.stream(l).skip(1).map(x -> {
                var arr = x.split("\\|");
                var a = arr[0];
                var b = arr[1];
                var f = a.replace("\\\\", "\0").replace("\\n", "\n").replace("\\S", "|").replace("\0", "\\");
                var g = b.replace("\\\\", "\0").replace("\\n", "\n").replace("\\S", "|").replace("\0", "\\");
                return Map.entry(f, g);
            }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }
    }

    public GithubDatabase(String owner, String repo) {
        this.owner = owner;
        this.repo = repo;
    }

    public String blobSha(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            var text = String.format("blob %s\0%s", content.length(), content);
            md.update(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private String getGithubFile(String folder, String fileName) {
        var now = Instant.now();
        var randomb = new byte[64];
        new SecureRandom().nextBytes(randomb);
        var url = String.format("https://raw.githubusercontent.com/%s/%s/refs/heads/main/%s/%s?time=%s&random=%s", owner, repo, folder, fileName, now.getNano() + (now.getEpochSecond() * 1000000000L), HexFormat.of().formatHex(randomb));
        try {
            var response = new HTTPRequester().apiGet(url);
            System.out.print("Request details: ");
            System.out.println(response);
            Helpers.AssertionException.softAssert(response.statusCode() == 200);
            return new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean writeGithubFile(String folder, String fileName, String contents) {
        var headers = new String[]{"Accept", "application/vnd.github+json", "Authorization", "Bearer " + System.getenv("GITHUB_DATABASE_KEY"), "X-GitHub-Api-Version", "2026-03-10"};
        var fileToSha = getGithubFile(folder, fileName);
        var body = String.format("{\"message\":\"Write to Database\",\"committer\":{\"name\":\"Utility Bot\",\"email\":\"REDACTED@unknown.com\"},\"content\":\"%s\"", Base64.getEncoder().encodeToString(contents.getBytes(StandardCharsets.UTF_8)));
        var url = String.format("https://api.github.com/repos/%s/%s/contents/%s/%s", owner, repo, folder, fileName);
        if (fileToSha == null) {
            body = body + "}";
        } else {
            body = body + ",\"sha\":\"" + blobSha(fileToSha) + "\"}";
        }
        try {
            var response = new HTTPRequester().apiPut(url, headers, body);
            System.out.print("Request details: ");
            System.out.println(response);
            Helpers.AssertionException.softAssert(response.statusCode() == 200 || response.statusCode() == 201);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public <T> T readData(String folder, String file, AbstractStorageType<T> storageType) {
        var data = getGithubFile(folder, file);
        if (data == null) {
            return null;
        }
        return storageType.deserialize(data);
    }

    public <T> boolean writeData(String folder, String file, T data, AbstractStorageType<T> storageType) {
        if (data == null) {
            return false;
        }
        return writeGithubFile(folder, file, storageType.serialize(data));
    }
}
