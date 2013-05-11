package search.system.peer.search;

import common.peer.PeerAddress;
import common.peer.PeerMessage;

public class Heartbeat extends PeerMessage {

	public Heartbeat(PeerAddress source, PeerAddress destination) {
		super(source, destination);

	}

	private static final long serialVersionUID = -8887256365858781941L;

}
