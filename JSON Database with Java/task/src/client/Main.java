package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.gson.Gson;

class Request {
    String type;
    String key;
    String value;

    Request(String type, String key, String value) {
        this.type = type;
        this.key = key;
        this.value = value;
    }
}

public class Main {
    @Parameter(names={"--type", "-t"})
    String type;
    @Parameter(names={"--key", "-k"})
    String key;
    @Parameter(names={"--value", "-v"})
    String value;
    @Parameter(names={"--in"})
    String in;

    public static void main(String[] args) {
        Main main = new Main();
        JCommander.newBuilder()
                .addObject(main)
                .build()
                .parse(args);
        main.run();
    }

    public void run() {
        String address = "127.0.0.1";
        int port = 23456;
        Socket socket;
        DataInputStream input;
        DataOutputStream output;

        System.out.println("Client started!");

        try {
            socket = new Socket(InetAddress.getByName(address), port);
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String json = null;
        if (in == null) {
            Request request = new Request(type, key, value);
            json = new Gson().toJson(request);
        } else {
            try (FileReader fileReader = new FileReader(in)) {
                json = String.valueOf(fileReader.read());
            } catch (IOException e) {
                System.err.println("Error reading file: " + e.getMessage());
            }
        }

        try {
            output.writeUTF(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println("Sent: " + json);

        String response;
        try {
            response = input.readUTF();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println("Received: " + response);
    }
}
