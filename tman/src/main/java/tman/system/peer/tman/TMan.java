package tman.system.peer.tman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import tman.simulator.snapshot.Snapshot;

import common.configuration.TManConfiguration;
import common.peer.PeerAddress;

import cyclon.system.peer.cyclon.CyclonSample;
import cyclon.system.peer.cyclon.CyclonSamplePort;

public final class TMan extends ComponentDefinition {

	private static final Logger logger = LoggerFactory.getLogger(TMan.class);
	Negative<TManSamplePort> tmanPartnersPort = negative(TManSamplePort.class);
	Positive<CyclonSamplePort> cyclonSamplePort = positive(CyclonSamplePort.class);
	Positive<Network> networkPort = positive(Network.class);
	Positive<Timer> timerPort = positive(Timer.class);
	private long period;
	private PeerAddress self;
	private int utility;
	private List<PeerAddress> tmanPartners = new ArrayList<PeerAddress>();
	private List<PeerAddress> cyclonPartners = new ArrayList<PeerAddress>();
	private TManConfiguration tmanConfiguration;
	private final int tmanPartnersSize = 5;
	private UtilityComparator uComparator;

	public class TManSchedule extends Timeout {

		public TManSchedule(SchedulePeriodicTimeout request) {
			super(request);
		}

		// -------------------------------------------------------------------
		public TManSchedule(ScheduleTimeout request) {
			super(request);
		}
	}

	// -------------------------------------------------------------------
	public TMan() {
		tmanPartners = new ArrayList<PeerAddress>();

		subscribe(handleInit, control);
		subscribe(handleRound, timerPort);
		subscribe(handleCyclonSample, cyclonSamplePort);
		subscribe(handleTManPartnersResponse, networkPort);
		subscribe(handleTManPartnersRequest, networkPort);
	}

	// -------------------------------------------------------------------
	Handler<TManInit> handleInit = new Handler<TManInit>() {
		@Override
		public void handle(TManInit init) {

			self = init.getSelf();
			uComparator = new UtilityComparator(self);
			tmanConfiguration = init.getConfiguration();
			period = tmanConfiguration.getPeriod();

			SchedulePeriodicTimeout rst = new SchedulePeriodicTimeout(period, period);
			rst.setTimeoutEvent(new TManSchedule(rst));
			trigger(rst, timerPort);

		}
	};
	// -------------------------------------------------------------------
	Handler<TManSchedule> handleRound = new Handler<TManSchedule>() {
		@Override
		public void handle(TManSchedule event) {
			Snapshot.updateTManPartners(self, tmanPartners);

			if (!tmanPartners.isEmpty()) {
				// With high probability, shuffle with preferred neighbor
				PeerAddress partner;
				partner = tmanPartners.get(0);
				if (tmanPartners.size() > 1) {
					Random rand = new Random();
					if (rand.nextFloat() > 0.8) { // Don't select the preferred
													// neighbor
						partner = tmanPartners.get(rand.nextInt(tmanPartners.size() - 1));
					}
				}

				List<PeerAddress> view = new ArrayList<PeerAddress>(tmanPartners);
				view.add(self);
				view.remove(partner);
				// Collections.sort(view, new UtilityComparator(partner));
				// Collections.reverse(view);
				trigger(new TManPartnersRequest(self, partner, view), networkPort);


				// Publish sample to connected components
				trigger(new TManSample(tmanPartners), tmanPartnersPort);
			}
		}
	};
	// -------------------------------------------------------------------
	Handler<CyclonSample> handleCyclonSample = new Handler<CyclonSample>() {
		@Override
		public void handle(CyclonSample event) {
			cyclonPartners = event.getSample();
			if (!cyclonPartners.isEmpty()) {

				for (PeerAddress partner : cyclonPartners) {
					// trace("partner: " + partner.getPeerAddress().getId());
					if (!tmanPartners.contains(partner)) {
						if (tmanPartners.size() < tmanPartnersSize) {
							tmanPartners.add(partner);
						} else {
							int last = tmanPartners.size() - 1;
							PeerAddress lessPreferred = tmanPartners.get(last);
							if (uComparator.compare(partner, lessPreferred) > 0) {
								tmanPartners.remove(last);
								tmanPartners.add(partner);
							}
						}

						Collections.sort(tmanPartners, uComparator);
						Collections.reverse(tmanPartners);
					}
				}

			} else {
				trace("empty cyclon sample");
			}

			StringBuilder sb = new StringBuilder();
			sb.append("tman partners: ");
			for (PeerAddress p : tmanPartners) {
				sb.append(p.getPeerAddress().getId());
				sb.append(" ");
			}
			trace(sb.toString());

		}
	};
	// -------------------------------------------------------------------

	Handler<TManPartnersRequest> handleTManPartnersRequest = new Handler<TManPartnersRequest>() {

		@Override
		public void handle(TManPartnersRequest event) {
			List<PeerAddress> viewSender = event.getView();
			mergeTmanPartners(viewSender);
			Collections.sort(tmanPartners, uComparator);

			StringBuilder sb = new StringBuilder();
			sb.append("handleTManPrequest: after sort: ");
			for (PeerAddress pp : tmanPartners) {
				sb.append(pp.getPeerAddress().getId());
				sb.append(" ");
			}
			// trace(sb.toString());

			while (tmanPartners.size() > tmanPartnersSize) {
				tmanPartners.remove(0);
			}
			Collections.reverse(tmanPartners);

			List<PeerAddress> view = new ArrayList<PeerAddress>(tmanPartners);
			view.add(self);
			view.remove(event.getPeerSource());

			// Collections.sort(view, new
			// UtilityComparator(event.getPeerSource()));
			// Collections.reverse(view);

			trigger(new TManPartnersResponse(self, event.getPeerSource(), view), networkPort);
		}

	};
	// Handler<ExchangeMsg.Request> handleTManPartnersRequest = new
	// Handler<ExchangeMsg.Request>() {
	// @Override
	// public void handle(ExchangeMsg.Request event) {
	//
	// }
	// };

	Handler<TManPartnersResponse> handleTManPartnersResponse = new Handler<TManPartnersResponse>() {

		@Override
		public void handle(TManPartnersResponse event) {
			List<PeerAddress> viewSender = event.getView();
			mergeTmanPartners(viewSender);
			Collections.sort(tmanPartners, uComparator);
			while (tmanPartners.size() > tmanPartnersSize) {
				tmanPartners.remove(0);
			}
			Collections.reverse(tmanPartners);
		}
	};

	// Handler<ExchangeMsg.Response> handleTManPartnersResponse = new
	// Handler<ExchangeMsg.Response>() {
	// @Override
	// public void handle(ExchangeMsg.Response event) {
	//
	// }
	// };

	private void mergeTmanPartners(List<PeerAddress> view) {
		for (PeerAddress peer : view) {
			if (!tmanPartners.contains(peer) && !peer.equals(self)) {
				tmanPartners.add(peer);
			}
		}

	}

	public void trace(String mess) {
		// String toWrite = "Node" + self.getPeerAddress().getId() + ". " +
		// mess;
		// logger.info(toWrite);
	}

}
