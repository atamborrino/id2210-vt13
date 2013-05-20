package search.system.peer.search;

import java.util.List;

import org.apache.lucene.document.Document;

import common.peer.PeerAddress;
import common.peer.PeerMessage;

public class LookupResponse extends PeerMessage{
	private static final long serialVersionUID = 8299883694902761560L;

	private List<Document> results;
	private int reqId;
	private String querystr;

	public LookupResponse(PeerAddress source, PeerAddress destination, int reqId, String querystr,
			List<Document> results) {
		super(source, destination);
		this.results = results;
		this.reqId = reqId;
		this.querystr = querystr;
	}

	public List<Document> getResults() {
		return results;
	}

	public int getReqId() {
		return reqId;
	}

	public String getQuerystr() {
		return querystr;
	}

}
