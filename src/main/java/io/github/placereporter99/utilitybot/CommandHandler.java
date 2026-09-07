package io.github.placereporter99.utilitybot;

import com.github.mangstadt.sochat4j.ChatMessage;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.io.*;
import java.util.function.*;
import java.security.SecureRandom;
import java.util.stream.Collectors;

import io.github.placereporter99.utilitybot.webapi.GithubDatabase;
import io.github.placereporter99.utilitybot.webapi.HTTPRequester;
import io.github.placereporter99.utilitybot.webapi.Pastebin;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.text.StringEscapeUtils;

class UserGeneratedCommandHandler extends CommandHandler {
    public UserGeneratedCommandHandler(int roomId) {
        super(roomId, false);
        put("new", "Creates/overwrites the given command, which executes the Java program from the given pastebin.com paste id. The command name must match the class name.", (args, msg) -> {
            var arr = args.split(" ");
            var cmd = arr[0];
            try {
                var id = arr[1];
                var contents = new Pastebin().read(id);
                if (contents == null) {
                    return "That pastebin.com paste does not exist.";
                }
                var result = gd.writeData("commands", cmd + ".java", contents, GithubDatabase.RAW);
                if (result) {
                    return "Command created!";
                } else {
                    return "Failed to create command.";
                }
            } catch (Exception e) {
                return Helpers.getFullMessage(e);
            }
        });
        put("exec", "Executes the given command with the given arguments.", (args, msg) -> {
            var arr = args.split(" ", 2);
            var cmd = arr[0];
            var cmdArgs = (arr.length == 1 ? new String[]{""} : CommandLine.parse("bananaland " + arr[1]).getArguments());
            var code = gd.readData("commands", cmd + ".java", GithubDatabase.RAW);
            if (code == null) {
                return "**ERROR**: GitHub returned non-200 response.\nIf you have recently created this command, try running it again.";
            }
            return new ArbitraryCodeExecutor(15).executeUntrustedCodeRaw(code, cmd, cmdArgs);
        });
        put("help", "Gets info about each subcommand for running custom commands.", (args, msg) -> getFormattedDocs());
    }
}

public class CommandHandler {
    private final HashMap<String, BiFunction<String, ChatMessage, String>> handlers = new HashMap<>();
    private final HashMap<String, String> docmap = new HashMap<>();
    final int roomId;
    final GithubDatabase gd = new GithubDatabase("SE-Utility-Bot", "utility-bot-database");

    static String buildReply(ChatMessage message, String text) {
        return String.format(":%s %s", message.id(), text);
    }

    void put(String command, String docs, BiFunction<String, ChatMessage, String> function) {
        handlers.put(command, function);
        docmap.put(command, docs);
    }

    void putMulti(String[] commands, String docs, BiFunction<String, ChatMessage, String> function) {
        Arrays.stream(commands).forEach(x -> handlers.put(x, function));
        docmap.put(String.join("/", commands), docs);
    }

    String getFormattedDocs() {
        return docmap.entrySet().stream().map(x -> x.getKey() + ": " + x.getValue()).collect(Collectors.joining("\n"));
    }

    BiFunction<String, ChatMessage, String> handlerToBi(CommandHandler commandHandler) {
        return (args, msg) -> commandHandler.handleCommand(new ChatMessage.Builder(msg).content(args, msg.content().isFixedWidthFont()).build());
    }

