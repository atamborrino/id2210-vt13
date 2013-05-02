package search.system.peer.search;

import java.util.Set;

import org.apache.lucene.document.Document;

import common.peer.PeerAddress;
import common.peer.PeerMessage;

public class IndexPullResponse extends PeerMessage {

	private static final long serialVersionUID = 1505996416089255388L;
	private Set<Document> entries;


	public IndexPullResponse(PeerAddress source, PeerAddress destination, Set<Document> entries) {
		super(source, destination);
		this.entries = entries;
	}


	public Set<Document> getEntries() {
		return entries;
	}

}
