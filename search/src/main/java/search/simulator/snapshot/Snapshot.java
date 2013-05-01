package search.simulator.snapshot;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import common.peer.PeerAddress;

public class Snapshot {
	private static Map<PeerAddress, PeerInfo> peers = new ConcurrentHashMap<PeerAddress, PeerInfo>();
	private static int counter = 0;
	private static String FILENAME = "search.out";

//-------------------------------------------------------------------
	public static void init(int numOfStripes) {
		FileIO.write("", FILENAME);
	}

//-------------------------------------------------------------------
	public static void addPeer(PeerAddress address) {
		peers.put(address, new PeerInfo());
	}

//-------------------------------------------------------------------
	public static void removePeer(PeerAddress address) {
		peers.remove(address);
	}

//-------------------------------------------------------------------
	public static void updateNum(PeerAddress address, double num) {
		PeerInfo peerInfo = peers.get(address);
		
		if (peerInfo == null)
			return;
		
		peerInfo.updateNum(num);
	}
	
//-------------------------------------------------------------------
	public static void updateCyclonPartners(PeerAddress address, ArrayList<PeerAddress> partners) {
		PeerInfo peerInfo = peers.get(address);
		
		if (peerInfo == null)
			return;
		
		peerInfo.updateCyclonPartners(partners);
	}

	public static void updateRandSelectedPeer(PeerAddress address, PeerAddress partner) {
		PeerInfo peerInfo = peers.get(address);

		if (peerInfo == null)
			return;

		peerInfo.updateRandSelectedPeer(partner);
	}

	public static void updateEntries(PeerAddress address, Integer entry) {
		PeerInfo peerInfo = peers.get(address);

		if (peerInfo == null)
			return;

		peerInfo.updateEntry(entry);
	}

//-------------------------------------------------------------------
	public static void report() {
		// String str = new String();
		// str += "current time: " + counter++ + "\n";
		// str += reportNetworkState();
		// str += reportDetails();
		// str += "###\n";
		//
		// System.out.println(str);
		// FileIO.append(str, FILENAME);
	}

//-------------------------------------------------------------------
	private static String reportNetworkState() {
		String str = new String("---\n");
		int totalNumOfPeers = peers.size();
		str += "total number of peers: " + totalNumOfPeers + "\n";

		return str;		
	}
	
//-------------------------------------------------------------------
	private static String reportDetails() {
		StringBuilder str = new StringBuilder("---\n");
		for (Map.Entry<PeerAddress, PeerInfo> entry : peers.entrySet()) {
			PeerAddress peer = entry.getKey();
			PeerInfo info = entry.getValue();
			if (info != null) {
				str.append("Peer " + peer.getPeerAddress().getId() + ": rand neighbour: ");
				if (info.getRandSelectedPeer() != null) {
					str.append(info.getRandSelectedPeer().getPeerAddress().getId());
				} else {
					str.append("null");
				}
				str.append("; entries:");
				for (Integer indexentry : info.getEntries()) {
					str.append(" " + indexentry);
				}
				str.append("\n");
			}
		}

		return str.toString();
	}


}
