package search.system.peer.search;

import java.util.Set;

import common.peer.PeerAddress;
import common.peer.PeerMessage;

public class IndexShuffleRequest extends PeerMessage {
	private int lastIndex;
	private Set<Integer> missing;

	public IndexShuffleRequest(PeerAddress source, PeerAddress destination,
			Set<Integer> missing, int lastIndex) {
		super(source, destination);
		this.lastIndex = lastIndex;
		this.missing = missing;
	}

	public int getLastIndex() {
		return lastIndex;
	}

	public Set<Integer> getMissing() {
		return missing;
	}

}
