package p2p.src;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class HBP2P {
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final int NODE_ID = secureRandom.nextInt(1000);
    private static List<String> PEER_IPS;
    private static int PEER_PORT;
    private static String DIRECTORY_PATH;
    private static int versionCounter = 1;

    private static final Map<Integer, NodeInfo> peerFileMap = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> activePeers = new ConcurrentHashMap<>();
    private static final Set<Integer> previouslyDeadPeers = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        loadPeerConfig("config/peer_config.txt");

        new Thread(HBP2P::listenForHeartbeats).start();

        new Thread(HBP2P::sendHeartbeats).start();

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(HBP2P::checkPeerHealth, 5, 5, TimeUnit.SECONDS);
    }

    private static void listenForHeartbeats() {
        try (DatagramSocket socket = new DatagramSocket(PEER_PORT)) {
            socket.setSoTimeout(5000);

            byte[] receiveData = new byte[4096];
            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                try {
                    socket.receive(receivePacket);
                    InetAddress peerIp = receivePacket.getAddress();
                    ByteArrayInputStream bis = new ByteArrayInputStream(receivePacket.getData());
                    try (ObjectInputStream ois = new ObjectInputStream(bis)) {
                        HACPacket packet = (HACPacket) ois.readObject();
                        activePeers.put(packet.getNodeId(), System.currentTimeMillis());
                        NodeInfo nodeInfo = new NodeInfo(packet.getVersion(), packet.getTimestamp(), packet.getFileList(), peerIp.getHostAddress());
                        handleReconnection(nodeInfo, packet.getNodeId());
                        System.out.println("Received heartbeat from Node " + packet.getNodeId());

                        printGlobalFileMap();
                    } catch (ClassNotFoundException e) {
                        System.err.println("Invalid packet received: " + e.getMessage());
                    } catch (InvalidClassException e) {
                        System.err.println("Corrupted packet received: " + e.getMessage());
                    }
                } catch (SocketTimeoutException e) {
                    System.err.println("Socket timeout: No packets received in the last 5 seconds.");
                } catch (IOException e) {
                    System.err.println("Error receiving packet: " + e.getMessage());
                }
            }
        } catch (SocketException e) {
            System.err.println("Error creating or configuring socket: " + e.getMessage());
        }
    }

    private static void sendHeartbeats() {
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
                for (String peerIP : PEER_IPS) {
                    try {
                        InetAddress peerAddress = InetAddress.getByName(peerIP);
                        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, peerAddress, PEER_PORT);
                        socket.send(sendPacket);
                        System.out.println("Sent heartbeat to " + peerIP);
                        sent = true;
                    } catch (IOException e) {
                        System.err.println("Failed to send heartbeat to " + peerIP + ": " + e.getMessage());
                    }
                }

                if (!sent) {
                    System.out.println("Failed to send heartbeat to all peers.");
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

    private static void checkPeerHealth() {
        long currentTime = System.currentTimeMillis();
        activePeers.forEach((nodeId, lastSeen) -> {
            if (currentTime - lastSeen > 30000) {
                System.out.println("Node " + nodeId + " is down.");
                activePeers.remove(nodeId);
                peerFileMap.remove(nodeId);
                previouslyDeadPeers.add(nodeId);
            }
        });
    }

    private static void handleReconnection(NodeInfo nodeInfo, int nodeId) {
        if (previouslyDeadPeers.contains(nodeId)) {
            System.out.println("Node " + nodeId + " has reconnected.");
            previouslyDeadPeers.remove(nodeId);
        } else {
            System.out.println("Node " + nodeId + " is up.");
        }
        peerFileMap.put(nodeId, nodeInfo);
    }

    private static void printGlobalFileMap() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Map<Integer, NodeInfo> sortedNodes = new TreeMap<>(peerFileMap);

        System.out.println("\nCurrent Node Status:");
        System.out.println("-------------------------------------------------------------------");
        System.out.printf("%-12s %-12s %-25s %-18s\n", "Node ID", "Version", "Timestamp", "Node IP");
        System.out.println("-------------------------------------------------------------------");

        sortedNodes.forEach((nodeId, info) -> {
            String formattedTime = sdf.format(new Date(info.timestamp));
            System.out.printf("%-12d %-12s %-25s %-18s\n", nodeId, info.version, formattedTime, info.nodeIp);
            System.out.println("\nFiles: " + info.fileList);
            System.out.println("-------------------------------------------------------------------\n");
        });
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

    private static void loadPeerConfig(String filePath) {
        try {
            Properties props = new Properties();
            props.load(new FileInputStream(filePath));
            PEER_IPS = Arrays.asList(props.getProperty("peer_ips").split(","));
            PEER_PORT = Integer.parseInt(props.getProperty("port"));
            DIRECTORY_PATH = props.getProperty("directory_path");
            System.out.println("Loaded peer configuration: PEER_IPS=" + PEER_IPS + ", PORT=" + PEER_PORT + ", DIRECTORY_PATH=" + DIRECTORY_PATH);
        } catch (IOException e) {
            System.err.println("Error reading peer config file: " + e.getMessage());
            System.exit(1);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number in config file: " + e.getMessage());
            System.exit(1);
        }
    }

    private static class NodeInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        String version;
        long timestamp;
        List<String> fileList;
        String nodeIp;

        NodeInfo(String version, long timestamp, List<String> fileList, String nodeIp) {
            this.version = version;
            this.timestamp = timestamp;
            this.fileList = fileList;
            this.nodeIp = nodeIp;
        }
    }
}
