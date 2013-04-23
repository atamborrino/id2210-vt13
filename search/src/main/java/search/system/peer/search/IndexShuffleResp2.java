package search.system.peer.search;

import java.util.Set;

import org.apache.lucene.document.Document;

import common.peer.PeerAddress;
import common.peer.PeerMessage;

public class IndexShuffleResp2 extends PeerMessage {
	private Set<Document> sentEntries;

	public IndexShuffleResp2(PeerAddress source, PeerAddress destination, Set<Document> entries) {
		super(source, destination);
		this.sentEntries = entries;
	}

	public Set<Document> getSentEntries() {
		return sentEntries;
	}

}
