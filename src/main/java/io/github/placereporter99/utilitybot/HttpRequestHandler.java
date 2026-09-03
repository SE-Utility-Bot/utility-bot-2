package io.github.placereporter99.utilitybot;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.nio.charset.StandardCharsets;

class StreamCollector implements Runnable {
    private final HttpRequestHandler rq;
    StreamCollector(HttpRequestHandler rq) {
        this.rq = rq;
    }

    @Override
    public void run() {
        try {
            while (true) {
                rq.addLogs(rq.getInputStream().read());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

public class HttpRequestHandler implements HttpHandler {
    private final PipedInputStream inputStream = new PipedInputStream();
    private final OutputStream outputStream;
    private final ByteArrayOutputStream logs = new ByteArrayOutputStream();
    private final Thread thread;

    public HttpRequestHandler() throws IOException {
        outputStream = new PipedOutputStream(inputStream);
        thread = new Thread(new StreamCollector(this));
        thread.start();
    }

    public PipedInputStream getInputStream() {
        return inputStream;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public void addLogs(int byteValue) {
        logs.write(byteValue);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        var response = "<h1><a href=\"https://chat.stackexchange.com/rooms/164579/utility-bot-hut\">Try it here</a></h1><br><h2>Logs</h2><br><pre>" + logs.toString(StandardCharsets.UTF_8) + "</pre>";
        exchange.sendResponseHeaders(200, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}
