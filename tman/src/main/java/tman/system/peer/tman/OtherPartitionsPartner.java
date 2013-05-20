package tman.system.peer.tman;

import se.sics.kompics.Event;

import common.peer.PeerAddress;

public class OtherPartitionsPartner extends Event {
	private PeerAddress partner;

	public OtherPartitionsPartner(PeerAddress partner) {
		this.partner = partner;
	}

	public PeerAddress getPartner() {
		return partner;
	}


}