    private void initialize() {
        put("echo", "Makes the bot say exactly what you typed.", (args, msg) -> (args));
        putMulti(new String[]{"status", "op"}, "Prints a random message from status.txt.", (args, msg) -> {
            try (var resource = CommandHandler.class.getClassLoader().getResourceAsStream("status.txt")) {
                var lines = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8)).lines().toList();
                var index = new SecureRandom().nextInt(lines.size());
                return lines.get(index);
            } catch (IOException e) {
                return "Weird IO things prevent me from doing anything else!!!!";
            } catch (NullPointerException e) {
                return "Oh no it's null and void!!!!";
            }
        });
        put("ping", "Pings the given user.", (args, msg) -> String.format("@%s you have been pinged by ^", args.replace(" ", "")));
        put("randombytes", "Generates the given number of random bytes. Some bytes may cause the message to not be sendable at all.", (args, msg) -> {
            if (args.length() <= 3) {
                var num = Integer.parseInt(args);
                var bytes = new byte[num];
                new SecureRandom().nextBytes(bytes);
                return new String(bytes, StandardCharsets.ISO_8859_1);
            } else {
                return buildReply(msg, "Number too big, must be at most 999.");
            }
        });
        put("execute", "Executes the given Java program. The program must be in a public static method called 'main' in a class called 'Main'.", (args, msg) -> Helpers.indentLinesByFourSpaces(new ArbitraryCodeExecutor(15).executeUntrustedCode(args)));
        put("webscrape", "Sends an HTTP GET request to the given URL and gives comprehensive info on the response.", (args, msg) -> new HTTPRequester().messageGet(args));
        put("getpaste", "Gets the contents of a paste on pastebin.com with the given ID.", (args, msg) -> {var result = new Pastebin().read(args); return (result == null) ? "That pastebin.com paste does not exist." : "\n" + Helpers.indentLinesByFourSpaces(result);});
        put("execpaste", "Executes the contents of the paste on pastebin.com with the given ID as a Java program. The program must be in a public static method called 'main' in a class called 'Main'.", (args, msg) -> {
            var arr = args.split(" ", 2);
            var id = arr[0];
            var cmdArgs = (arr.length == 1 ? new String[]{""} : CommandLine.parse("bananaland " + arr[1]).getArguments());
            var result = new Pastebin().read(id);
            if (result == null) {
                return "That pastebin.com paste does not exist.";
            }
            return new ArbitraryCodeExecutor(15).executeUntrustedCode(result, cmdArgs);
        });
        put("command", "Does things relating to custom user defined commands. Run `command help` for more info.", handlerToBi(new UserGeneratedCommandHandler(roomId)));
        put("help", "Gets info about each command.", (args, msg) -> getFormattedDocs());
        put("echochr", "Sends the UTF-8 character with the given codepoint", (args, msg) -> Character.toString(Integer.parseInt(args)));
    }

    public CommandHandler(int roomId) {
        this(roomId, true);
    }

    public CommandHandler(int roomId, boolean registerCommands) {
        this.roomId = roomId;
        if (registerCommands) {
            initialize();
        }
    }

    public String handleCommand(ChatMessage message) {
        var text = message.content().getContent();
        var arr = text.split("[ \n]", 2);
        var get = handlers.get(arr[0]);
        System.out.println("________________________________________________________________________");
        System.out.println(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        System.out.print("Room ID: ");
        System.out.println(roomId);
        System.out.print("Received message: ");
        System.out.println(message.content().getContent());
        System.out.print("Sent by: ");
        System.out.printf("%s (%s)%n", message.username(), message.userId());
        System.out.print("Command: ");
        System.out.println(arr[0]);
        String one;
        try {
            one = StringEscapeUtils.unescapeHtml4(arr[1]);
        } catch (ArrayIndexOutOfBoundsException _) {
            one = null;
        }
        if (get == null) {
            System.out.println("Command does not exist, will not send any message.");
            System.out.println("________________________________________________________________________");
            return null;
        }
        System.out.print("Args: ");
        System.out.println(one);
        String finalMessage;
        try {
            finalMessage = get.apply(one, message);
        } catch (Exception e) {
            finalMessage = buildReply(message, Helpers.indentLinesByFourSpaces("An error occurred:\n" + Helpers.getFullMessage(e)));
        }
        System.out.print("Final message: ");
        System.out.println(finalMessage);
        System.out.println("________________________________________________________________________");
        return finalMessage;
    }
}