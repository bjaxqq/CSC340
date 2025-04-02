package model;

import java.io.Serializable;

// Added by Brooks - TCP message protocol class for all server-client communications
// This class defines all possible message types and carries payload data when needed
public class TCPMessage implements Serializable {
    // Added by Brooks - Enumeration of all possible message types
    // Modified by Eric - Removed NEXT and utilizing QUESTION as the NEXT
    //                  - Added the TIMEOUT message for when the ack client does not answer
    public enum MessageType {
        QUESTION,     // Server sends question to clients
        ACK,          // Positive acknowledgment for first buzz
        NACK,         // Negative acknowledgment for late buzz
        CORRECT,      // Server confirms correct answer
        WRONG,        // Server indicates wrong answer
        TIMEOUT,      // Server indicates no answer
        SCORE_UPDATE, // Broadcast updated scores
        GAME_OVER,    // Signals end of game
        KILL_CLIENT,  // Kills the client
        ELIGIBILITY   // Allow the client to poll
    }
    
    private final MessageType type;  // Added by Brooks - The message type
    private final Object payload;    // Added by Brooks - Optional payload data
    
    // Added by Brooks - Constructor for messages without payload
    public TCPMessage(MessageType type) {
        this(type, null);
    }
    
    // Added by Brooks - Constructor for messages with payload
    public TCPMessage(MessageType type, Object payload) {
        this.type = type;
        this.payload = payload;
    }
    
    // Added by Brooks - Getter for message type
    public MessageType getType() {
        return type;
    }
    
    // Added by Brooks - Getter for payload data
    public Object getPayload() {
        return payload;
    }
    
    @Override
    public String toString() {
        return "TCPMessage{" + type + (payload != null ? ", " + payload : "") + "}";
    }
}