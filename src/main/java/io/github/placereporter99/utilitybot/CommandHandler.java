package io.github.placereporter99.utilitybot;

import com.github.mangstadt.sochat4j.ChatMessage;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.io.*;
import java.util.function.*;
import java.security.SecureRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.github.mangstadt.sochat4j.Room;
import io.github.classgraph.ClassGraph;
import io.github.placereporter99.utilitybot.fishinggames.FishingInventory;
import io.github.placereporter99.utilitybot.fishinggames.FishingPool;
import io.github.placereporter99.utilitybot.webapi.GithubDatabase;
import io.github.placereporter99.utilitybot.webapi.HTTPRequester;
import io.github.placereporter99.utilitybot.webapi.Pastebin;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.text.StringEscapeUtils;

interface FishingHandler {
    Supplier<Void> getListenerFromUserName(String username);
    FishingInventory loadInventoryOfUser(String username, int userId);
    FishingInventory getInventoryOfUser(String username, int userId);
    boolean saveInventoryOfUser(int userId);
}

class ExceptionFishingHandler extends CommandHandler implements FishingHandler {
    private final FishingPool pool = new FishingPool();
    private final Map<Integer, FishingInventory> inventories = new HashMap<>();
    private final GithubDatabase gd;

    public ExceptionFishingHandler(Room room, GithubDatabase gd) {
        super(room, false);
        this.gd = gd;
        var scanResult = new ClassGraph().verbose().enableSystemJarsAndModules().ignoreClassVisibility().removeTemporaryFilesAfterScan().scan();
        var classNames = scanResult.getSubclasses(Throwable.class).getNames();
        classNames.forEach(x -> pool.addItem(x, 1));
        putSingleMsg("cast", "Toggles whether the rod is thrown.", (args, msg) -> {
            var inv = getInventoryOfUser(msg.username(), msg.userId());
            if (inv.isRodCasted()) {
                var item = inv.pullRod();
                if (item == null) {
                    return "\uD83D\uDEA9 *" + msg.username() + " fails to catch anything.*";
                } else {
                    try {
                        saveInventoryOfUser(msg.userId());
                    } catch (Exception _) {}
                    return "\uD83D\uDEA9 *" + msg.username() + " successfully catches a `" + item + "`.*";
                }
            } else {
                inv.throwRod();
                return "\uD83D\uDEA9 *" + msg.username() + " casts away their handlers.*";
            }
        });
        put("cycle", "Unthrows the rod if it is pulled in, and re-throws it.", (args, msg) -> {
            var inv = getInventoryOfUser(msg.username(), msg.userId());
            var l = new ArrayList<String>();
            if (inv.isRodCasted()) {
                var item = inv.pullRod();
                if (item == null) {
                    l.add("\uD83D\uDEA9 *" + msg.username() + " fails to catch anything.*");
                } else {
                    try {
                        saveInventoryOfUser(msg.userId());
                    } catch (Exception _) {}
                    l.add("\uD83D\uDEA9 *" + msg.username() + " successfully catches a `" + item + "`.*");
                }
            }
            inv.throwRod();
            l.add("\uD83D\uDEA9 *" + msg.username() + " casts away their handlers.*");
            return l.toArray(String[]::new);
        });
        putSingleMsg("inv", "Gets your inventory.", (args, msg) -> {
            var inv = getInventoryOfUser(msg.username(), msg.userId());
            return "\uD83D\uDEA9 *" + msg.username() + "'s inventory contains: " + inv.getSerializableInventory().entrySet().stream().map(x -> "`" + x.getKey() + "` (x" + x.getValue() + ")").collect(Collectors.joining(", ")) + "*";
        });
        putSingleMsg("throw", "Re-throws or sacrifices a caught throwable.", (args, msg) -> {
            var inv = getInventoryOfUser(msg.username(), msg.userId());
            var cond = inv.dispose(args);
            saveInventoryOfUser(msg.userId());
            if (cond) {
                return "\uD83D\uDEA9 *" + msg.username() + " re-throws `" + args + "` as a sacrifice to the call stack.*";
            } else {
                return "\uD83D\uDEA9 *" + msg.username() + " discovers that they do not have any `" + args + "`.*";
            }
        });
        putSingleMsg("help", "Gets info about each subcommand for fishing and catching throwables.", (args, msg) -> getFormattedDocs());
    }

    public Supplier<Void> getListenerFromUserName(String username) {
        return () -> {try {room.sendMessage("\uD83D\uDEA9 *" + username + "'s call stack clatters with a new exception.*");} catch (Exception e) {System.out.println("Failed to send fishing message"); e.printStackTrace();} return null;};
    }

