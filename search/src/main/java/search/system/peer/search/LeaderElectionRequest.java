package search.system.peer.search;

import common.peer.PeerAddress;
import common.peer.PeerMessage;

public class LeaderElectionRequest extends PeerMessage {

	private int electionId;

	public int getElectionId() {
		return electionId;
	}

	public void setElectionId(int electionId) {
		this.electionId = electionId;
	}

	public LeaderElectionRequest(PeerAddress source, PeerAddress destination, int electionId) {
		super(source, destination);
		this.electionId = electionId;
	}

	/**
	 *
	 */
	private static final long serialVersionUID = 1345599899555718428L;

}
