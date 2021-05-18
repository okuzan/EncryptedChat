package maindir.server;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;

import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import static maindir.Tools.*;


public class ServerController implements Initializable {
    private static final HashMap<String, PrintWriter> connectedPpl = new HashMap<>();
    private static final HashMap<String, SecretKeySpec> pplKeys = new HashMap<>();
    private static boolean exit = false;
    private static int PORT = 8080;
    public TextArea text;
    public Button btn;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        //redirecting output to textArea, javafx exceptions
//        System.setOut(new PrintStream(new CustomOutput(text)));

    }

    @FXML
    private void onClick() {
        //starting server in separate thread
        if (btn.getText().equals("START")) {
            new Thread(new Server()).start();
            timedQuote("SERVER STARTED");
            text.setText("SERVER STARTED");
            btn.setText("STOP");
            exit = false;
        } else {
            timedQuote("SERVER STOPPED");
            System.exit(0);
        }
    }

    //server sends info to all connected ppl, no encryption, for system alarms
    private static void sendToAll(String message) {
        for (PrintWriter p : connectedPpl.values()) {
            p.println(message);
        }
    }

    //encrypted broadcast
    private static void sendToAllEnc(String message) {
        for (Map.Entry<String, PrintWriter> entry : connectedPpl.entrySet()) {
            try {
                String name = entry.getKey();
                SecretKeySpec key = pplKeys.get(name);
                PrintWriter writer = entry.getValue();
                //no need to encrypt stamp
                String stamp = message.substring(0, message.indexOf("]") + 2);
                String text = message.substring(message.indexOf("]") + 2);
                //encrypting message
                writer.println(stamp + encrypt(text, key));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //inner class for server
    private static class Server implements Runnable {
        @Override
        public void run() {
            try {
                ServerSocket server = new ServerSocket(PORT);
                timedQuote("Awaiting participants on port: " + PORT);
                while (!exit) new Thread(new ClientHandler(server.accept())).start();
            } catch (Exception e) {
                timedQuote("\nSomething went wrong: \n");
            }
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private String name;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            timedQuote("Client connected: " + socket.getInetAddress());
            try {
                InputStream inStream = socket.getInputStream();
                OutputStream outStream = socket.getOutputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inStream));
                PrintWriter printWriter = new PrintWriter(outStream, true);

                SecretKeySpec key = diffieHellmanServer(outStream, inStream);

                while (true) {
                    //detecting joining users
                    name = reader.readLine();
                    synchronized (connectedPpl) {
                        if (!connectedPpl.containsKey(name)) break;
                        else printWriter.println("Name already taken!");
                    }
                }

                printWriter.println("Welcome, " + name.toUpperCase());
                timedQuote(name.toUpperCase() + " has joined.");
                sendToAll("[SYSTEM] " + name.toUpperCase() + " has joined.");
                connectedPpl.put(name, printWriter);
                //filling hash map with pairs name-key to broadcast them their messages
                pplKeys.put(name, key);

                //redirecting user's manage to chatroom
                String message;
                printWriter.println("You may join the chat now...");
                while ((message = reader.readLine()) != null && !exit) {
                    if (!message.isEmpty()) {
                        //server has access to plaintext, thus this is not end to end encryption :(
                        String msg = decrypt(message, key);
                        //users' messages should be encrypted with corresponding AES keys
                        sendToAllEnc(String.format("[*%s] %s", name, msg));
                    }
                }
            } catch (Exception e) {
                timedQuote(e.getMessage());
            } finally {
                if (name != null) {
                    timedQuote(name + " is leaving");
                    connectedPpl.remove(name);
                    pplKeys.remove(name);
                    sendToAll(name + " has left");
                }
            }
        }
    }
}