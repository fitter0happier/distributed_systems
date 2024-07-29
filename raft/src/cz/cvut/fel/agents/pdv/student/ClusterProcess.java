package cz.cvut.fel.agents.pdv.student;

import cz.cvut.fel.agents.pdv.dsand.Message;
import cz.cvut.fel.agents.pdv.dsand.Pair;
import cz.cvut.fel.agents.pdv.evaluation.StoreOperationEnums;
import cz.cvut.fel.agents.pdv.raft.RaftProcess;
import cz.cvut.fel.agents.pdv.raft.messages.ClientRequestWhoIsLeader;
import cz.cvut.fel.agents.pdv.raft.messages.ClientRequestWithContent;
import cz.cvut.fel.agents.pdv.raft.messages.ServerResponseConfirm;
import cz.cvut.fel.agents.pdv.raft.messages.ServerResponseLeader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.function.BiConsumer;

/**
 * Vasim ukolem bude naimplementovat (pravdepodobne nejenom) tuto tridu. Procesy v clusteru pracuji
 * s logy, kde kazdy zanam ma podobu mapy - kazdy zaznam v logu by mel reprezentovat stav
 * distribuovane databaze v danem okamziku.
 *
 * Vasi implementaci budeme testovat v ruznych scenarich (viz evaluation.RaftRun a oficialni
 * zadani). Nasim cilem je, abyste zvladli implementovat jednoduchou distribuovanou key/value
 * databazi s garancemi podle RAFT.
 */
public class ClusterProcess extends RaftProcess<Map<String, String>> {

	// ostatni procesy v clusteru
	private final List<String> otherProcessesInCluster;
	// maximalni spozdeni v siti
	private final int networkDelays;

	// copy of database
	private Map<String, String> database;

	// persistent state variables
	private int currentTerm = 0;
	private String votedFor = null;
	private String leaderId = null;

	// command log, each entry of type term : (request : (key : value))
	private List<Pair<Integer, Pair<String, Pair<String, String>>>> log;

	// volatile state variables 

	// index of highest log entry known to be committed
	private int commitIndex = 0;
	// index of highest log entry applied to state machine
	private int lastApplied = 0;

	// volatile leader state variables

	// for each other process, the index of next log entry to send to them
	private int[] nextIndex;
	// for each other process, the last index in the log where it matches leader's log
	private int[] matchIndex;
	// list of client requests that still await confirmation
	private List<Pair<String, String>> requestsToConfirm;

	// election timeout that will be randomly chosen at each election
	private int electionTimeout;
	// current election timeout, if == electionTimeout we initiate the new election
	private int currentElectionTimeout = 0;
	// current amount of voters
	private int votersCount = 0;

	private Random random;

	// enum to store types of statuses in which process can be during RAFT
	private enum Status {
		LEADER,
		FOLLOWER,
		CANDIDATE
	};

	// current status of process
	private Status currentStatus;

	public ClusterProcess(String id, Queue<Message> inbox, BiConsumer<String, Message> outbox,
		List<String> otherProcessesInCluster, int networkDelays) {
		super(id, inbox, outbox);
		this.otherProcessesInCluster = otherProcessesInCluster;
		this.networkDelays = networkDelays;

		this.database = new HashMap<String,String>();

		this.random = new Random();
		this.electionTimeout = networkDelays + random.nextInt(15);
		this.currentStatus = Status.FOLLOWER;

		this.log = new ArrayList<Pair<Integer, Pair<String, Pair<String, String>>>>();

		//empty pair as first match
		log.add(new Pair<Integer,Pair<String,Pair<String,String>>>(0, null));

		this.requestsToConfirm = new ArrayList<>();

		this.nextIndex = new int[otherProcessesInCluster.size()];
		this.matchIndex = new int[otherProcessesInCluster.size()];
	}

	@Override
	public Optional<Map<String, String>> getLastSnapshotOfLog() {

		// komentar viz deklarace
		if (database == null) {
			return Optional.empty();
		}

		return Optional.of(database);
	}

	@Override
	public String getCurrentLeader() {

		// komentar viz deklarace
		return leaderId;
	}

