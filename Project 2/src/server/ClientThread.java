package server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import model.PlayerAnswer;
import model.Question;
import model.TCPMessage;
import model.UDPMessage;


// Added by Eric - THIS CLASS HANDLES THE COMMUNICATION BETWEEN THE SERVER AND THE ONE CLIENT ON THAT THREAD
// TCP
// Modified by Brooks - Enhanced client thread handler with full TCP communication
// Handles all incoming and outgoing messages for a single client connection
public class ClientThread implements Runnable {
    private final int id;                 // Added by Brooks - Unique client identifier
    private final Socket socket;          // Added by Brooks - Client connection socket
    private final ServerTrivia server;    // Added by Brooks - Reference to main server
    private final BlockingQueue<UDPMessage> messageQueue = new LinkedBlockingQueue<>();
    private ObjectOutputStream out;       // Added by Brooks - TCP output stream
    private boolean isActive = true;      // Added by Brooks - Connection status flag
    private volatile PlayerAnswer answer = null; // Added by Eric - safe player answer

    // Added by Eric - Contructor for the client thread
    // Modified by Brooks - Added server reference
    public ClientThread(Socket clientSocket, int id, ServerTrivia server) {
        this.id = id;
        this.socket = clientSocket;
        this.server = server;
    }

    // Added by Eric - Method to listen for the incoming TCP packets from the client
    // Modified by Brooks - Implemented run() with score tracking
    @Override
    public void run() {
        // Added by Brooks - Main client communication loop
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            this.out = new ObjectOutputStream(socket.getOutputStream());
            
            // Added by Brooks - Send initial score to client
            sendMessage(new TCPMessage(TCPMessage.MessageType.SCORE_UPDATE, server.getClientScore(id)));
            sendMessage(new TCPMessage(TCPMessage.MessageType.QUESTION, server.getCurrentQuestion()));
            
            // Added by Brooks - Continuous message processing loop
            while (isActive) {
                Object input = in.readObject();
                if (input instanceof PlayerAnswer) {
                    processAnswer((PlayerAnswer) input);
                }
                // Additional message types can be handled here
            }
        } catch (Exception e) {
            System.err.println("Client " + id + " disconnected: " + e.getMessage());
        } finally {
            // Added by Brooks - Cleanup on disconnect
            server.removeClient(id);
            closeConnection();
        }
    }

    // Added by Brooks - Safely sends a TCP message to the client
    public void sendMessage(TCPMessage message) throws IOException {
        if (out != null) {
            out.writeObject(message);
            out.flush();
            System.out.println("Sent to client " + id + ": " + message);
        }
    }

    // Added by Eric - Processes player answer
    // Modified by Brooks - Added better error handling for answer processing
    private void processAnswer(PlayerAnswer answer) throws IOException {
        if (answer != null) {
            this.answer = answer;
            System.out.println("Received answer from client " + id + 
                             " for Q" + answer.getQuestionId() + 
                             ": " + answer.getSelectedOption());
        }
    }

    // Added by Eric - Get player answer
    public PlayerAnswer getAnswer() {
        return answer;
    }

    // Added by Eric - Clear player answer
    public void clearAnswer() {
        this.answer = null;
    }

    // Added by Brooks - Sends a new question to the client
    public void sendQuestion(Question question) throws IOException {
        sendMessage(new TCPMessage(TCPMessage.MessageType.QUESTION, question));
    }
    
    // Added by Brooks - Acknowledges first buzz attempt
    public void sendAck() throws IOException {
        sendMessage(new TCPMessage(TCPMessage.MessageType.ACK));
    }
    
    // Added by Brooks - Notifies late buzz attempts
    public void sendNack() throws IOException {
        sendMessage(new TCPMessage(TCPMessage.MessageType.NACK));
    }
    
    // Added by Brooks - Terminates client connection gracefully
    public void sendGameOver() throws IOException {
        sendMessage(new TCPMessage(TCPMessage.MessageType.GAME_OVER));
        isActive = false;
    }

    // Added by Eric - Notifies client if they answered correctly
    public void sendRight() throws IOException {
        sendMessage(new TCPMessage(TCPMessage.MessageType.CORRECT));
    }

    // Added by Eric - Notifies client if they answered wrong
    public void sendWrong() throws IOException {
        sendMessage(new TCPMessage(TCPMessage.MessageType.WRONG));
    }

    // Added by Eric - Notifies client if they did not send answer in time
    public void sendTimeout() throws IOException {
        sendMessage(new TCPMessage(TCPMessage.MessageType.TIMEOUT));
    }

    // Added by Pierce - will allow the client to poll when sent to the client.
    public void sendEligibility() throws IOException {
        sendMessage(new TCPMessage(TCPMessage.MessageType.ELIGIBILITY));
    }
    
    // Added by Brooks - Cleans up network resources
    private void closeConnection() {
        try {
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error closing connection for client " + id);
        }
    }
    
    // Added by Brooks - Getter for client ID
    public int getClientId() {
        return id;
    }
    
    // Added by Brooks - Getter for client IP address
    public String getClientIP() {
        return socket.getInetAddress().getHostAddress();
    }
    
    // Added by Brooks - Adds UDP message to processing queue
    public void addUdpMessage(UDPMessage message) {
        messageQueue.add(message);
    }
}
