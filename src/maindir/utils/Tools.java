package maindir.utils;

import javafx.scene.control.TextArea;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

public class Tools {
    private static final String ALGO = "AES/CBC/PKCS5Padding";
    public static SimpleDateFormat formatter = new SimpleDateFormat("[H:mm]");
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    private static int ivLen = 16;


    //convert bytes to readable hex format
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static void timedQuote(String message) {
        System.out.printf("%s %s\n", formatter.format(new Date()), message);
    }
    public static void timedQuote2(String message, TextArea textArea) {
//        System.out.printf("%s %s\n", formatter.format(new Date()), message);
        String ss = String.format("%s %s\n", formatter.format(new Date()), message);
        textArea.setText(textArea.getText() + "\n" + ss);
//        System.out.println(new Date() + " | " + message);
    }

    public static SecretKeySpec diffieHellmanServer(OutputStream outStream, InputStream inStream) throws Exception {
        DataOutputStream dos = new DataOutputStream(outStream);
        DataInputStream dis = new DataInputStream(inStream);
        System.out.println("SERVER generates DH keypair ...");

        // generating key pair, size 2048
        KeyPairGenerator serverKeyPairGenerator = KeyPairGenerator.getInstance("DH");
        serverKeyPairGenerator.initialize(2048);
        KeyPair serverKeyPair = serverKeyPairGenerator.generateKeyPair();

        // Server creates and initializes her DH KeyAgreement object
        System.out.println("SERVER initializes DH KeyAgreement object ...");
        KeyAgreement serverKeyAgree = KeyAgreement.getInstance("DH");
        serverKeyAgree.init(serverKeyPair.getPrivate());

        // Server sends public key to the client
        PublicKey serverPublicKey = serverKeyPair.getPublic();
        byte[] serverPublicKeyEnc = serverPublicKey.getEncoded();
        dos.writeInt(serverPublicKeyEnc.length);
        dos.write(serverPublicKeyEnc);

        //Server received client's public key
        int bLen = dis.readInt();
        byte[] theBytes = new byte[bLen];
        dis.readFully(theBytes);

        //Server instantiates client's public key
        KeyFactory serverKeyFactory = KeyFactory.getInstance("DH");
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(theBytes);
        PublicKey clientPublicKey = serverKeyFactory.generatePublic(x509KeySpec);
        System.out.println("SERVER executes PHASE1 ...");
        serverKeyAgree.doPhase(clientPublicKey, true);

        //shared secret generated
        byte[] sharedSecret = serverKeyAgree.generateSecret();
        System.out.println(bytesToHex(sharedSecret));

        //deriving AES key from shared secret
        SecretKeySpec aesKey = new SecretKeySpec(sharedSecret, 0, 16, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey);

        byte[] cleartext = "If you see this it means that encryption works!".getBytes();
        byte[] ciphertext = cipher.doFinal(cleartext);
        byte[] encodedParams = cipher.getParameters().getEncoded();

        //sending text encrypted with AES and cipher info
        dos.writeInt(ciphertext.length);
        dos.write(ciphertext);
        dos.writeInt(encodedParams.length);
        dos.write(encodedParams);

        return aesKey;
    }

    public static SecretKeySpec diffieHellmanClient(OutputStream outStream, InputStream inStream) throws Exception {
        DataInputStream dis = new DataInputStream(inStream);
        DataOutputStream dos = new DataOutputStream(outStream);

        KeyFactory clientKeyFactory = KeyFactory.getInstance("DH");

        //receiving info from server
        int byteLength = dis.readInt();
        byte[] theBytes = new byte[byteLength];
        dis.readFully(theBytes);
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(theBytes);
        PublicKey serverPublicKey = clientKeyFactory.generatePublic(x509KeySpec);
        DHParameterSpec dhParamFromServerPubKey = ((DHPublicKey) serverPublicKey).getParams();

        // Client creates his own DH key pair
        System.out.println("CLIENT generates DH keypair ...");
        KeyPairGenerator clientKeyPairGen = KeyPairGenerator.getInstance("DH");
        clientKeyPairGen.initialize(dhParamFromServerPubKey);
        KeyPair clientKeyPair = clientKeyPairGen.generateKeyPair();

        // Client creates and initializes his DH KeyAgreement object
        System.out.println("CLIENT initializes ...");
        KeyAgreement clientKeyAgreement = KeyAgreement.getInstance("DH");
        clientKeyAgreement.init(clientKeyPair.getPrivate());

        // Client encodes his public key, and sends it over to Server.
        byte[] clientPublicKeyEnc = clientKeyPair.getPublic().getEncoded();
        dos.writeInt(clientPublicKeyEnc.length);
        dos.write(clientPublicKeyEnc);

        System.out.println("CLIENT executes PHASE1 ...");
        clientKeyAgreement.doPhase(serverPublicKey, true);

        //shared secret generated
        byte[] sharedSecret = clientKeyAgreement.generateSecret();
        System.out.println("Shared Secret:" + bytesToHex(sharedSecret));

        System.out.println("Use shared secret as SecretKey object ...");
        SecretKeySpec aesKey = new SecretKeySpec(sharedSecret, 0, 16, "AES");

        //receiving AES-encrypted text
        int msgL = dis.readInt();
        byte[] ciphertext = new byte[msgL];
        dis.readFully(ciphertext);
        int msgL2 = dis.readInt();
        byte[] encodedParams = new byte[msgL2];
        dis.readFully(encodedParams);

        //decrypting
        AlgorithmParameters aesParams = AlgorithmParameters.getInstance("AES");
        aesParams.init(encodedParams);
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.DECRYPT_MODE, aesKey, aesParams);
        byte[] recovered = cipher.doFinal(ciphertext);
        System.out.println(new String(recovered));
        return aesKey;
    }


    public static String encrypt(String input, SecretKeySpec key
    ) throws Exception {

        // generating IV
        byte[] iv = new byte[ivLen];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        //encrypting
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
        byte[] cipherBytes = cipher.doFinal(input.getBytes());

        //sending (ciphertext + iv) as String
        String cipherText = Base64.getEncoder().encodeToString(cipherBytes);
        String ivText = Base64.getEncoder().encodeToString(iv);
        return cipherText + ivText;
    }

    public static String decrypt(String msgEnc, SecretKeySpec key) throws Exception {

        //Getting ciphertext and IV from message
        String ivText = msgEnc.substring(msgEnc.length() - 24);
        String cipherText = msgEnc.substring(0, msgEnc.length() - 24);

        //converting them from string to byte array
        byte[] ivBytes = Base64.getDecoder().decode(ivText);
        byte[] cipherBytes = Base64.getDecoder().decode(cipherText);

        //decrypting
        IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
        byte[] plainText = cipher.doFinal(cipherBytes);
        //return as string
        return new String(plainText);
    }

}