	@Override
	public void act() {
		// doimplementuje metodu act() podle RAFT

		// krome vlastnich zprav (vasich trid), dostavate typy zprav z balicku raft.messages s rodicem
		// ClientRequest, tak si je projdete, at vite, co je ucelem a obsahem jednotlivych typu.
		// V pripade ClientRequestWithContent dostavate zpravu typu
		// ClientRequestWithContent<StoreOperationEnums, Pair<String, String>>, kde StoreOperationEnums
		// je operace, kterou mate udelat s obsahem paru Pair<String, String>, kde prvni hodnota
		// paru je klic (nikdy neni prazdny) a druha je hodnota (v pripade pozadavku GET je prazdny)

		// dejte si pozor na jednotlive akce podle RAFT. S klientem komunikujte vyhradne pomoci zprav
		// typu ServerResponse v messages

		// na pozadavky klientu odpovidate zpravami typu ServerResponse viz popis podtypu v messages.
		// !!! V TOMTO PRIPADE JE 'requestId' ROVNO POZADAVKU KLIENTA, NA KTERY ODPOVIDATE !!!

		// dalsi podrobnosti naleznete na strance se zadanim

		// read inbox until it is not empty

		while (!inbox.isEmpty()) {
			// get the last message
			Message response = inbox.poll();
			

			if (response instanceof AppendEntries) {
				switch (currentStatus) {
					case LEADER:
						reactToAppendAsLeader(response);
						break;

					case FOLLOWER:
						reactToAppendAsFollower(response);
						break;

					case CANDIDATE:
						reactToAppendAsCandidate(response);
						break;	
				}
			}


			else if (response instanceof AppendConfirmation) {
				// react to confirmations only if in leader state
				if (currentStatus == Status.LEADER) {
					handleConfirmation(response);
				}
			}

			
			else if (response instanceof RequestVote) {
				switch (currentStatus) {
					case LEADER:
						reactToRequestAsLeader(response);
						break;

					case FOLLOWER:
						reactToRequestAsFollower(response);
						break;

					case CANDIDATE:
						reactToRequestAsCandidate(response);
						break;	
				}
			}

			
			else if (response instanceof Vote) {
				// react to votes only if in candidate state
				if (currentStatus == Status.CANDIDATE) {
					handleVote(response);
				}
			}

			
			else if (response instanceof ClientRequestWhoIsLeader) {
				// get request id
				String requestId = ((ClientRequestWhoIsLeader) response).getRequestId();

				// send the message with leader id
				send(response.sender, new ServerResponseLeader(requestId, leaderId));
			}


			else if (response instanceof ClientRequestWithContent) {
				// we need to handle request differently depending on our current role
				switch(currentStatus) {
					case LEADER:
						handleClientRequest(response);
						break;
					
					default:
						// get request id
						String requestId = ((ClientRequestWithContent<StoreOperationEnums, Pair<String, String>>) response).getRequestId();

						// send the message with leader id
						send(response.sender, new ServerResponseLeader(requestId, leaderId));
						break;
				}
			}

		}

		// increase timer only if not currently leader 
		if (currentStatus != Status.LEADER) {
			// increase current election timeout
			currentElectionTimeout += 1;

			// if we haven't received RPCs for long enough
			if (currentElectionTimeout == electionTimeout) {
				initElection();
			}

		} else {
			// send AppendEntries to all other processes 
			for (String process : otherProcessesInCluster) {
				// get index of process 
				int processIndex = otherProcessesInCluster.indexOf(process);
	
				// if process log is not full
				if (log.size() - 1 >= nextIndex[processIndex]) {
					// form AppendEntries RPC

					// entry to send to process
					Pair<Integer, Pair<String, Pair<String, String>>> entry = log.get(nextIndex[processIndex]);

					// previous log index
					int prevLogIndex = nextIndex[processIndex] - 1;

					// previous log term
					int prevLogTerm =  (log.get(nextIndex[processIndex] - 1)).getFirst();
				
					send(process, new AppendEntries(currentTerm, prevLogIndex, prevLogTerm, entry, commitIndex));
				} 

				// if process log is full, send empty AppendEntries as heartbeat
				else {
					send(process, new AppendEntries(currentTerm, null, commitIndex));
				}
			}

			// check if there are any entries that can be safely commited  
			
			// create copy of array of matchIndex
			int[] matchCopy = matchIndex.clone();

			// sort array
			Arrays.sort(matchCopy);
			
			// find N such that majority of matchIndex[i] ≥ N
			int N;
			
			if (matchCopy.length % 2 == 1) {
				N = matchCopy[matchCopy.length / 2];

			} else {
				N = matchCopy[matchCopy.length / 2 - 1];
			}

			if (N > commitIndex && log.get(N).getFirst() == currentTerm) {
				commitIndex = N;
			}
		}

		// check if we need to apply commands to state machine
		while (lastApplied < commitIndex) {
			// increment lastApplied
			lastApplied += 1;

			// get entry from log
			Pair<Integer, Pair<String, Pair<String,String>>> logEntry = log.get(lastApplied);

			// get request to respond to
			String requestId = logEntry.getSecond().getFirst();

			// get entry to be appended from log
			Pair<String, String> databaseEntry = logEntry.getSecond().getSecond();

			// perform operation on database
			String key = databaseEntry.getFirst();
			if (database.get(key) != null) {
				String oldVal = database.get(key);
				database.put(key, oldVal + databaseEntry.getSecond());

			} else {
				database.put(key, databaseEntry.getSecond());
			}

			if (currentStatus == Status.LEADER) {
				// respond to request
				int i = 0;
				while (!requestsToConfirm.get(i).getSecond().equals(requestId) && i != requestsToConfirm.size()) {
					++i;
				}

				if (i != requestsToConfirm.size()) {
					send(requestsToConfirm.get(i).getFirst(), new ServerResponseConfirm(requestId));
				}
			}

		}
	}

