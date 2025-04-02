package server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import model.UDPMessage;

// Added by Eric - THIS CLASS HANDLES THE COMMUNICATION BETWEEN THE SERVER AND ALL THE CLIENTS
// UDP 
public class UDPThread implements Runnable{

    private DatagramSocket socket;
    private ServerTrivia server;
    private Queue<Integer> buzzQueue;
    
    public UDPThread(DatagramSocket socket, ServerTrivia server) {
        this.socket = socket;
        this.server = server;
        this.buzzQueue = new ConcurrentLinkedQueue<>();
    }

    // Added by Eric - Method to listen for incoming UDP packets from all clients
    @Override
    public void run() {
        try {
            byte[] incomingData = new byte[512];
            while (true) {
                DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
                socket.receive(incomingPacket);

                // Process the incoming packet
                String clientIP = incomingPacket.getAddress().getHostAddress();
                
                // Decode the message from the packet
                UDPMessage receivedMessage = UDPMessage.decode(
                    java.util.Arrays.copyOfRange(incomingPacket.getData(), 0, incomingPacket.getLength())
                );
                
                if (receivedMessage == null) {
                    System.err.println("Failed to decode message from IP: " + clientIP);
                    continue;
                }

                // Find the client thread based on IP
                ClientThread clientThread = getClientThreadByIP(clientIP);
                if (clientThread != null) {
                    // Process the message and handle buzz logic
                    processBuzz(clientThread, receivedMessage);
                } else {
                    System.err.println("Unknown client IP: " + clientIP);
                }
            }
        } catch (Exception e) {
            System.err.println("Error in UDP listening thread: " + e.getMessage());
        }
    }

    // Added by Eric - Resets the buzz queue after the game loop for one question finishes in the server
    // Modified by Brooks - Improved queue clearing
    public void clearBuzzQueue() {
        if (!buzzQueue.isEmpty()) {
            System.out.println("Clearing buzz queue: " + buzzQueue);
            buzzQueue.clear();
        }
    }

    // Added by Eric - Allows the server to get the ID of the first buzzed client
    public Integer getFirstBuzzedClient() {
        return buzzQueue.poll();
    }

    // Added by Brooks - Added method to check if buzz queue is empty
    public boolean isBuzzQueueEmpty() {
        return buzzQueue.isEmpty();
    }

    // Added by Eric - Method to process the buzz while maintaining timestamp order
    // Modified by Brooks - Simplified buzz processing for extra credit feature
    private void processBuzz(ClientThread clientThread, UDPMessage receivedMessage) {
        if (clientThread != null) {
            Integer clientID = clientThread.getClientId();
            
            // Prevent duplicate buzzes from same client
            if (!buzzQueue.contains(clientID)) {
                buzzQueue.add(clientID);
                System.out.println("Client " + clientID + " added to buzz queue");
            }
            
            System.out.println("Current Queue: " + buzzQueue);
        } else {
            System.out.println("Client not found for IP: " + receivedMessage.getClientIP());
        }
    }

    // Added by Eric - Method to get the client thread based on the UDP client sender IP
    private ClientThread getClientThreadByIP(String ip) {
        for (ClientThread client : server.getActiveClients().values()) {
            if (client.getClientIP().equals(ip)) {
                return client;
            }
        }
        return null; 
    }
}
