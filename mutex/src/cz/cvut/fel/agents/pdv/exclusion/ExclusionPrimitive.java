package cz.cvut.fel.agents.pdv.exclusion;

import cz.cvut.fel.agents.pdv.clocked.ClockedMessage;
import cz.cvut.fel.agents.pdv.clocked.ClockedProcess;

public class ExclusionPrimitive {

    /**
     * Stavy, ve kterych se zamek muze nachazet.
     */
    enum AcquisitionState {
        RELEASED,    // Uvolneny   - Proces, ktery vlastni aktualni instanci ExclusionPrimitive o pristup do kriticke
                     //              sekce nezada

        WANTED,      // Chteny     - Proces, ktery vlastni aktualni instanci ExclusionPrimitive zada o pristup do
                     //              kriticke sekce. Ten mu ale zatim nebyl odsouhlasen ostatnimi procesy.

        HELD         // Vlastneny  - Procesu bylo prideleno pravo pristupovat ke sdilenemu zdroji.
    }

    private ClockedProcess owner;            // Proces, ktery vlastni aktualni instanci ExclusionPrimitive

    private String criticalSectionName;      // Nazev kriticke sekce. POZOR! V aplikaci se muze nachazet vice kritickych
                                             // sekci s ruznymi nazvy!

    private String[] allAccessingProcesses;  // Seznam vsech procesu, ktere mohou chtit pristupovat ke kriticke sekci
                                             // s nazvem 'criticalSectionName' (a tak spolurozhoduji o udelovani pristupu)

    private AcquisitionState state;          // Aktualni stav zamku (vzhledem k procesu 'owner').
                                             // state==HELD znamena, ze proces 'owner' muze vstoupit do kriticke sekce

    // Doplnte pripadne vlastni datove struktury potrebne pro implementaci
    // Ricart-Agrawalova algoritmu pro vzajemne vylouceni
    
    private String[] waitingProcesses;
    private String[] processesIAmWaitingFrom;
    private int processesCount;
    private int waitingProcessesCount = 0;
    private int timeOfRequest = 0;

    public ExclusionPrimitive(ClockedProcess owner, String criticalSectionName, String[] allProcesses) {
        this.owner = owner;
        this.criticalSectionName = criticalSectionName;
        this.allAccessingProcesses = allProcesses;
        this.processesCount = allProcesses.length;
        this.waitingProcesses = new String[processesCount];
        this.processesIAmWaitingFrom = new String[processesCount];

        // Na zacatku je zamek uvolneny
        this.state = AcquisitionState.RELEASED;
    }

