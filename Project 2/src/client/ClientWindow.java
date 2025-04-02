package client;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import javax.swing.*;

import model.PlayerAnswer;
import model.Question;
import model.TCPMessage;
import model.UDPMessage;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

// Modified by Brooks - Client GUI for multiplayer trivia game
// Handles all user interaction and network communication
public class ClientWindow implements ActionListener {
    // UI Components
    private JButton poll;
    private JButton submit;
    private JRadioButton[] options;
    private ButtonGroup optionGroup;
    private JLabel question;
    private JLabel timer;
    private JLabel score;
    private JTextArea leaderboardArea;
    private JScrollPane leaderboardScroll;
    private TimerTask clock;
    private JFrame window;
    
    // Game State
    private Question currentQuestion;
    private int playerScore = 0;
    private boolean eligibility = false;
    
    // Network Configuration
    private String serverIP;
    private int TCPserverPort;
    private int UDPserverPort;
    
    // Network Connections
    private ObjectInputStream tcpIn;
    private ObjectOutputStream tcpOut;
    private Socket tcpSocket;

    // Added by Eric - Client window constructor
    // Modified by pierce - start eligibility as false for each client.
    public ClientWindow() {
        this.eligibility = false;
        initializeUI();
        System.out.println("Initialized UI");
    
        readConfig();
        System.out.println("Read config");

        connectToServer();
        System.out.println("Connected to server");

        window.setVisible(true);
        new Thread(this::listenForTcpMessages).start();
    }

