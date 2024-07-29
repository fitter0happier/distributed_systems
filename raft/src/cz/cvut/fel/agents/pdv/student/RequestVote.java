package cz.cvut.fel.agents.pdv.student;

import cz.cvut.fel.agents.pdv.dsand.Message;

public class RequestVote extends Message {
    private int term;
    private int lastLogIndex;
    private int lastLogTerm;

    public RequestVote(int term, int lastLogIndex, int lastLogTerm) {
        this.term = term;
        this.lastLogIndex = lastLogIndex;
        this.lastLogTerm = lastLogTerm;
    }

    public int getTerm() {
        return term;
    }

    public int lastLogIndex() {
        return lastLogIndex;
    }

    public int lastLogTerm() {
        return lastLogTerm;
    }
}
