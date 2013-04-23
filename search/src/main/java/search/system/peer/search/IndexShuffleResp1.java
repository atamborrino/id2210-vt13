package search.system.peer.search;

import java.util.Set;

import org.apache.lucene.document.Document;

import common.peer.PeerAddress;
import common.peer.PeerMessage;

public class IndexShuffleResp1 extends PeerMessage {
	private int lastIndex;
	private Set<Document> sentEntries;
	private Set<Integer> wantedEntries;

	public IndexShuffleResp1(PeerAddress source, PeerAddress destination,
			Set<Document> entries, Set<Integer> wantedEntries, int lastIndex) {
		super(source, destination);
		this.lastIndex = lastIndex;
		this.sentEntries = entries;
		this.wantedEntries = wantedEntries;
	}

	public int getLastIndex() {
		return lastIndex;
	}

	public Set<Document> getSentEntries() {
		return sentEntries;
	}

	public Set<Integer> getWantedEntries() {
		return wantedEntries;
	}


}
