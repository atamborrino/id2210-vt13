package tman.system.peer.tman;

import java.util.ArrayList;
import java.util.List;

import se.sics.kompics.Event;

import common.peer.PeerAddress;


public class TManSample extends Event {
	List<PeerAddress> partners = new ArrayList<PeerAddress>();

//-------------------------------------------------------------------
	public TManSample(List<PeerAddress> partners) {
		this.partners = partners;
	}
        
	public TManSample() {
	}

//-------------------------------------------------------------------
	public List<PeerAddress> getSample() {
		return this.partners;
	}
}