    /**
     * Metoda pro zpracovani nove prichozi zpravy
     *
     * @param m    prichozi zprava
     * @return 'true', pokud je zprava 'm' relevantni pro aktualni kritickou sekci.
     */
    public boolean accept(ClockedMessage m) {
        // Implementujte zpracovani prijimane zpravy informujici
        // o pristupech ke sdilenemu zdroji podle Ricart-Agrawalova
        // algoritmu. Pokud potrebujete posilat specificke zpravy,
        // vytvorte si pro ne vlastni tridy.
        //
        // POZOR! Ne vsechny zpravy, ktere v teto metode dostanete Vas
        // budou zajimat! Budou Vam prichazet i zpravy, ktere se  napriklad
        // tykaji jinych kritickych sekci. Pokud je zprava nerelevantni, tak
        // ji nezpracovavejte a vratte navratovou hodnotu 'false'. Nekdo jiny
        // se o ni urcite postara :-)
        //
        // Nezapomente se starat o cas procesu 'owner'
        // pomoci metody owner.increaseTime(). Aktualizaci
        // logickeho casu procesu s prijatou zpravou (pomoci maxima) jsme
        // za Vas jiz vyresili.
        //
        // Cas poslani prijate zpravy muzete zjistit dotazem na hodnotu
        // m.sentOn. Aktualni logicky cas muzete zjistit metodou owner.getTime().
        // Zpravy se posilaji pomoci owner.send(prijemce, zprava) a je jim auto-
        // maticky pridelen logicky cas odeslani. Retezec identifikujici proces
        // 'owner' je ulozeny v owner.id.
        
        //REQUEST received

        if (m instanceof RequestMessage) {
            //first check if this message is relevant for this CS
            String receivedName = ((RequestMessage) m).name;
            if (!this.getName().equals(receivedName)) {
                return false;
            }

            //if we are in released state, just send OK to sender of m
            if (this.state == AcquisitionState.RELEASED) {
                owner.increaseTime();
                AckMessage msg = new AckMessage(this.getName());
                owner.send(m.sender, msg);
            }

            //if we are in wanted state
            else if (this.state == AcquisitionState.WANTED) {
                //check if the sender's logical clock is earlier
                int senderTime = m.sentOn;
                if (senderTime < timeOfRequest) {
                    owner.increaseTime();
                    AckMessage msg = new AckMessage(this.getName());
                    owner.send(m.sender, msg);
                }

                //check if it is the same but he is first in the lexicographic order
                else if (senderTime == timeOfRequest && m.sender.compareTo(owner.id) < 0) {
                    owner.increaseTime();
                    AckMessage msg = new AckMessage(this.getName());
                    owner.send(m.sender, msg);
                }

                //if neither of those are true, we add the sender to the waiting list
                else {
                    waitingProcesses[waitingProcessesCount] = m.sender;
                    waitingProcessesCount++; 
                }
            }

            //if we are in held state
            else {
                //add the process to the waiting list and increase their count
                waitingProcesses[waitingProcessesCount] = m.sender;
                waitingProcessesCount++;
            }
        
        } 
        
        //OK received
        else if (m instanceof AckMessage) {
            //first check if this message is relevant for this CS
            String receivedName = ((AckMessage) m).name;
            if (!this.getName().equals(receivedName)) {
                return false;
            }

            //we remove the process that we got OK from from the list
            boolean canEnter = true;
            for (int i = 0; i < processesCount; ++i) {
                if (processesIAmWaitingFrom[i] == null) {
                    continue;
                }

                if (processesIAmWaitingFrom[i].equals(m.sender)) {
                    processesIAmWaitingFrom[i] = null;
                }

                if (processesIAmWaitingFrom[i] != null) {
                    canEnter = false;
                }
            }

            //if there are no processes left, we enter CS
            if (canEnter) {
                this.state = AcquisitionState.HELD;
            }

        } 

        //anything else received
        else {
            return false;
        }

        return true;
    }

    public void requestEnter() {
        // Implementujte zadost procesu 'owner' o pristup
        // ke sdilenemu zdroji 'criticalSectionName'

        this.state = AcquisitionState.WANTED;

        //increase time
        owner.increaseTime();
        timeOfRequest = owner.getTime();

        for (int i = 0; i < processesCount; ++i) {
            //form message of type Request with the name of CS
            String process = allAccessingProcesses[i];
            if (!process.equals(owner.id)) {
                RequestMessage msg = new RequestMessage(this.getName());
                owner.send(process, msg);

                //we add the process to processes that I need to receive OK from
                processesIAmWaitingFrom[i] = process;
            }
        }
    }

    public void exit() {
        // Implementuje uvolneni zdroje, aby k nemu meli pristup i
        // ostatni procesy z 'allAccessingProcesses', ktere ke zdroji
        // mohou chtit pristupovat

        this.state = AcquisitionState.RELEASED;

        //increase time
        owner.increaseTime();

        for (int i = 0; i < waitingProcessesCount; ++i) {
            //form message of type OK with the name of CS
            AckMessage msg = new AckMessage(this.getName());
            owner.send(waitingProcesses[i], msg);

            //empty this position in list of waiting processes
            waitingProcesses[i] = null;
        }

        waitingProcessesCount = 0;
    }

    public String getName() {
        return criticalSectionName;
    }

    public boolean isHeld() {
        return state == AcquisitionState.HELD;
    }

}
