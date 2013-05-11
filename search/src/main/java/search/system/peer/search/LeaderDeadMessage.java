package search.system.peer.search;

import common.peer.PeerAddress;
import common.peer.PeerMessage;

public class LeaderDeadMessage extends PeerMessage {

	private static final long serialVersionUID = -7496257558073217324L;

	public LeaderDeadMessage(PeerAddress source, PeerAddress destination) {
		super(source, destination);
	}

}
