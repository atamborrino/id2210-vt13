package search.system.peer.search;

import se.sics.kompics.web.WebRequest;

import common.peer.PeerAddress;
import common.peer.PeerMessage;

public class AddRequest extends PeerMessage {

	private final int reqId;
	private final String text;
	private final WebRequest webReq;
	private final int nbHops;

	public AddRequest(PeerAddress source, PeerAddress destination, int reqId, String text, WebRequest webReq, int nbHops) {
		super(source, destination);
		this.reqId = reqId;
		this.text = text;
		this.webReq = webReq;
		this.nbHops = nbHops;
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -8055001500660134019L;

	public int getReqId() {
		return reqId;
	}

	public String getText() {
		return text;
	}

	public WebRequest getWebReq() {
		return webReq;
	}

	public int getNbHops() {
		return nbHops;
	}

}
