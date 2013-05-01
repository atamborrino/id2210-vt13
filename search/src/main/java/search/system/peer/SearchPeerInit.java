package search.system.peer;

import se.sics.kompics.Init;
import se.sics.kompics.p2p.bootstrap.BootstrapConfiguration;

import common.configuration.CyclonConfiguration;
import common.configuration.SearchConfiguration;
import common.configuration.TManConfiguration;
import common.peer.PeerAddress;

public final class SearchPeerInit extends Init {

	private final PeerAddress peerSelf;
	private final int num;
	private final BootstrapConfiguration bootstrapConfiguration;
	private final CyclonConfiguration cyclonConfiguration;
	private final SearchConfiguration applicationConfiguration;
	private final TManConfiguration tmanConfiguration;

//-------------------------------------------------------------------	
	public SearchPeerInit(PeerAddress peerSelf, int num,
			BootstrapConfiguration bootstrapConfiguration,
			CyclonConfiguration cyclonConfiguration,
			TManConfiguration tmanConfiguration,
			SearchConfiguration applicationConfiguration) {
		super();
		this.peerSelf = peerSelf;
		this.num = num;
		this.bootstrapConfiguration = bootstrapConfiguration;
		this.cyclonConfiguration = cyclonConfiguration;
		this.tmanConfiguration = tmanConfiguration;
		this.applicationConfiguration = applicationConfiguration;
	}

	public TManConfiguration getTmanConfiguration() {
		return tmanConfiguration;
	}

	// -------------------------------------------------------------------
	public PeerAddress getPeerSelf() {
		return this.peerSelf;
	}

//-------------------------------------------------------------------	
	public int getNum() {
		return this.num;
	}

//-------------------------------------------------------------------	
	public BootstrapConfiguration getBootstrapConfiguration() {
		return this.bootstrapConfiguration;
	}

//-------------------------------------------------------------------	
	public CyclonConfiguration getCyclonConfiguration() {
		return this.cyclonConfiguration;
	}

//-------------------------------------------------------------------	
	public SearchConfiguration getApplicationConfiguration() {
		return this.applicationConfiguration;
	}

}