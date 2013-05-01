package tman.system.peer.tman;

import java.util.List;

import common.peer.PeerAddress;
import common.peer.PeerMessage;

public class TManPartnersResponse extends PeerMessage {
	private List<PeerAddress> view;

	public TManPartnersResponse(PeerAddress source, PeerAddress destination, List<PeerAddress> view) {
		super(source, destination);
		this.view = view;
	}

	public List<PeerAddress> getView() {
		return view;
	}

}
