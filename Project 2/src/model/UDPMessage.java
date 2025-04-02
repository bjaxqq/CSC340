package model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class UDPMessage implements Serializable {
    
    // Added by Eric - Data Fields of timestamp and source ip
    private static final long serialVersionUID = 1L;
    private long timestamp;
    private String ip;

    // Added by Eric - UDP Message Protocol Class
    public UDPMessage(long timestamp, String ip) {
        this.timestamp = timestamp;
        this.ip = ip;
    }

    // Added by Eric - Getter for the timestamp and the client IP
    public long getTimestamp() {
        return timestamp;
    }

    public String getClientIP() {
        return ip;
    }

    // Added by Eric - Used to decode the UDP message from byte array to object using serilization
    public static UDPMessage decode(byte[] data) {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return (UDPMessage) ois.readObject();
        } catch (Exception e) {
            System.err.println("Error decoding message: " + e.getMessage());
            return null;
        }
    }

    // Added by Eric - Used to encode the UDP message to a byte array using serialization
    public byte[] encode() {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(this);
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            System.err.println("Error encoding message: " + e.getMessage());
            return new byte[0];
        }
    }
}
