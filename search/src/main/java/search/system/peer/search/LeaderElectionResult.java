package search.system.peer.search;

import java.util.List;

import common.peer.PeerAddress;
import common.peer.PeerMessage;

public class LeaderElectionResult extends PeerMessage {

	private List<PeerAddress> electionGroup;

	public List<PeerAddress> getElectionGroup() {
		return electionGroup;
	}

	public void setElectionGroup(List<PeerAddress> electionGroup) {
		this.electionGroup = electionGroup;
	}

	public LeaderElectionResult(PeerAddress source, PeerAddress destination, List<PeerAddress> electionGroup) {
		super(source, destination);
		this.electionGroup = electionGroup;
	}

	/**
	 *
	 */
	private static final long serialVersionUID = -2349026623058408144L;

}