	private void reactToAppendAsFollower(Message response) {
		// first typecast response and declare it as variable
		AppendEntries request = ((AppendEntries) response);
		
		// compare received term with current term
		if (currentTerm > request.getTerm()) {
			send(request.sender, new AppendConfirmation(currentTerm, false));

		} else {
			updateLog(request);
		}
	}

	private void reactToAppendAsLeader(Message response) {
		// first typecast response and declare it as variable
		AppendEntries request = ((AppendEntries) response);

		// compare received term with current term
		if (currentTerm > request.getTerm()) {
			send(request.sender, new AppendConfirmation(currentTerm, false));

		} else {
			currentStatus = Status.FOLLOWER;
			updateLog(request);
		}

	}

	private void reactToAppendAsCandidate(Message response) {
		// first typecast response and declare it as variable
		AppendEntries request = ((AppendEntries) response);

		// compare received term with current term
		if (currentTerm > request.getTerm()) {
			send(request.sender, new AppendConfirmation(currentTerm, false));

		} else {
			currentStatus = Status.FOLLOWER;
			updateLog(request);
		}
	}

	private void reactToRequestAsFollower(Message response) {
		// first typecast response and declare it as variable
		RequestVote request = ((RequestVote) response);

		// my vote, false by default
		boolean voting = false;

		// only consider voting if the term of request is bigger
		if (currentTerm <= request.getTerm()) {
			// there are only two cases when we consider a candidate
			if (votedFor == null || votedFor.equals(request.sender)) {
				if (log.get(log.size() - 1).getFirst() <= request.lastLogTerm() && log.size() - 1 <= request.lastLogIndex()) {
					// set term to the turn of request
					currentTerm = request.getTerm();
					voting = true;

				} else if (votedFor == null || votedFor.equals(request.sender)) {
					// if we no longer want the previous candidate to win
					votedFor = null;
				}
			}
		}

		// here we form a message for candidate
		if (voting) {
			votedFor = request.sender;
		}

		Vote ballot = new Vote(voting, currentTerm);
		send(request.sender, ballot);
	}

	private void reactToRequestAsLeader(Message response) {
		// first typecast response and declare it as variable
		RequestVote request = ((RequestVote) response);

		// deny the request, since we are already a leader
		send(request.sender, new Vote(false, currentTerm));
	}

	private void reactToRequestAsCandidate(Message response) {
		// first typecast response and declare it as variable
		RequestVote request = ((RequestVote) response);
		
		// deny the request, since our votedFor is not null
		send(request.sender, new Vote(false, currentTerm));
	}

	private void initElection() {
		// change status to candidate
		currentStatus = Status.CANDIDATE;

		// increment current term
		currentTerm += 1;
		
		// randomize new election timeout
		electionTimeout = random.nextInt(15);

		// reset current election timeout
		currentElectionTimeout = 0;

		// vote for yourself
		votedFor = getId();
		votersCount += 1;

		// send vote request to each other process in the cluster
		for (String process : otherProcessesInCluster) {
			send(process, new RequestVote(currentTerm, log.size() - 1, log.get(log.size() - 1).getFirst()));
		}
	}

