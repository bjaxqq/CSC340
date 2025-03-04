package client_server.src;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class HBServer {
    private static int PORT;
    private static final Map<Integer, NodeInfo> nodeFileMap = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> activeNodes = new ConcurrentHashMap<>();
    private static final Set<Integer> previouslyDeadNodes = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        loadServerConfig("client_server/config/server.txt");

        try (DatagramSocket serverSocket = new DatagramSocket(PORT)) {
            serverSocket.setSoTimeout(5000);
            System.out.println("Server listening for heartbeats on port " + PORT);

            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(HBServer::checkNodeHealth, 5, 5, TimeUnit.SECONDS);

            byte[] receiveData = new byte[4096];
            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                try {
                    serverSocket.receive(receivePacket);
                    InetAddress nodeIp = receivePacket.getAddress();
                    ByteArrayInputStream bis = new ByteArrayInputStream(receivePacket.getData());
                    try (ObjectInputStream ois = new ObjectInputStream(bis)) {
                        HACPacket packet = (HACPacket) ois.readObject();
                        activeNodes.put(packet.getNodeId(), System.currentTimeMillis());
                        NodeInfo nodeInfo = new NodeInfo(packet.getVersion(), packet.getTimestamp(), packet.getFileList(), nodeIp.getHostAddress());
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
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
        }
    }

    private static void loadServerConfig(String filePath) {
        try {
            Properties props = new Properties();
            props.load(new FileInputStream(filePath));
            PORT = Integer.parseInt(props.getProperty("port"));
            System.out.println("Loaded server configuration: PORT=" + PORT);
        } catch (IOException e) {
            System.err.println("Error reading server config file: " + e.getMessage());
            System.exit(1);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number in config file: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void checkNodeHealth() {
        long currentTime = System.currentTimeMillis();
        activeNodes.forEach((nodeId, lastSeen) -> {
            if (currentTime - lastSeen > 30000) {
                System.out.println("Node " + nodeId + " is down.");
                activeNodes.remove(nodeId);
                nodeFileMap.remove(nodeId);
                previouslyDeadNodes.add(nodeId);
            }
        });
    }

    private static void handleReconnection(NodeInfo nodeInfo, int nodeId) {
        if (previouslyDeadNodes.contains(nodeId)) {
            System.out.println("Node " + nodeId + " has reconnected.");
            previouslyDeadNodes.remove(nodeId);
        } else {
            System.out.println("Node " + nodeId + " is up.");
        }
        nodeFileMap.put(nodeId, nodeInfo);
    }

    private static void printGlobalFileMap() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Map<Integer, NodeInfo> sortedNodes = new TreeMap<>(nodeFileMap);

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
