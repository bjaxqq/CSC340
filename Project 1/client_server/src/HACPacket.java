package client_server.src;

import java.io.Serializable;
import java.util.List;

public class HACPacket implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int nodeId;
    private final String message;
    private final List<String> fileList;
    private final long timestamp;
    private final String version;

    public HACPacket(int nodeId, String message, List<String> fileList, String version) {
        this.nodeId = nodeId;
        this.message = message;
        this.fileList = fileList;
        this.timestamp = System.currentTimeMillis();
        this.version = version;
    }

    public int getNodeId() {
        return nodeId;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getFileList() {
        return fileList;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "Node " + nodeId + ": " + message + " | Version: " + version + " | Files: " + fileList;
    }
}
