package io.github.placereporter99.utilitybot;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.function.Consumer;

import com.github.mangstadt.sochat4j.Room;
import com.github.mangstadt.sochat4j.RoomNotFoundException;
import com.github.mangstadt.sochat4j.RoomPermissionException;
import com.github.mangstadt.sochat4j.Site;
import com.github.mangstadt.sochat4j.InvalidCredentialsException;
import com.github.mangstadt.sochat4j.ChatClient;
import com.github.mangstadt.sochat4j.PrivateRoomException;
import com.github.mangstadt.sochat4j.event.Event;
import com.github.mangstadt.sochat4j.event.MessageEditedEvent;
import com.github.mangstadt.sochat4j.event.MessagePostedEvent;
import com.sun.net.httpserver.HttpServer;

class MessageSendingListener implements Runnable {
    private final Event event;
    private final CommandHandler commandHandler;
    private final Room room;
    public MessageSendingListener(Event event, CommandHandler commandHandler, Room room) {
        if (!(event instanceof MessagePostedEvent || event instanceof MessageEditedEvent)) {
            throw new IllegalArgumentException("Not a message sending event");
        }
        this.event = event;
        this.commandHandler = commandHandler;
        this.room = room;
    }

    public void run() {
        try {
            if (event instanceof MessagePostedEvent) {
                var result = commandHandler.handleCommand(((MessagePostedEvent) event).getMessage(), room.getRoomId());
                if (result != null) {
                    room.sendMessage(result);
                }
            } else if (event instanceof MessageEditedEvent) {
                var result = commandHandler.handleCommand(((MessageEditedEvent) event).getMessage(), room.getRoomId());
                if (result != null) {
                    room.sendMessage(result);
                }
            }
        } catch (RoomPermissionException | IOException e) {
            e.printStackTrace();
        }
    }
}

public class Main {
    public static <T extends Event> Consumer<T> getListener(Class<T> event, Room room, CommandHandler handler) {
        return (T e) -> Thread.startVirtualThread(new MessageSendingListener(e, handler, room));
    }

    public static <T extends Event> void addListener(Class<T> event, Room room, CommandHandler handler) {
        room.addEventListener(event, getListener(event, room, handler));
    }

    public static void prepareRoom(Room room, CommandHandler handler){
        try {
            addListener(MessagePostedEvent.class, room, handler);
            addListener(MessageEditedEvent.class, room, handler);

            room.sendMessage("UtilityBot (testing) Online!");
        } catch (RoomNotFoundException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void leaveRoom(Room room){
        try {
            room.sendMessage("UtilityBot (testing) Offline!");
            room.leave();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        var site = Site.STACKEXCHANGE;
        var email = System.getenv("BOT_EMAIL");
        var password = System.getenv("BOT_PASSWORD");
        var roomIds = new Integer[]{1, 164579};

        try (var client = ChatClient.connect(site, email, password)) {
            var rooms = Arrays.stream(roomIds).map(x -> {try {return client.joinRoom(x);} catch (IOException | RoomNotFoundException e) {throw new RuntimeException(e);}}).toArray();
            var handler = new CommandHandler();
            var http = HttpServer.create(new InetSocketAddress(10000), -1);

            http.setExecutor(null);
            var httpRequestHandler = new HttpRequestHandler();
            System.setOut(DualOutputStream.teeWithOut(httpRequestHandler.getOutputStream()));
            System.setErr(DualOutputStream.teeWithErr(httpRequestHandler.getOutputStream()));
            http.createContext("/", httpRequestHandler);
            http.start();

            Arrays.stream(rooms).forEach(x -> prepareRoom((Room) x, handler));

            System.out.println("Bot has started!");
            try {
                Thread.currentThread().join();
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    Arrays.stream(rooms).forEach(x -> leaveRoom((Room) x));
                } catch (Exception ee) {
                    ee.printStackTrace();
                }
                main(args);
            }

            Arrays.stream(rooms).forEach(x -> leaveRoom((Room) x));
        } catch (InvalidCredentialsException e) {
            System.err.println("Login credentials invalid.");
        } catch (RoomNotFoundException e) {
            System.err.println("Room not found.");
        } catch (PrivateRoomException e) {
            System.err.println("Cannot join room because it is private.");
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Issue with HTTP.");
        }
    }
}