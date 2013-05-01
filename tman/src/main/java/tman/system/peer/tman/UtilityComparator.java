package tman.system.peer.tman;

import java.util.Comparator;

import common.peer.PeerAddress;

public class UtilityComparator implements Comparator<PeerAddress> {
	private PeerAddress self;
	private int utility;

	public UtilityComparator(PeerAddress self) {
		this.self = self;
		utility = peerUtility(self);
	}

	@Override
	public int compare(PeerAddress p1, PeerAddress p2) {
		int u1 = peerUtility(p1);
		int u2 = peerUtility(p2);
		if ((u1 > utility && u2 < utility) || (Math.abs(u1 - utility) < Math.abs(u2 - utility))) {
			return 1;
		} else {
			return -1;
		}
	}

	public int peerUtility(PeerAddress peer) {
		return (-1) * peer.getPeerAddress().getId();
	}
}
