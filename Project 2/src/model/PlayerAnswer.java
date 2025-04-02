package model;

import java.io.Serializable;

// Added by Brooks - Player answer submission class
public class PlayerAnswer implements Serializable {
    private final int questionId;
    private final char selectedOption;
    
    public PlayerAnswer(int questionId, char selectedOption) {
        this.questionId = questionId;
        this.selectedOption = selectedOption;
    }
    
    public int getQuestionId() {
        return questionId;
    }
    
    public char getSelectedOption() {
        return selectedOption;
    }
    
    @Override
    public String toString() {
        return "PlayerAnswer{q=" + questionId + ", answer=" + selectedOption + "}";
    }
}