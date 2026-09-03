package io.github.placereporter99.utilitybot;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.Arrays;

import com.github.mangstadt.sochat4j.Room;
import com.github.mangstadt.sochat4j.RoomNotFoundException;
import com.github.mangstadt.sochat4j.RoomPermissionException;
import com.github.mangstadt.sochat4j.Site;
import com.github.mangstadt.sochat4j.InvalidCredentialsException;
import com.github.mangstadt.sochat4j.ChatClient;
import com.github.mangstadt.sochat4j.PrivateRoomException;
import com.github.mangstadt.sochat4j.event.MessagePostedEvent;
import com.sun.net.httpserver.HttpServer;

public class Main {
    public static boolean prepareRoom(Room room, CommandHandler handler){
        try {
            room.addEventListener(MessagePostedEvent.class, event -> {
                try {
                    var result = handler.handleCommand(event.getMessage(), room.getRoomId());
                    if (result != null) {
                        room.sendMessage(result);
                    }
                } catch (RoomPermissionException | IOException e) {
                    e.printStackTrace();
                }
            });

            room.sendMessage("UtilityBot (testing) Online!");
            return true;
        } catch (RoomNotFoundException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean leaveRoom(Room room){
        try {
            room.sendMessage("UtilityBot (testing) Offline!");
            room.leave();
            return true;
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
            var rooms = Arrays.stream(roomIds).map(x -> {try {return client.joinRoom(x);} catch (IOException e) {throw new RuntimeException(e);} catch (RoomNotFoundException ex) {throw new RuntimeException(ex);}}).toArray();
            var handler = new CommandHandler();
            var http = HttpServer.create(new InetSocketAddress(10000), -1);

            http.setExecutor(null);
            var httpRequestHandler = new HttpRequestHandler();
            System.setOut(httpRequestHandler.getPrintStream());
            System.setErr(httpRequestHandler.getPrintStream());
            http.createContext("/", httpRequestHandler);
            http.start();

            Arrays.stream(rooms).map(x -> prepareRoom((Room) x, handler)).toArray();

            System.out.println("Bot has started!");
            try {
                Thread.currentThread().join();
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    Arrays.stream(rooms).map(x -> leaveRoom((Room) x)).toArray();
                } catch (Exception ee) {
                    ee.printStackTrace();
                }
                main(args);
            }

            Arrays.stream(rooms).map(x -> leaveRoom((Room) x)).toArray();
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