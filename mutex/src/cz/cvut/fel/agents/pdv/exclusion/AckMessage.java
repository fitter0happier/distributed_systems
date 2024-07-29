package cz.cvut.fel.agents.pdv.exclusion;

import cz.cvut.fel.agents.pdv.clocked.ClockedMessage;

public class AckMessage extends ClockedMessage {
    
    public String name;

    public AckMessage(String name) {
        this.name = name;
    }

}
