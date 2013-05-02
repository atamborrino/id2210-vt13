package search.system.peer.search;

import java.util.Set;

import common.peer.PeerAddress;
import common.peer.PeerMessage;

public class IndexPullRequest extends PeerMessage {

	private static final long serialVersionUID = -6562014825769834828L;
	private Set<Integer> missing;
	private int lastIndex;

	public IndexPullRequest(PeerAddress source, PeerAddress destination, Set<Integer> missing, int lastIndex) {
		super(source, destination);
		this.missing = missing;
		this.lastIndex = lastIndex;
	}

	public Set<Integer> getMissing() {
		return missing;
	}

	public int getLastIndex() {
		return lastIndex;
	}

}