    public FishingInventory getInventoryOfUser(String username, int userId) {
        var inv = inventories.get(userId);
        if (inv == null) {
            return loadInventoryOfUser(username, userId);
        }
        return inv;
    }

    public FishingInventory loadInventoryOfUser(String username, int userId) {
        var data = gd.readData("fishinggames/throwables/inventory", Integer.toString(userId) + ".inv", GithubDatabase.KEY_VALUE);
        FishingInventory inv;
        if (data == null) {
            inv = pool.createLinkedInventory(getListenerFromUserName(username));
        } else {
            inv = pool.loadLinkedInventory(getListenerFromUserName(username), data);
        }
        inventories.put(userId, inv);
        return inv;
    }

    public boolean saveInventoryOfUser(int userId) {
        var inv = inventories.get(userId);
        if (inv == null) {
            return false;
        }
        var data = inv.getSerializableInventory();
        return gd.writeData("fishinggames/throwables/inventory", Integer.toString(userId) + ".inv", data, GithubDatabase.KEY_VALUE);
    }
}

class UserGeneratedCommandHandler extends CommandHandler {
    public UserGeneratedCommandHandler(Room room) {
        super(room, false);
        putSingleMsg("new", "Creates/overwrites the given command, which executes the Java program from the given pastebin.com paste id. The command name must match the class name.", (args, msg) -> {
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
        putSingleMsg("exec", "Executes the given command with the given arguments.", (args, msg) -> {
            var arr = args.split(" ", 2);
            var cmd = arr[0];
            var cmdArgs = (arr.length == 1 ? new String[]{""} : CommandLine.parse("bananaland " + arr[1]).getArguments());
            var code = gd.readData("commands", cmd + ".java", GithubDatabase.RAW);
            if (code == null) {
                return "**ERROR**: GitHub returned non-200 response.\nIf you have recently created this command, try running it again.";
            }
            return new ArbitraryCodeExecutor(15).executeUntrustedCodeRaw(code, cmd, cmdArgs);
        });
        putSingleMsg("help", "Gets info about each subcommand for running custom commands.", (args, msg) -> getFormattedDocs());
    }
}

public class CommandHandler {
    private final HashMap<String, BiFunction<String, ChatMessage, String[]>> handlers = new HashMap<>();
    private final HashMap<String, String> docmap = new HashMap<>();
    final int roomId;
    final GithubDatabase gd = new GithubDatabase("SE-Utility-Bot", "utility-bot-database");
    final Room room;

    static String buildReply(ChatMessage message, String text) {
        return String.format(":%s %s", message.id(), text);
    }

    void put(String command, String docs, BiFunction<String, ChatMessage, String[]> function) {
        handlers.put(command, function);
        docmap.put(command, docs);
    }

    void putSingleMsg(String command, String docs, BiFunction<String, ChatMessage, String> function) {
        put(command, docs, (args, msg) -> new String[]{function.apply(args, msg)});
    }

    void putMulti(String[] commands, String docs, BiFunction<String, ChatMessage, String[]> function) {
        Arrays.stream(commands).forEach(x -> handlers.put(x, function));
        docmap.put(String.join("/", commands), docs);
    }

    void putMultiSingleMsg(String[] commands, String docs, BiFunction<String, ChatMessage, String> function) {
        putMulti(commands, docs, (args, msg) -> new String[]{function.apply(args, msg)});
    }

    String getFormattedDocs() {
        return docmap.entrySet().stream().map(x -> x.getKey() + ": " + x.getValue()).collect(Collectors.joining("\n"));
    }

    BiFunction<String, ChatMessage, String[]> handlerToBi(CommandHandler commandHandler) {
        return (args, msg) -> commandHandler.handleCommand(new ChatMessage.Builder(msg).content(args, msg.content().isFixedWidthFont()).build());
    }

    BiFunction<String, ChatMessage, String[]> handlerToBiNoLogs(CommandHandler commandHandler) {
        return (args, msg) -> commandHandler.handleCommandNoLogs(new ChatMessage.Builder(msg).content(args, msg.content().isFixedWidthFont()).build());
    }

    private void initialize() {
        putSingleMsg("echo", "Makes the bot say exactly what you typed.", (args, msg) -> (args));
        putMultiSingleMsg(new String[]{"status", "op"}, "Prints a random message from status.txt.", (args, msg) -> {
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
        putSingleMsg("ping", "Pings the given user.", (args, msg) -> String.format("@%s you have been pinged by ^", args.replace(" ", "")));
        putSingleMsg("randombytes", "Generates the given number of random bytes. Some bytes may cause the message to not be sendable at all.", (args, msg) -> {
            if (args.length() <= 3) {
                var num = Integer.parseInt(args);
                var bytes = new byte[num];
                new SecureRandom().nextBytes(bytes);
                return new String(bytes, StandardCharsets.ISO_8859_1);
            } else {
                return buildReply(msg, "Number too big, must be at most 999.");
            }
        });
        putSingleMsg("execute", "Executes the given Java program. The program must be in a public static method called 'main' in a class called 'Main'.", (args, msg) -> Helpers.indentLinesByFourSpaces(new ArbitraryCodeExecutor(15).executeUntrustedCode(args)));
        putSingleMsg("webscrape", "Sends an HTTP GET request to the given URL and gives comprehensive info on the response.", (args, msg) -> new HTTPRequester().messageGet(args));
        putSingleMsg("getpaste", "Gets the contents of a paste on pastebin.com with the given ID.", (args, msg) -> {var result = new Pastebin().read(args); return (result == null) ? "That pastebin.com paste does not exist." : "\n" + Helpers.indentLinesByFourSpaces(result);});
        putSingleMsg("execpaste", "Executes the contents of the paste on pastebin.com with the given ID as a Java program. The program must be in a public static method called 'main' in a class called 'Main'.", (args, msg) -> {
            var arr = args.split(" ", 2);
            var id = arr[0];
            var cmdArgs = (arr.length == 1 ? new String[]{""} : CommandLine.parse("bananaland " + arr[1]).getArguments());
            var result = new Pastebin().read(id);
            if (result == null) {
                return "That pastebin.com paste does not exist.";
            }
            return new ArbitraryCodeExecutor(15).executeUntrustedCode(result, cmdArgs);
        });
        put("command", "Does things relating to custom user defined commands. Run `command help` for more info.", handlerToBi(new UserGeneratedCommandHandler(room)));
        putSingleMsg("help", "Gets info about each command.", (args, msg) -> getFormattedDocs());
        putSingleMsg("echochr", "Sends the UTF-8 character with the given codepoint.", (args, msg) -> Character.toString(Integer.parseInt(args)));
        putSingleMsg("\uD83D\uDC1F", "Fishing listener.", (args, msg) -> {
            var pattern = Pattern.compile("^<i>(.*)'s line quivers\\.</i>$");
            var matcher = pattern.matcher(args);
            if (msg.userId() != 375672) {
                return null;
            }
            if (matcher.find()) {
                var user = matcher.group(0);
                if (user.startsWith("Utility Bot")) {
                    return "/fish again";
                } else {
                    return "@" + user.replace(" ", "") + " your fish is ready! `/fish again`";
                }
            }
            return null;
        });
        putSingleMsg("\uD83D\uDCE7", "Phishing listener.", (args, msg) -> {
            var pattern = Pattern.compile("^<i>(.*)'s inbox pings\\.</i>$");
            var matcher = pattern.matcher(args);
            if (msg.userId() != 375672) {
                return null;
            }
            if (matcher.find()) {
                var user = matcher.group(0);
                if (user.startsWith("Utility Bot")) {
                    return "/phish again";
                } else {
                    return "@" + user.replace(" ", "") + " your phish is ready! `/phish again`";
                }
            }
            return null;
        });
        put("throwable", "Play a game involving fishing for throwables.", handlerToBi(new ExceptionFishingHandler(room, gd)));
    }

    public CommandHandler(Room room) {
        this(room, true);
    }

    public CommandHandler(Room room, boolean registerCommands) {
        this.roomId = room.getRoomId();
        this.room = room;
        if (registerCommands) {
            initialize();
        }
    }

    public String[] handleCommandNoLogs(ChatMessage message) {
        var text = message.content().getContent();
        var arr = text.split("[ \n]", 2);
        var get = handlers.get(arr[0]);
        String one;
        try {
            one = StringEscapeUtils.unescapeHtml4(arr[1]);
        } catch (ArrayIndexOutOfBoundsException _) {
            one = null;
        }
        if (get == null) {
            return null;
        }
        String[] finalMessage;
        try {
            finalMessage = get.apply(one, message);
        } catch (Exception e) {
            finalMessage = new String[]{buildReply(message, Helpers.indentLinesByFourSpaces("An error occurred:\n" + Helpers.getFullMessage(e)))};
        }
        return finalMessage;
    }

    public String[] handleCommand(ChatMessage message) {
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
        String[] finalMessage;
        try {
            finalMessage = get.apply(one, message);
        } catch (Exception e) {
            finalMessage = new String[]{buildReply(message, Helpers.indentLinesByFourSpaces("An error occurred:\n" + Helpers.getFullMessage(e)))};
        }
        System.out.println("Final messages:\n");
        System.out.println(String.join("\n", finalMessage));
        System.out.println("________________________________________________________________________");
        return finalMessage;
    }
}
