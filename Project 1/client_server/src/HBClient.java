package client_server.src;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;

public class HBClient {
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final int NODE_ID = secureRandom.nextInt(1000);
    private static List<String> SERVER_IPS;
    private static int SERVER_PORT;
    private static String DIRECTORY_PATH;
    private static int versionCounter = 1;

    public static void main(String[] args) {
        loadClientConfig("client_server/config/client.txt");

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(5000);

            while (true) {
                String version = "v" + versionCounter++;
                List<String> fileList = getFileListing(DIRECTORY_PATH);
                HACPacket packet = new HACPacket(NODE_ID, "HEARTBEAT", fileList, version);

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
                    oos.writeObject(packet);
                } catch (IOException e) {
                    System.err.println("Error serializing packet: " + e.getMessage());
                    continue;
                }

                byte[] sendData = bos.toByteArray();

                boolean sent = false;
                for (String serverIP : SERVER_IPS) {
                    try {
                        InetAddress serverAddress = InetAddress.getByName(serverIP);
                        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, SERVER_PORT);
                        socket.send(sendPacket);
                        System.out.println("Sent heartbeat to " + serverIP);
                        sent = true;
                    } catch (IOException e) {
                        System.err.println("Failed to send heartbeat to " + serverIP + ": " + e.getMessage());
                    }
                }

                if (!sent) {
                    System.out.println("Failed to send heartbeat to all servers.");
                }

                try {
                    int sleepTime = secureRandom.nextInt(30) + 1;
                    Thread.sleep(sleepTime * 1000L);
                } catch (InterruptedException e) {
                    System.err.println("Thread interrupted: " + e.getMessage());
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (SocketException e) {
            System.err.println("Error creating or configuring socket: " + e.getMessage());
        } catch (@SuppressWarnings("hiding") IOException e) {
            System.err.println("Unexpected I/O error: " + e.getMessage());
        }
    }

    private static void loadClientConfig(String filePath) {
        try {
            Properties props = new Properties();
            props.load(new FileInputStream(filePath));
            SERVER_IPS = Arrays.asList(props.getProperty("server_ips").split(","));
            SERVER_PORT = Integer.parseInt(props.getProperty("port"));
            DIRECTORY_PATH = props.getProperty("directory_path");
            System.out.println("Loaded client configuration: SERVER_IPS=" + SERVER_IPS + ", PORT=" + SERVER_PORT + ", DIRECTORY_PATH=" + DIRECTORY_PATH);
        } catch (IOException e) {
            System.err.println("Error reading client config file: " + e.getMessage());
            System.exit(1);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number in config file: " + e.getMessage());
            System.exit(1);
        }
    }

    private static List<String> getFileListing(String directoryPath) {
        List<String> fileList = new ArrayList<>();
        try {
            Files.list(Paths.get(directoryPath))
                 .filter(Files::isRegularFile)
                 .forEach(file -> fileList.add(file.getFileName().toString()));
        } catch (IOException e) {
            System.err.println("Error reading directory: " + e.getMessage());
        } catch (SecurityException e) {
            System.err.println("Permission denied while accessing directory: " + e.getMessage());
        }
        return fileList;
    }
}