package tman.simulator.snapshot;

import java.util.ArrayList;
import java.util.List;

import common.peer.PeerAddress;


public class PeerInfo {
	private List<PeerAddress> tmanPartners;
	private List<PeerAddress> cyclonPartners;

//-------------------------------------------------------------------
	public PeerInfo() {
		this.tmanPartners = new ArrayList<PeerAddress>();
		this.cyclonPartners = new ArrayList<PeerAddress>();
	}

//-------------------------------------------------------------------
	public void updateTManPartners(List<PeerAddress> partners) {
		this.tmanPartners = partners;
	}

//-------------------------------------------------------------------
	public void updateCyclonPartners(List<PeerAddress> partners) {
		this.cyclonPartners = partners;
	}

//-------------------------------------------------------------------
	public List<PeerAddress> getTManPartners() {
		return this.tmanPartners;
	}

//-------------------------------------------------------------------
	public List<PeerAddress> getCyclonPartners() {
		return this.cyclonPartners;
	}
}
