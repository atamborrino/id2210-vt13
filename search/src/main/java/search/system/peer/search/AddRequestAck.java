package search.system.peer.search;

import common.peer.PeerAddress;
import common.peer.PeerMessage;

public class AddRequestAck extends PeerMessage {
	private final int reqId;
	private static final long serialVersionUID = -7018402931311830048L;

	public AddRequestAck(PeerAddress source, PeerAddress destination, int reqId) {
		super(source, destination);
		this.reqId = reqId;
	}

	public int getReqId() {
		return reqId;
	}

}
