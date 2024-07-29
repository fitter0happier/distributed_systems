package cz.cvut.fel.agents.pdv.swim;

import cz.cvut.fel.agents.pdv.dsand.Message;

public class PingReqMessage extends Message {
    public final String whoToPing;

    public PingReqMessage(String whoToPing) {
        this.whoToPing = whoToPing;
    }

    public String getTargetId() {
        return whoToPing;
    }
}
