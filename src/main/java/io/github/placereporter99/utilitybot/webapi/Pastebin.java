package io.github.placereporter99.utilitybot.webapi;

import io.github.placereporter99.utilitybot.Helpers;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

public class Pastebin {
    private final HTTPRequester httpRequester = new HTTPRequester();

    public String read(String id) {
        try {
            var response = httpRequester.apiGet("https://pastebin.com/raw/" + id);
            Helpers.AssertionException.softAssert(response.statusCode() == 200);
            return new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
        } catch (URISyntaxException | IOException | InterruptedException | Helpers.AssertionException e) {
            e.printStackTrace();
            return null;
        }
    }
}
