package maindir.client;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;

import static maindir.Tools.*;

public class ClientController implements Initializable {
    private static Socket socket;
    private PrintWriter printWriter;
    private DataOutputStream dos;
    private static final int PORT = 8080;
    private SecretKeySpec aesKey;
    public TextArea text;
    public Button sendBtn;
    public Button connBtn;
    public TextField msgField;
    public TextField usernameField;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // commented because Javafx thread application bugs
//        System.setOut(new PrintStream(new CustomOutput(text)));
    }


    @FXML
    private void send() {
        String msg = msgField.getText().trim();
//        byte[] msgEnc = msgField.getText().trim().getBytes();

//        try {
//            dos.writeInt(msgEnc.length);
//            dos.write(msgEnc);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        if (!msg.equals("")) {
            printWriter.println("");
            try {
                printWriter.println(encrypt(msg, aesKey));
            } catch (Exception e) {
                e.printStackTrace();
            }
            msgField.setText("");
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Error");
            alert.setContentText("You can't send empty message!");
            alert.showAndWait();
        }
    }

    @FXML
    private void clickConn() {
        if (connBtn.getText().equals("Connect")) {
            connect();
            connBtn.setText("Disconnect");
        } else {
            disconnect();
            connBtn.setText("Connect");
        }
    }

    public void connect() {
        try {
            String clientName = usernameField.getText().trim();
            if (clientName.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Error");
                alert.setContentText("Please enter your username!");
                alert.showAndWait();
                return;
            }
            usernameField.setEditable(false);
            socket = new Socket("localhost", PORT);
            OutputStream outStream = socket.getOutputStream();
            InputStream inStream = socket.getInputStream();
            dos = new DataOutputStream(outStream);
            aesKey = diffieHellmanClient(outStream, inStream);

            printWriter = new PrintWriter(socket.getOutputStream(), true);
            new Thread(new Listener(aesKey)).start();

            //send name
            printWriter.println(clientName);
        } catch (Exception err) {
            timedQuote("[ERROR] " + err.getLocalizedMessage());
        }
    }

    public void disconnect() {
        System.exit(0);
    }


    private static class Listener implements Runnable {
        private final SecretKeySpec key;

        Listener(SecretKeySpec aesKey) {
            this.key = aesKey;
        }

        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String read;
                while (true) {
                    read = in.readLine();
                    if (read != null && !(read.isEmpty()))
                        try {
                            //discerning messages from server and from other participants
                            if (read.contains("[*")) {
                                //messages from other participants are encrypted, so we need to decrypt them
                                timedQuote(read.substring(0, read.indexOf("]") + 2) + decrypt(read.substring(read.indexOf("]") + 2), key));
                            } else {
                                //just server plaintext
                                timedQuote((read));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                }
                //exception here but doesn't matter
            } catch (IOException ignored) {
            }
        }
    }
}
