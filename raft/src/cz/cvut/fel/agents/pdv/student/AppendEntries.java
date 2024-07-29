package cz.cvut.fel.agents.pdv.student;

import cz.cvut.fel.agents.pdv.dsand.Message;
import cz.cvut.fel.agents.pdv.dsand.Pair;

public class AppendEntries extends Message {
    private int term;
    private int prevLogIndex;
    private int prevLogTerm;
    private Pair<Integer, Pair<String, Pair<String, String>>> entry;
    private int leaderCommit;

    public AppendEntries(int term, 
                         int prevLogIndex, 
                         int prevLogTerm, 
                         Pair<Integer, Pair<String, Pair<String, String>>> entry,
                         int leaderCommit) {
        this.term = term;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.entry = entry;
        this.leaderCommit = leaderCommit;
    }

    public AppendEntries(int term, 
                         Pair<Integer, Pair<String, Pair<String, String>>> entry,
                         int leaderCommit) {
        this.term = term;
        this.prevLogIndex = 0;
        this.prevLogTerm = 0;
        this.entry = entry;
        this.leaderCommit = leaderCommit;
    }

    public int getTerm() {
        return term;
    }

    public int getPrevLogIndex() {
        return prevLogIndex;
    }

    public int getPrevLogTerm() {
        return prevLogTerm;
    }

    public Pair<Integer, Pair<String, Pair<String, String>>> getNewEntry() {
        return entry;
    }

    public int getLeaderCommit() {
        return leaderCommit;
    }
}
