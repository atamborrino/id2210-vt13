package search.system.peer.search;

import common.peer.PeerAddress;
import common.peer.PeerMessage;

public class AddOrder extends PeerMessage {

	private int entryId;
	private String text;

	public int getEntryId() {
		return entryId;
	}

	public void setEntryId(int entryId) {
		this.entryId = entryId;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public AddOrder(PeerAddress source, PeerAddress destination, String text, int entryId) {
		super(source, destination);
		this.text = text;
		this.entryId = entryId;
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 6949880337237568191L;

}
