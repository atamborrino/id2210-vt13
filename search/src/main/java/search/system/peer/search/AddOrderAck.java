package search.system.peer.search;

import common.peer.PeerAddress;
import common.peer.PeerMessage;

public class AddOrderAck extends PeerMessage {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4848500791043096845L;
	private int entryId;

	public int getEntryId() {
		return entryId;
	}

	public void setEntryId(int entryId) {
		this.entryId = entryId;
	}

	public AddOrderAck(PeerAddress source, PeerAddress destination, int entryId) {
		super(source, destination);
		this.entryId = entryId;
	}

}
