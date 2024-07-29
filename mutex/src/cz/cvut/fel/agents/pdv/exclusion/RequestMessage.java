package cz.cvut.fel.agents.pdv.exclusion;

import cz.cvut.fel.agents.pdv.clocked.ClockedMessage;

public class RequestMessage extends ClockedMessage {

    public String name;

    public RequestMessage(String name) {
        this.name = name;
    }
    
}
