package io.github.placereporter99.utilitybot;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class HttpRequestHandler implements HttpHandler {
    private final ByteArrayOutputStream logs = new ByteArrayOutputStream();

    public ByteArrayOutputStream getOutputStream() {
        return logs;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        var response = ("<h1><a href=\"https://chat.stackexchange.com/rooms/164579/utility-bot-hut\">Try it here</a></h1><br><h2>Logs</h2><br><pre>" + logs.toString(StandardCharsets.UTF_8) + "</pre>").getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        OutputStream os = exchange.getResponseBody();
        os.write(response);
        os.close();
    }
}
