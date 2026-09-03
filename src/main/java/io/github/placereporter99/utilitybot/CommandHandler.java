package io.github.placereporter99.utilitybot;

import com.github.mangstadt.sochat4j.ChatMessage;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;
import java.util.function.*;
import java.security.SecureRandom;

public class CommandHandler {
    private final HashMap<String, BiFunction<String, ChatMessage, String>> handlers = new HashMap<>();

    private String buildReply(ChatMessage message, String text) {
        return String.format(":%s %s", message.id(), text);
    }

    private void put(String command, BiFunction<String, ChatMessage, String> function) {
        handlers.put(command, function);
    }

    private void putMulti(String[] commands, BiFunction<String, ChatMessage, String> function) {
        Arrays.stream(commands).forEach(x -> put(x, function));
    }

    public CommandHandler() {
        put("echo", (args, msg) -> (args));
        putMulti(new String[]{"status", "op"}, (args, msg) -> {
            try (var resource = CommandHandler.class.getClassLoader().getResourceAsStream("status.txt")) {
                var lines = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8)).lines().toList();
                var index = new SecureRandom().nextInt(lines.size());
                return buildReply(msg, lines.get(index));
            } catch (IOException e) {
                return buildReply(msg, "Weird IO things prevent me from doing anything else!!!!");
            } catch (NullPointerException e) {
                return buildReply(msg, "Oh no it's null and void!!!!");
            }
        });
        put("ping", (args, msg) -> buildReply(msg, String.format("@%s you have been pinged by ^", args.replace(" ", ""))));
        put("randombytes", (args, msg) -> {
            if (args.length() <= 3) {
                var num = Integer.parseInt(args);
                var bytes = new byte[num];
                new SecureRandom().nextBytes(bytes);
                return new String(bytes, StandardCharsets.ISO_8859_1);
            } else {
                return buildReply(msg, "Number too big, must be at most 999.");
            }
        });
    }

    public String handleCommand(ChatMessage message, int id) {
        var text = message.content().getContent();
        var arr = text.split(" ", 2);
        var get = handlers.get(arr[0]);
        System.out.println("________________________________________________________________________");
        System.out.print("Room ID: ");
        System.out.println(id);
        System.out.print("Command: ");
        System.out.println(arr[0]);
        String one;
        try {
            one = arr[1];
        } catch (ArrayIndexOutOfBoundsException _) {
            one = null;
        }
        if (get == null) {
            return null;
        }
        System.out.print("Args: ");
        System.out.println(one);
        String finalMessage;
        try {
            finalMessage = get.apply(one, message);
        } catch (Exception e) {
            finalMessage = buildReply(message, "An error occurred: `" + e.toString() + "`");
        }
        System.out.print("Final message: ");
        System.out.print(finalMessage);
        System.out.println("________________________________________________________________________");
        return finalMessage;
    }
}
