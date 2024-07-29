package cz.cvut.fel.agents.pdv.swim;

import cz.cvut.fel.agents.pdv.dsand.Message;

public class AckMessage extends Message {

    public String originalSender;
    public String responder;
    
    public AckMessage(String responder, String originalSender) {
        this.originalSender = originalSender;
        this.responder = responder;
    }

    public String getOriginalSender() {
        return originalSender;
    }

    public String getResponder() {
        return responder;
    }
}