    // Added by Eric - Reads server configuration
    private void readConfig() {
        String configFile = "config/config.txt";
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            serverIP = reader.readLine().trim();
            TCPserverPort = Integer.parseInt(reader.readLine().trim());
            UDPserverPort = Integer.parseInt(reader.readLine().trim());
            System.out.println("Configured server: " + serverIP + ":" + TCPserverPort);
        } catch (IOException e) {
            throw new RuntimeException("Error reading config: " + e.getMessage());
        }
    }

    // Added by Brooks - Initializes all UI components
    // Modified by Pierce - disabled pol button at the start of running the code.
    private void initializeUI() {
        window = new JFrame("Trivia Client");
        window.setSize(600, 400); // Wider window for leaderboard
        window.setLayout(new BorderLayout(10, 10));
        window.getContentPane().setBackground(new Color(240, 240, 240));

        // Main game panel (left side)
        JPanel gamePanel = new JPanel();
        gamePanel.setLayout(null);
        gamePanel.setPreferredSize(new Dimension(350, 400));
        gamePanel.setBackground(new Color(250, 250, 250));
        gamePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Question area
        question = new JLabel("<html><div style='width:330px;'>Waiting for game to start...</div></html>");
        question.setBounds(10, 10, 330, 100);
        question.setFont(new Font("Arial", Font.PLAIN, 14));
        gamePanel.add(question);
        
        // Answer options
        options = new JRadioButton[4];
        optionGroup = new ButtonGroup();
        for(int i = 0; i < options.length; i++) {
            options[i] = new JRadioButton("Option " + (i+1));
            options[i].addActionListener(this);
            options[i].setBounds(10, 120 + (i * 30), 330, 25);
            options[i].setFont(new Font("Arial", Font.PLAIN, 12));
            options[i].setEnabled(false);
            gamePanel.add(options[i]);
            optionGroup.add(options[i]);
        }
        
        // Timer display
        timer = new JLabel("", SwingConstants.CENTER);
        timer.setBounds(75, 250, 200, 20);
        timer.setFont(new Font("Arial", Font.BOLD, 16));
        timer.setForeground(Color.BLUE);
        gamePanel.add(timer);
        
        // Score display
        score = new JLabel("SCORE: 0");
        score.setBounds(50, 250, 100, 20);
        score.setFont(new Font("Arial", Font.BOLD, 14));
        gamePanel.add(score);
        
        // Buttons
        poll = new JButton("Poll");
        poll.setBounds(50, 300, 100, 30);
        poll.addActionListener(this);
        poll.setEnabled(false);
        gamePanel.add(poll);
        
        submit = new JButton("Submit");
        submit.setBounds(200, 300, 100, 30);
        submit.addActionListener(this);
        submit.setEnabled(false);
        gamePanel.add(submit);

        // Leaderboard panel (right side)
        JPanel leaderboardPanel = new JPanel();
        leaderboardPanel.setLayout(new BorderLayout());
        leaderboardPanel.setPreferredSize(new Dimension(220, 400));
        leaderboardPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Leaderboard"),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        leaderboardPanel.setBackground(new Color(250, 250, 250));

        leaderboardArea = new JTextArea();
        leaderboardArea.setEditable(false);
        leaderboardArea.setFont(new Font("Consolas", Font.BOLD, 12));
        leaderboardArea.setMargin(new Insets(10, 10, 10, 10));
        leaderboardArea.setBackground(new Color(240, 240, 240));
        
        leaderboardScroll = new JScrollPane(leaderboardArea);
        leaderboardScroll.setBorder(BorderFactory.createEmptyBorder());
        leaderboardPanel.add(leaderboardScroll, BorderLayout.CENTER);

        // Add panels to main window
        window.add(gamePanel, BorderLayout.WEST);
        window.add(leaderboardPanel, BorderLayout.EAST);
        
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    private void updateLeaderboard(Map<Integer, Integer> scores) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-12s %6s\n", "PLAYER", "SCORE"));
        sb.append("-------------------\n");
        
        scores.entrySet().stream()
            .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
            .forEach(entry -> {
                sb.append(String.format("%-12s %6d\n", 
                    "Player " + entry.getKey(), 
                    entry.getValue()));
            });
        
        leaderboardArea.setText(sb.toString());
        leaderboardArea.setCaretPosition(0); // Scroll to top
    }

    // Added by Brooks - Establishes server connection with proper resource management
    private void connectToServer() {
        System.out.println("Attempting to connect to server...");
        while (true) { // Keep retrying until connected
            try {
                tcpSocket = new Socket();
                tcpSocket.connect(new InetSocketAddress(serverIP, TCPserverPort), 5000);
                tcpOut = new ObjectOutputStream(tcpSocket.getOutputStream());
                tcpIn = new ObjectInputStream(tcpSocket.getInputStream());
                System.out.println("Connected to server!");
                break;
            } catch (IOException e) {
                System.out.println("Failed to connect to server. Retrying in 3 seconds...");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    System.out.println("Connection attempt interrupted.");
                    return;
                }
            }
        }
    }

    // Added by Brooks - Listens for incoming TCP messages
    private void listenForTcpMessages() {
        try {
            while (true) {
                Object message = tcpIn.readObject();
                if (message instanceof TCPMessage) {
                    processTcpMessage((TCPMessage) message);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace(); // Log the specific exception
            SwingUtilities.invokeLater(() -> 
                JOptionPane.showMessageDialog(window, "Connection error: " + e.getMessage()));
            System.exit(1);
        }
    }

    // Added by Brooks - Handles different message types
    // Modified by Eric - Correctly handles the flow of the game using the various methods implemented, Question message serves as the "NEXT"
    private void processTcpMessage(TCPMessage message) {
        SwingUtilities.invokeLater(() -> {
            System.out.print(message.getType());
            switch (message.getType()) {
                case QUESTION:
                    currentQuestion = (Question) message.getPayload();
                    loadQuestion(currentQuestion);
                    break;
                    
                case ACK:
                    handleAck();
                    break;
                    
                case NACK:
                    handleNack();
                    break;
                    
                case CORRECT:
                    playerScore += 10;
                    updateScore(playerScore);
                    showFeedback(1);
                    break;
                    
                case WRONG:
                    playerScore -= 10;
                    updateScore(playerScore);
                    showFeedback(2);
                    break;

                case TIMEOUT:
                    playerScore -= 20;
                    updateScore(playerScore);
                    showFeedback(3);
                    break;
                    
                case GAME_OVER:
                    endGame();
                    break;
                    
                case SCORE_UPDATE:
                    if (message.getPayload() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<Integer, Integer> scores = (Map<Integer, Integer>) message.getPayload();
                        updateLeaderboard(scores);
                    }
                    break;
                
                case KILL_CLIENT:
                    killClient();
                    break;

                    case ELIGIBILITY:
                    this.eligibility = true;
                    poll.setEnabled(true);
                    break;
            }
        });
    }

    // Added by Pierce
    private void killClient(){
        JOptionPane.showMessageDialog(window, "You have been terminated by the server.");
        try {
            Thread.sleep(3000);  // Delay for 3 seconds
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    // Added by Brooks - Loads and displays new question
    private void loadQuestion(Question question) {
        this.currentQuestion = question;
        
        try {
            // Assuming `question` is already defined and initialized
            // Modified by Eric - utilize new question methods after Question class modifications
            this.question.setText("<html>Q" + question.getQuestionNumber() + 
                                   ". " + question.getQuestionText() + "</html>");
                                   String[] currentOptions = question.getOptions();
        for (int i = 0; i < options.length; i++) {
            options[i].setText(currentOptions[i]);
            options[i].setSelected(false);
        }
        
        System.out.print("load");
        resetForNewQuestion();
        } catch (NullPointerException e) {
            System.err.println("");
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
        }
        
    }

    // Added by Brooks - Resets UI for new question
    private void resetForNewQuestion() {
        if (clock != null) clock.cancel();
        
        optionGroup.clearSelection();
        for (JRadioButton option : options) {
            option.setSelected(false);
            option.setEnabled(false);
        }
        
        clock = new TimerCode(15, false);
        new Timer().schedule(clock, 0, 1000);
        if(eligibility == true){
            poll.setEnabled(true);
        }
        submit.setEnabled(false);
    }

    // Added by Brooks - Handles poll button click
    // Modified by Pierce - Poll should be able to be spammed until timer reaches 0(client recieves message from serv).
    private void handlePoll() {
        sendBuzzMessage();
    }

    // Added by Eric - send the buzz to the server using UDP when polling
    private void sendBuzzMessage() {
        try (DatagramSocket socket = new DatagramSocket()) {
            UDPMessage message = new UDPMessage(
                System.currentTimeMillis(), 
                InetAddress.getLocalHost().getHostAddress()
            );
            
            byte[] data = message.encode();
            DatagramPacket packet = new DatagramPacket(
                data, data.length, 
                InetAddress.getByName(serverIP), 
                UDPserverPort
            );
            
            socket.send(packet);
            System.out.println("Buzz message sent");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(window, "Error sending buzz: " + e.getMessage());
        }
    }

    // Added by Brooks - Handles ACK from server
    // Modified by Eric - Polling disabled
    private void handleAck() {
        System.out.println("Received ACK from server");
        poll.setEnabled(false);
        submit.setEnabled(true);
        for (JRadioButton option : options) {
            option.setEnabled(true);
        }
        
        if (clock != null) {
            clock.cancel();
        }
        clock = new TimerCode(10, true);
        new Timer().schedule(clock, 0, 1000);
    }

    // Added by Brooks - Handles NACK from server
    // Modified by Eric - disabled polling, cancels the buzzing timer and replaces with too slow text
    private void handleNack() {
        System.out.println("Received NACK from server");
        poll.setEnabled(false);
        if (clock != null) {
            clock.cancel();
        }
        timer.setText("Too slow!");
    }

    // Added by Brooks - Updates score display
    private void updateScore(int newScore) {
        playerScore = newScore;
        score.setText("SCORE: " + playerScore);
    }

    // Added by Brooks - Shows feedback popup
    // Modified by Eric - Three different types of popups with point gain/loss instead of just right/wrong
    private void showFeedback(int number) {
        String display;
        if (number == 1) {
            display = "Correct! +10 points";
        } else if (number == 2) {
            display = "Wrong! -10 points";
        } else {
            display = "Timeout! -20 points :(";
        }
        JOptionPane.showMessageDialog(window, display);
    }

    // Added by Brooks - Ends game session
    private void endGame() {
        JOptionPane.showMessageDialog(window, 
            "Game over! Your final score: " + playerScore);
        window.dispose();
        System.exit(0);
    }

    // Added by Eric - Handles all button clicks
    @Override
    public void actionPerformed(ActionEvent e) {
        String command = e.getActionCommand();
        
        if ("Poll".equals(command)) {
            handlePoll();
        } 
        else if ("Submit".equals(command)) {
            handleSubmit();
        }
    }

    // Added by Brooks - Handles answer submission
    // Modified by Eric - Correctly uses new question number method
    private void handleSubmit() {
        char selectedAnswer = ' ';
        for (int i = 0; i < options.length; i++) {
            if (options[i].isSelected()) {
                selectedAnswer = (char) ('A' + i);
                break;
            }
        }
        
        if (selectedAnswer != ' ') {
            try {
                PlayerAnswer answer = new PlayerAnswer(
                    currentQuestion.getQuestionNumber(),
                    selectedAnswer
                );
                tcpOut.writeObject(answer);
                tcpOut.flush();
                submit.setEnabled(false);
                if (clock != null) clock.cancel();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(window, "Error submitting answer");
            }
        }
    }

    // Modified by Brooks - Enhanced timer class
    // Modified by Eric - removes local penalty application and waits for TIMEOUT of QUESTION
    private class TimerCode extends TimerTask {
        private int duration;
        public TimerCode(int duration, boolean isAnswerPeriod) {
            this.duration = duration;
        }
        
        @Override
        public void run() {
            SwingUtilities.invokeLater(() -> {
                if (duration < 0) {
                    timer.setText("<html>Time expired</html>");
                    this.cancel();
                } else {
                    timer.setForeground(duration < 6 ? Color.RED : Color.BLUE);
                    timer.setText(duration + "");
                    duration--;
                }
                window.repaint();
            });
        }
    }

    public static void main(String[] args) {
        new ClientWindow();
    }
}