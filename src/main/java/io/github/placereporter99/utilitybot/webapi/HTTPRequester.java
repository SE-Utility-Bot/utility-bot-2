package io.github.placereporter99.utilitybot.webapi;

import io.github.placereporter99.utilitybot.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

public class HTTPRequester {
    private final HttpClient client;
    public HTTPRequester() {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public HttpResponse<InputStream> apiGet(String url) throws URISyntaxException, IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(new URI(url)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
    }

    public HttpResponse<InputStream> apiPut(String url, String[] headers, String body) throws URISyntaxException, IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(new URI(url)).PUT(HttpRequest.BodyPublishers.ofString(body)).headers(headers).build(), HttpResponse.BodyHandlers.ofInputStream());
    }

    public String messageGet(String url) {
        try {
            var result = apiGet(url);
            var code = result.statusCode();
            var prettyHeaders = result.headers().map().entrySet().stream().map(x -> x.getKey() + " = [" + String.join(", ", x.getValue()) + "]").collect(Collectors.joining("\n"));
            var body = new String(result.body().readAllBytes(), StandardCharsets.UTF_8);
            return String.format("Status: %s\n\nHEADERS\n=======\n\n%s\n\nBODY\n====\n\n%s", code, prettyHeaders, body);
        } catch (Exception e) {
            return "An error occurred:\n\n" + Helpers.getFullMessage(e);
        }
    }
}
