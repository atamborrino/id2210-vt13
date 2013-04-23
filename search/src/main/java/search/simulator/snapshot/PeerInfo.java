package search.simulator.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import common.peer.PeerAddress;

public class PeerInfo {
	private double num;
	private List<PeerAddress> cyclonPartners = new ArrayList<PeerAddress>();
	private PeerAddress randPeer;
	private List<Integer> entries = new ArrayList<Integer>();

//-------------------------------------------------------------------
	public PeerInfo() {
		this.cyclonPartners = new ArrayList<PeerAddress>();
	}

//-------------------------------------------------------------------
	public void updateNum(double num) {
		this.num = num;
	}

//-------------------------------------------------------------------
	public void updateNum(int num) {
		this.num = num;
	}

//-------------------------------------------------------------------
	public void updateCyclonPartners(ArrayList<PeerAddress> partners) {
		this.cyclonPartners = partners;
	}

//-------------------------------------------------------------------
	public double getNum() {
		return this.num;
	}

//-------------------------------------------------------------------
	public List<PeerAddress> getCyclonPartners() {
		return this.cyclonPartners;
	}
	
	public void updateRandSelectedPeer(PeerAddress peer) {
		this.randPeer = peer;
	}

	public PeerAddress getRandSelectedPeer() {
		return this.randPeer;
	}

	public void updateEntry(int entry) {
		this.entries.add(entry);
		Collections.sort(this.entries);
	}

	public List<Integer> getEntries() {
		return this.entries;
	}
}
