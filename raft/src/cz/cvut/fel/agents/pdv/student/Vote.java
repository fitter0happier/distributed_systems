package cz.cvut.fel.agents.pdv.student;

import cz.cvut.fel.agents.pdv.dsand.Message;

public class Vote extends Message {
    private boolean voted;
    private int term;

    public Vote(boolean voted, int term) {
        this.voted = voted;
        this.term = term;
    }

    public boolean getResult() {
        return voted;
    }

    public int getTerm() {
        return term;
    }
}
