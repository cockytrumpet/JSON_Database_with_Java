package server;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class Content {
    private final JsonObject map;

    Content() {
        map = new JsonObject();
    }

    public JsonObject getMap() {
        return map;
    }
    public void put(JsonElement key, JsonElement value) {
        traverse(map, key.getAsJsonArray(), true, value);
    }
    public JsonElement get(JsonElement key) {
        return traverse(map, key.getAsJsonArray(), false, null);
    }
    public JsonElement remove(JsonElement key) {
        return traverse(map, key.getAsJsonArray(), true, null);
    }

    private JsonElement traverse(JsonObject current, JsonArray path, boolean modify, JsonElement value) {
        JsonObject parent = current;
        String lastKey = null;

        for (int i = 0; i < path.size(); i++) {
            lastKey = path.get(i).getAsString();

            if (modify && i < path.size() - 1) {
                if (!current.has(lastKey) || !current.get(lastKey).isJsonObject()) {
                    JsonObject newObject = new JsonObject();
                    current.add(lastKey, newObject);
                }
                current = current.getAsJsonObject(lastKey);
            } else if (!modify) {
                if (!current.has(lastKey)) {
                    return null;
                }
                if (i < path.size() - 1) {
                    current = current.getAsJsonObject(lastKey);
                }
            }
        }

        if (modify) {
            if (value == null) {
                return parent.remove(lastKey);
            } else {
                parent.add(lastKey, value);
                return value;
            }
        } else {
            return current.get(lastKey);
        }
    }
}

class DB {
    private final File dbFile = new File(System.getProperty("user.dir") + "/db.json");
    private final Gson gson;
    private final Lock readLock;
    private final Lock writeLock;
    private Content content;


    public DB() {
        gson = new Gson();
        content = new Content();
        ReadWriteLock lock = new ReentrantReadWriteLock();
        readLock = lock.readLock();
        writeLock = lock.writeLock();

        if (!dbFile.exists()) {
            try {
                dbFile.createNewFile();
            } catch (IOException e) {
                System.out.println("Can't create db file");
                System.exit(1);
            }
        }
    }

    private void load() {
        try (FileReader reader = new FileReader(dbFile)) {
            content = gson.fromJson(reader, Content.class);
            if (content == null) {
                content = new Content();
            }
        } catch (IOException e) {
            System.out.println("Error reading from db file");
            content = new Content();
        }
    }

    private void store() {
        try (FileWriter writer = new FileWriter(dbFile)) {
            gson.toJson(content.getMap(), writer);
        } catch (IOException e) {
            System.out.println("Error writing to db file");
        }
    }

    public String set(JsonArray key, JsonElement value) {
        writeLock.lock();
        try {
            load();
            content.put(key, value);
            store();
        } finally {
            writeLock.unlock();
        }
        return "OK";
    }

    public String get(JsonArray key) {
        readLock.lock();
        try {
            load();
        } finally {
            readLock.unlock();
        }
        JsonElement result = content.get(key);
        return result == null ? "ERROR" : result.toString();
    }

    public String delete(JsonArray key) {
        writeLock.lock();
        try {
            load();
            JsonElement removed = content.remove(key);
                store();
                return removed != null ? "OK" : "ERROR";
        } finally {
            writeLock.unlock();
        }
    }

    public void exit() {
        System.exit(0);
    }
}

class Request {
    String type;
    JsonArray key;
    JsonElement value;

    Request() {}
}

class Response {
    String response;
    String reason;
    String value;

    Response() {}
    Response(String response, String reason, String value) {
        this.response = response;
        this.reason = reason;
        this.value = value;
    }
}

public class Main {
    public static void main(String[] args) {
        DB db = new DB();
        String address = "127.0.0.1";
        int port = 23456;
        ExecutorService threadPool = Executors.newFixedThreadPool(10);

        System.out.println("Server started!");
        try (ServerSocket server = new ServerSocket(port, 50, InetAddress.getByName((address)))) {
            while (true) {
                Socket socket = server.accept();
                threadPool.submit(() -> handleClient(socket, db));
            }
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }

    private static void handleClient(Socket socket, DB db) {
        try (DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {

            String received = input.readUTF();
            System.out.println("Received: " + received);

            String response = process(db, received);

            output.writeUTF(response);
            System.out.println("Sent: " + response);
        } catch (IOException e) {
            System.out.println("Error handling client: " + e.getMessage());
        }
    }

    public static String process(DB db, String received) {
        Request request = new Gson().fromJson(received, Request.class);
        Response response = new Response();
        String result;

        switch (request.type) {
            case "set":
                result = db.set(request.key, request.value);
                response.response = result;
                break;
            case "get":
                result = db.get(request.key);
                if (result.equals("ERROR")) {
                    response.response = "ERROR";
                } else {
                    response.response = "OK";
                    response.value = result;
                }
                break;
            case "delete":
                result = db.delete(request.key);
                response.response = result;
                break;
            case "exit":
                db.exit();
            default:
                response.response = "!!!";
        }

        return new Gson().toJson(response);
    }
}
