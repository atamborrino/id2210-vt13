package search.system.peer.search;

import common.peer.PeerAddress;
import common.peer.PeerMessage;

public class LeaderElectionResponse extends PeerMessage {

	private boolean isLeader;
	private int electionId;

	public int getElectionId() {
		return electionId;
	}

	public void setElectionId(int electionId) {
		this.electionId = electionId;
	}

	public boolean isLeader() {
		return isLeader;
	}

	public void setLeader(boolean isLeader) {
		this.isLeader = isLeader;
	}

	public LeaderElectionResponse(PeerAddress source, PeerAddress destination, boolean isLeader, int electionId) {
		super(source, destination);
		this.isLeader = isLeader;
		this.electionId = electionId;
		// TODO Auto-generated constructor stub
	}

	/**
	 *
	 */
	private static final long serialVersionUID = 6742004879454451012L;

}
