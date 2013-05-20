package search.system.peer.search;

import common.peer.PeerAddress;
import common.peer.PeerMessage;

public class LookupRequest extends PeerMessage {

	private static final long serialVersionUID = 8099673545603236811L;

	private String querystr;
	private int reqId;

	public LookupRequest(PeerAddress source, PeerAddress destination, int reqId, String querystr) {
		super(source, destination);
		this.querystr = querystr;
		this.reqId = reqId;
	}

	public String getQuerystr() {
		return querystr;
	}

	public int getReqId() {
		return reqId;
	}

}
