package model;

import java.io.Serializable;

// Added by Brooks - Score update message class
public class ScoreUpdate implements Serializable {
    private final int score;
    
    public ScoreUpdate(int score) {
        this.score = score;
    }
    
    public int getScore() {
        return score;
    }
    
    @Override
    public String toString() {
        return "ScoreUpdate{score=" + score + "}";
    }
}