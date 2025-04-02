package server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import model.PlayerAnswer;
import model.Question;
import model.QuestionBank;
import model.TCPMessage;

public class ServerTrivia {
    private ExecutorService executorService;
    private UDPThread udpThread;
    private int nextClientID = 1;
    private String serverIP;
    private int serverPort1; // TCP Port
    private int serverPort2; // UDP Port
    private Map<Integer, ClientThread> activeClients = new ConcurrentHashMap<>();
    private Question currentQuestion;
    
    // Added by Brooks - Thread-safe score tracking for all clients
    private final Map<Integer, Integer> clientScores = new ConcurrentHashMap<>();
    // Added by Brooks - Question bank to serve questions to clients
    private QuestionBank questionBank = new QuestionBank();
    // Added by Brooks - Flag to control game state
    private volatile boolean gameActive = true;
    
    // Added by Eric - Server Trivia Constructor
    public ServerTrivia() {
        executorService = Executors.newCachedThreadPool();
    }

    // Added by Eric - Read Server Config for IP and Port
    public void readConfig() {
        String configFile = "config/config.txt";
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            serverIP = reader.readLine().trim();
            serverPort1 = Integer.parseInt(reader.readLine().trim());
            serverPort2 = Integer.parseInt(reader.readLine().trim());
            System.out.println("Server configured - IP: " + serverIP + 
                             " TCP: " + serverPort1 + 
                             " UDP: " + serverPort2);
        } catch (IOException e) {
            throw new RuntimeException("Error reading server config: " + e.getMessage());
        }
    }

    // Added by Brooks - Initializes score tracking for new clients
    public void initializeClientScore(int clientID) {
        clientScores.put(clientID, 0);
        System.out.println("Score initialized for client " + clientID);
    }
    
    // Added by Brooks - Updates client score with positive/negative delta
    public void updateClientScore(int clientID, int delta) {
        clientScores.merge(clientID, delta, Integer::sum);
        broadcastScores();
    }
    
    // Added by Brooks - Retrieves current score for specified client
    public int getClientScore(int clientID) {
        return clientScores.getOrDefault(clientID, 0);
    }

    // Added by Brooks - Validates player answer against correct answer
    public boolean validateAnswer(PlayerAnswer answer) {
        Question question = questionBank.getQuestion(answer.getQuestionId());

        return question != null && 
               question.getCorrectAnswer() == answer.getSelectedOption();
    }

    // Added by Eric - Start Trivia Server
    // Modified by Brooks - Added full server startup sequence with proper resource cleanup
    public void startServer() {
        ServerSocket serverSocket = null;
        DatagramSocket udpSocket = null;
        
        try {
            // Start TCP server socket
            serverSocket = new ServerSocket(serverPort1, 50, InetAddress.getByName(serverIP));
            System.out.println("TCP server started on " + serverIP + ":" + serverPort1);

            // Start UDP server socket
            udpSocket = new DatagramSocket(serverPort2, InetAddress.getByName(serverIP));
            System.out.println("UDP server started on port " + serverPort2);

            // Start UDP message handler
            udpThread = new UDPThread(udpSocket, this);
            executorService.submit(udpThread);

            // Start game question loop
                                                               // MAKE GAME WAIT FOR 10 SEC FOR PEOPLE TO JOIN.
            startGame();

            // Accept client connections
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                int clientID = nextClientID++;
                ClientThread clientThread = new ClientThread(clientSocket, clientID, this);
                
                initializeClientScore(clientID);
                activeClients.put(clientID, clientThread);
                executorService.submit(clientThread);
            }

        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            // Added by Brooks - Proper resource cleanup
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                System.err.println("Error closing TCP server socket: " + e.getMessage());
            }
            try {
                if (udpSocket != null && !udpSocket.isClosed()) {
                    udpSocket.close();
                }
            } catch (Exception e) {
                System.err.println("Error closing UDP socket: " + e.getMessage());
            }
            shutdown();
        }
    }

    // Added by Brooks - Manages the game question flow in separate thread
    // Modified by Eric - Added ACK/NACK logic, answer handling, thread timing logic with sleeps and loops, resets Buzz queue and answer of client
    // Modified by Pierce - updates client eligibilities before sending a question to the client.
    // Modified by Brooks - Added logic for extra credit
    private void startGame() {
        new Thread(() -> {
            try {
                Thread.sleep(10000); // Initial wait for players to join
                while (gameActive && questionBank.hasMoreQuestions()) {
                    currentQuestion = questionBank.getNextQuestion();
                    eligibility();
                    broadcastQuestion(currentQuestion);
                    udpThread.clearBuzzQueue(); // Reset the buzz queue

                    // Wait 15 seconds for buzzes
                    Thread.sleep(15000);

                    // NEW: Flag to track if question was answered correctly
                    boolean correctAnswerReceived = false;
                    
                    // NEW: Process buzzes in order until correct answer or queue empty
                    while (!correctAnswerReceived && !udpThread.isBuzzQueueEmpty()) {
                        Integer answeringClientId = udpThread.getFirstBuzzedClient();
                        if (answeringClientId != null) {
                            ClientThread answeringClient = activeClients.get(answeringClientId);
                            if (answeringClient != null) {
                                // Send ACK to answering client
                                try {
                                    answeringClient.sendAck();
                                } catch (IOException e) {
                                    System.err.println("Error sending ACK to client " + answeringClientId);
                                    continue;
                                }
                                answeringClient.clearAnswer();

                                // Send NACK to all other active clients
                                activeClients.values().forEach(c -> {
                                    if (c.getClientId() != answeringClientId) {
                                        try {
                                            c.sendNack();
                                        } catch (IOException e) {
                                            System.err.println("Error sending NACK to client " + c.getClientId());
                                        }
                                    }
                                });

                                // Wait up to 10 seconds for the answer
                                long startTime = System.currentTimeMillis();
                                PlayerAnswer answer = null;
                                while (System.currentTimeMillis() - startTime < 10000) {
                                    answer = answeringClient.getAnswer();
                                    if (answer != null) {
                                        if (validateAnswer(answer)) {
                                            updateClientScore(answeringClientId, 10);
                                            try {
                                                answeringClient.sendRight();
                                            } catch (IOException e) {
                                                System.err.println("Error sending RIGHT to client " + answeringClientId);
                                            }
                                            correctAnswerReceived = true;
                                        } else {
                                            updateClientScore(answeringClientId, -10);
                                            try {
                                                answeringClient.sendWrong();
                                            } catch (IOException e) {
                                                System.err.println("Error sending WRONG to client " + answeringClientId);
                                            }
                                            // NEW: Break to give next client a chance
                                            break;
                                        }
                                        break; // Exit loop once answer is received
                                    }
                                    Thread.sleep(100); // Check every 100ms
                                }

                                if (answer == null) { // No answer received after 10 seconds
                                    updateClientScore(answeringClientId, -20);
                                    try {
                                        answeringClient.sendTimeout();
                                    } catch (IOException e) {
                                        System.err.println("Error sending TIMEOUT to client " + answeringClientId);
                                    }
                                    // NEW: Timeout - break to give next client a chance
                                }
                                answeringClient.clearAnswer();
                            }
                        }
                    }
                    Thread.sleep(5000); // Brief pause between questions
                }
                endGame();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    // Added by Brooks - Broadcasts question to all connected clients
    private void broadcastQuestion(Question question) {
        activeClients.values().forEach(client -> {
            try {
                client.sendQuestion(question);
            } catch (IOException e) {
                System.err.println("Error sending question to client " + client.getClientId());
            }
        });
    }

    private void broadcastScores() {
    activeClients.values().forEach(client -> {
        try {
            client.sendMessage(new TCPMessage(TCPMessage.MessageType.SCORE_UPDATE, 
                new HashMap<>(clientScores)));
        } catch (IOException e) {
            System.err.println("Error sending scores to client " + client.getClientId());
        }
    });
}

    //Added by Pierce - sends ELIGIBILITY message to client allowing them to press the poll button.
    private void eligibility() {
        activeClients.values().forEach(client -> {
            try {
                client.sendEligibility();
            } catch (IOException e) {
                System.err.println("Error sending eligibility to client " + client.getClientId());
            }
        });
    }

    // Added by Brooks - Ends game and announces final scores
    private void endGame() {
        gameActive = false;
        activeClients.values().forEach(client -> {
            try {
                client.sendGameOver();
            } catch (IOException e) {
                System.err.println("Error sending game over to client " + client.getClientId());
            }
        });
        
        System.out.println("Game over! Final scores:");
        clientScores.forEach((id, score) -> 
            System.out.println("Client " + id + ": " + score + " points"));
    }

    // Added by Eric - Shutdown server thread pool
    private void shutdown() {
        executorService.shutdown();
        System.out.println("Server shutting down...");
    }

    // Added by Eric - Method to get all connected clients
    public Map<Integer, ClientThread> getActiveClients() {
        return activeClients;
    }

    // Added by Eric - Method to add a new client to the map
    public void addClient(int clientID, ClientThread clientThread) {
        activeClients.put(clientID, clientThread);
    }

    // Added by Eric - Method to remove a client from the map
    public void removeClient(int clientID) {
        activeClients.remove(clientID);
        System.out.println("Client " + clientID + " removed");
    }

    // Added by Eric - Method to get the current question the game server/loop sent to the clients
    public Question getCurrentQuestion() {
        return currentQuestion;
    }

    // Added by Eric - main method to start trivia server
    public static void main(String[] args) {
        ServerTrivia server = new ServerTrivia();
        server.readConfig();
        server.startServer();
    }
}