	private void handleVote(Message message) {
		// first typecast response and declare it as variable
		Vote ballot = ((Vote) message);
		
		if (ballot.getResult() == false) {
			if (currentTerm < ballot.getTerm()) {
				// set current term equal to voter's term
				currentTerm = ballot.getTerm();

				// set status to follower
				currentStatus = Status.FOLLOWER;
			}

			return;
		}

		// increase count of voters
		votersCount += 1;

		// check if you won the election
		if (votersCount >= otherProcessesInCluster.size() / 2 + 1) {
			// change status to leader
			currentStatus = Status.LEADER;
			leaderId = getId();

			// set votedFor to null
			votedFor = null;

			// set election timeout to 0
			electionTimeout = 0;
			
			int i = 0;
			for (String process : otherProcessesInCluster) {
				// reinitialize nextIndex and matchIndex
				nextIndex[i] = log.size();
				matchIndex[i] = 0;
				++i;

				// send empty AppendEntries to all other processes
				send(process, new AppendEntries(currentTerm, null, commitIndex));
				
			}
		}
	}

	private void handleClientRequest(Message response) {
		// first typecast response and declare it as variable
		ClientRequestWithContent<StoreOperationEnums, Pair<String, String>> request 
			= ((ClientRequestWithContent<StoreOperationEnums, Pair<String, String>>) response);

		// get request id
		String requestId = request.getRequestId(); 

		// check if the request is not in a log already
		int i = 1;
		while (i != log.size() && !log.get(i).getSecond().getFirst().equals(requestId)) {
			++i;
		}

		if (i != log.size()) {
			return;
		}

		// get key-value pair from request
		Pair<String, String> entry = request.getContent();

		// store request and term in log
		log.add(new Pair<Integer, Pair<String, Pair<String,String>>>(currentTerm, new Pair<>(requestId, entry)));

		// add request to the list of requests to respond to later
		requestsToConfirm.add(new Pair<String, String>(request.sender, requestId));
	}

	private void handleConfirmation(Message response) {
		// first typecast response and declare it as variable
		AppendConfirmation confirmation = ((AppendConfirmation) response);

		// get term from message 
		int followerTerm = confirmation.getTerm();

		if (currentTerm < followerTerm) {
			// if the term of confirmation is bigger, convert to follower
			currentStatus = Status.FOLLOWER;
			
			// set currentTerm = followerTerm
			currentTerm = followerTerm;

		} else {
			// get index of process 
			int processIndex = otherProcessesInCluster.indexOf(confirmation.sender);

			if (confirmation.getResult() == true) {
				// update matchIndex
				matchIndex[processIndex] = confirmation.getMatchedOn();

				// increment nextIndex 
				nextIndex[processIndex] = matchIndex[processIndex] + 1;
				
			} else {
				// decrement nextIndex
				nextIndex[processIndex] -= 1;
			}
		}
	}

	private void updateLog(AppendEntries request) {
		// set parameters 
		electionTimeout = 0;
		leaderId = request.sender;
		currentTerm = request.getTerm();
		votedFor = null;

		// check if RPC is a heartbeat
		if (request.getNewEntry() != null) {
			if (log.get(request.getPrevLogIndex()).getFirst() != request.getPrevLogTerm()) {
				// reply false if log doesn’t contain an entry at prevLogIndex whose term matches prevLogTerm
				send(request.sender, new AppendConfirmation(currentTerm, false));


			} else if (request.getPrevLogIndex() + 1 == log.size() || 
				log.get(request.getPrevLogIndex() + 1).getFirst() != request.getNewEntry().getFirst()) {
				// if an existing entry conflicts with a new one (same index but different terms), 
				// delete the existing entry and all that follow it 
				log.subList(request.getPrevLogIndex() + 1, log.size()).clear();
				log.add(request.getNewEntry());

			} else {
				log.add(request.getNewEntry());
			}

			send(request.sender, new AppendConfirmation(currentTerm, true, request.getPrevLogIndex() + 1));
		}

		if (request.getLeaderCommit() > commitIndex) {
			commitIndex = Math.min(request.getLeaderCommit(), log.size() - 1);
		}
	}
}
