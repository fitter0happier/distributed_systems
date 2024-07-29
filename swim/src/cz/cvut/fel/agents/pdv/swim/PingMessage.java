package cz.cvut.fel.agents.pdv.swim;

import cz.cvut.fel.agents.pdv.dsand.Message;

public class PingMessage extends Message {

    public String originalSender;
    public String router;

    public PingMessage(String originalSender) {
        this.originalSender = originalSender;
        this.router = null;
    }

    public String getOriginalSender() {
        return originalSender;
    }

    public String getRouter() {
        return router;
    }

    public void setRouter(String router) {
        this.router = router;
    }
    
}
