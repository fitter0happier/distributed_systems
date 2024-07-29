package cz.cvut.fel.agents.pdv.swim;

import cz.cvut.fel.agents.pdv.dsand.Message;
import cz.cvut.fel.agents.pdv.dsand.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Iterator;

/**
 * Trida s implementaci metody act() pro proces Failure Detector. Tuto tridu (a tridy pouzivanych zprav) budete
 * odevzdavat. Do tridy si muzete doplnit vlastni pomocne datove struktury. Hodnoty muzete inicializovat primo
 * v konstruktoru. Klicova je metoda act(), kterou vola kazda instance tridy FailureDetectorProcess ve sve metode
 * act(). Tuto metodu naimplementujte podle protokolu SWIM predstaveneho na prednasce.
 *
 * Pokud si stale jeste nevite rady s frameworkem, inspiraci muzete nalezt v resenych prikladech ze cviceni.
 */
public class ActStrategy {
    private Random random = new Random();

    // maximalni zpozdeni zprav
    private final int maxDelayForMessages;
    private List<String> otherProcesses;

    //processes that I've sent ping to and am waiting for their response 
    private HashMap<String, Integer> pingedProcesses;

    //processes that I did not receive confirmation from and requested additional confirmation from another processes about them
    private HashMap<String, Integer> ackReqProcesses;

    private int amountOfRequests = 8;
    private int upperBoundOnMessages;

    private int msgsSentSoFar = 0;


    // Definujte vsechny sve promenne a datove struktury, ktere budete potrebovat

    public ActStrategy(int maxDelayForMessages, List<String> otherProcesses,
                       int timeToDetectKilledProcess, int upperBoundOnMessages) {
        this.maxDelayForMessages = maxDelayForMessages;
        this.otherProcesses = otherProcesses;
        this.upperBoundOnMessages = upperBoundOnMessages;

        // Doplne inicializaci
        pingedProcesses = new HashMap<String, Integer>();
        ackReqProcesses = new HashMap<String, Integer>();
    }

    /**
     * Metoda je volana s kazdym zavolanim metody act v FailureDetectorProcess. Metodu implementujte
     * tak, jako byste implementovali metodu act() v FailureDetectorProcess, misto pouzivani send()
     * pridejte zpravy v podobe paru - prijemce, zprava do listu. Zpravy budou nasledne odeslany.
     * <p>
     * Diky zavedeni teto metody muzeme kontrolovat pocet odeslanych zprav vasi implementaci.
     */
    public List<Pair<String, Message>> act(Queue<Message> inbox, String disseminationProcess) {

        // Od DisseminationProcess muzete dostat zpravu typu DeadProcessMessage, ktera Vas
        // informuje o spravne detekovanem ukoncenem procesu.
        // DisseminationProcess muzete poslat zpravu o detekovanem "mrtvem" procesu.
        // Zprava musi byt typu PFDMessage.
        List<Pair<String, Message>> outbox = new ArrayList<>();

        //working with inbox
        while (!inbox.isEmpty()) {
            Message msg = inbox.poll();

            if (msg instanceof DeadProcessMessage) {
                //removing process from list of alive processes
                otherProcesses.remove(((DeadProcessMessage) msg).getProcessID());

            } else if (msg instanceof PingReqMessage) {
                //requests to ping another process
                String target = (((PingReqMessage) msg).getTargetId());
                PingMessage ping = new PingMessage(msg.sender);
                outbox.add(new Pair<String,Message>(target, ping));

            } else if (msg instanceof PingMessage) {
                //responding to ping
                String whoPinged = msg.sender;
                AckMessage ack = new AckMessage(null, ((PingMessage) msg).getOriginalSender());
                outbox.add(new Pair<String, Message>(whoPinged, ack));

            } else {
                //receiving Ack message
                
                //process that sent ack
                String acker = msg.sender;

                //who the ack is being routed to
                String originalSender = ((AckMessage) msg).getOriginalSender();

                //i was the one who requested the ack
                if (msg.recipient.equals(originalSender)) {
                    String responder = ((AckMessage) msg).getResponder();
                    ackReqProcesses.remove(responder);

                } else if (originalSender == null) {
                    pingedProcesses.remove(acker);

                } 
                //i am not the original sender, need to reroute 
                else {
                    AckMessage ack = new AckMessage(acker, originalSender);
                    outbox.add(new Pair<String, Message>(originalSender, ack)); 
                }
            }
        }

        //forming my own messages for outbox

        //increasing counter for each process in the pingedProcesses that I did not receive acks from
        Iterator<Map.Entry<String, Integer>> iterator = pingedProcesses.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Integer> process = iterator.next(); 
            String processID = process.getKey();
            int processWait = process.getValue();

            if (processWait == 2 * maxDelayForMessages) {
                //we are waiting too long for a response, we need to ack another processes to ping this process
                for (int i = 0; i < amountOfRequests; ++i) {
                    int randomIndex = random.nextInt(otherProcesses.size());
                    String processToRequest = otherProcesses.get(randomIndex);
                    PingReqMessage pingReq = new PingReqMessage(processID);
                    outbox.add(new Pair<String, Message>(processToRequest, pingReq));
                }

                iterator.remove();
                ackReqProcesses.put(processID, 0);

            } else {
                int newProcessWait = processWait + 1;
                pingedProcesses.put(processID, newProcessWait);

            }
        }

        //increasing counter for each process in the ackReqProcesses that I did not receiver acks from
        iterator = ackReqProcesses.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Integer> process = iterator.next();
            String processID = process.getKey();
            int processWait = process.getValue();

            if (processWait == 4 * maxDelayForMessages) {
                //we mark process as dead and form the message to dissProcess
                PFDMessage msg = new PFDMessage(processID);
                outbox.add(new Pair<String,Message>(disseminationProcess, msg));
                iterator.remove();

            } else {
                int newProcessWait = processWait + 1;
                ackReqProcesses.put(processID, newProcessWait);
            }
        }


        //getting a random process to ping
        int randomIndex = random.nextInt(otherProcesses.size());
        String processToPing = otherProcesses.get(randomIndex);
        PingMessage ping = new PingMessage(null);
        outbox.add(new Pair<String, Message>(processToPing, ping));
        pingedProcesses.put(processToPing, 0);
        

        //check how many messages we have sent so far
        msgsSentSoFar += outbox.size();

        //if we have sent too many messages, we need to finish communication
        if (msgsSentSoFar > upperBoundOnMessages) {
            List<Pair<String, Message>> empty_outbox = new ArrayList<>();
            return empty_outbox;
        }
        
        return outbox;
    }

}
