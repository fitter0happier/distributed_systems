package cz.cvut.fel.agents.pdv.student;

import cz.cvut.fel.agents.pdv.dsand.Message;

public class AppendConfirmation extends Message {
    private int term;
    private boolean success;
    private int matchedOn;

    public AppendConfirmation(int term, boolean success, int matchedOn) {
        this.term = term;
        this.success = success;
        this.matchedOn = matchedOn;
    }

    public AppendConfirmation(int term, boolean success) {
        this.term = term;
        this.success = success;
        this.matchedOn = 0;
    }

    public int getTerm() {
        return term;
    }

    public boolean getResult() {
        return success;
    }

    public int getMatchedOn() {
        return matchedOn;
    }
}
