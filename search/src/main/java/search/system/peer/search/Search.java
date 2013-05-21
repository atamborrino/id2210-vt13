package search.system.peer.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.mortbay.jetty.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timer;
import se.sics.kompics.web.Web;
import se.sics.kompics.web.WebRequest;
import se.sics.kompics.web.WebResponse;
import search.simulator.snapshot.Snapshot;
import search.system.peer.AddIndexText;
import search.system.peer.IndexPort;
import tman.system.peer.tman.OtherPartitionsPartner;
import tman.system.peer.tman.TManSample;
import tman.system.peer.tman.TManSamplePort;
import tman.system.peer.tman.UtilityComparator;

import common.configuration.SearchConfiguration;
import common.peer.PeerAddress;

import cyclon.system.peer.cyclon.CyclonSample;
import cyclon.system.peer.cyclon.CyclonSamplePort;

/**
 * Should have some comments here.
 * 
 * @author jdowling
 */
public final class Search extends ComponentDefinition {

	private static final Logger logger = LoggerFactory.getLogger(Search.class);
	Positive<IndexPort> indexPort = positive(IndexPort.class);
	Positive<Network> networkPort = positive(Network.class);
	Positive<Timer> timerPort = positive(Timer.class);
	Negative<Web> webPort = negative(Web.class);
	Positive<CyclonSamplePort> cyclonSamplePort = positive(CyclonSamplePort.class);
	Positive<TManSamplePort> tmanSamplePort = positive(TManSamplePort.class);

	ArrayList<PeerAddress> cyclonPartners = new ArrayList<PeerAddress>();
	Random randomGenerator = new Random();
	private PeerAddress self;
	private long period;
	private double num;
	private SearchConfiguration searchConfiguration;
	// Apache Lucene used for searching
	StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_42);
	Directory index = new RAMDirectory();
	IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_42, analyzer);

	// Indices
	private int lastIndex = 0;
	private Set<Integer> missingIndices = new HashSet<Integer>();
	private boolean ANTI_ENTROPY_ON = false;

	// TMan
	private List<PeerAddress> tmanPartners = Collections.synchronizedList(new ArrayList<PeerAddress>());
	private final int CONVERGENCE_TRHESHOLD = 10;
	private int currentConvergence = 0;
	private boolean gradientHasConverged = false;
	private boolean isLeader = false;
	private boolean onGoingElection = false;
	private int leaderElectionAcks = 0;
	private int electionId = 0;
	private PeerAddress leader = null;
	private List<PeerAddress> electionGroup;

	// Gradient
	protected static final long ADD_REQ_TIMEOUT = 10000;
	protected static final long HEARTBEAT_TIMEOUT = 23000;
	protected static final long SEND_HEARTBEAT_TIMEOUT = 10000;
	int addRequestId = 0;
	Map<Integer, AddRequest> mapAddReqs = new HashMap<Integer, AddRequest>(); // used by adder to resend in case of timeout
	int monotonicEntryId = 1;
	Map<Integer, Integer> mapEntryIdNbAcks = new HashMap<Integer, Integer>(); // used by master
	Map<Integer, AddRequest> mapEntryIdAddReqs = new HashMap<Integer, AddRequest>(); // used by master

	private UtilityComparator uComparator;

	// Failure
	private Map<PeerAddress, UUID> electionGroupHb = new HashMap<PeerAddress, UUID>();
	private UUID leaderHb;
	private UUID sendHBtimeout;
	private int leaderSuspicion = 0;
	private boolean newElectionGroup = false;

	// Partition
	private WebRequest lookupRequest;
	private int lookupReqId = 0;
	private final int otherPatnersNb = 3;
	private final int partitionNb = 10;
	private Map<Integer, List<PeerAddress>> otherPartners = new HashMap<Integer, List<PeerAddress>>();
	private Map<Integer, List<Document>> lookupResults = new HashMap<Integer, List<Document>>();

	// -------------------------------------------------------------------
	public Search() {

		subscribe(handleInit, control);
		subscribe(handleWebRequest, webPort);
		subscribe(handleCyclonSample, cyclonSamplePort);
		subscribe(handleTManSample, tmanSamplePort);
		subscribe(handleAddIndexText, indexPort);

		// anti-entropy
		subscribe(handleIndexShuffleRequest, networkPort);
		subscribe(handleIndexShuffleResp1Handler, networkPort);
		subscribe(handleIndexShuffleResp2Handler, networkPort);

		// pull-based
		subscribe(handleIndexPullRequest, networkPort);
		subscribe(handleIndexPullResponse, networkPort);
		subscribe(handleUpdateIndexTimeout, timerPort);

		// leader selection
		subscribe(handleLeaderElectionRequest, networkPort);
		subscribe(handleLeaderElectionResponse, networkPort);
		subscribe(handleLeaderElectionResult, networkPort);

		// adding index entry
		subscribe(handlerAddOrder, networkPort);
		subscribe(handlerAddOrderAck, networkPort);
		subscribe(handlerAddRequest, networkPort);
		subscribe(handlerAddRequestAck, networkPort);
		subscribe(handlerAddRequestTimeout, timerPort);

		// failure handling
		subscribe(handleHeartbeat, networkPort);
		subscribe(handleHeartbeatTimeout, timerPort);
		subscribe(handleLeaderDeadMsg, networkPort);
		subscribe(handleSendHeartbeatTimeout, timerPort);

		subscribe(handleKillLeaderTimeout, timerPort);

		// partition
		subscribe(handleOtherPartitionPartner, tmanSamplePort);
		subscribe(handleLookupRequest, networkPort);
		subscribe(handleLookupResponse, networkPort);


	}

	// -------------------------------------------------------------------
	Handler<SearchInit> handleInit = new Handler<SearchInit>() {
		public void handle(SearchInit init) {
			self = init.getSelf();
			num = init.getNum();
			searchConfiguration = init.getConfiguration();
			period = searchConfiguration.getPeriod();

			uComparator = new UtilityComparator(self);

			SchedulePeriodicTimeout rst = new SchedulePeriodicTimeout(period, period);
			rst.setTimeoutEvent(new UpdateIndexTimeout(rst));
			trigger(rst, timerPort);

			SchedulePeriodicTimeout st = new SchedulePeriodicTimeout(period, period);
			st.setTimeoutEvent(new IndexPullTimeout(st));
			trigger(st, timerPort);

			SchedulePeriodicTimeout st1 = new SchedulePeriodicTimeout(SEND_HEARTBEAT_TIMEOUT, SEND_HEARTBEAT_TIMEOUT);
			st1.setTimeoutEvent(new sendHeartbeatTimeout(st1));
			sendHBtimeout = st1.getTimeoutEvent().getTimeoutId();
			trigger(st1, timerPort);

			Snapshot.updateNum(self, num);
			try {
				String title = "The Art of Computer Science";
				String id = "1";
				addEntry(title, id);
			} catch (IOException ex) {
				java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
				System.exit(-1);
			}

			trace("alive");
		}
	};

	Handler<WebRequest> handleWebRequest = new Handler<WebRequest>() {
		public void handle(WebRequest event) {
			if (event.getDestination() != self.getPeerAddress().getId()) {
				return;
			}

			Request jettyReq = event.getRequest();
			String relativepath = null;
			try {
				relativepath = jettyReq.getUri().getPath().split("/")[2];
			} catch (IndexOutOfBoundsException e) {

			}
			if (relativepath != null) {
				if (relativepath.equals("docs")) {
					if (jettyReq.getMethod().equals("GET")) {
						// search
						String textToSeach = jettyReq.getParameter("q");

						// Partition
						// send query to partners
						// lookup in local index
						int idx = self.getPeerAddress().getId() % partitionNb;
						List<Document> res = lookup(textToSeach);

						if (!lookupResults.containsKey(idx)) {
							lookupResults.put(idx, res);
						}

						for (Map.Entry<Integer, List<PeerAddress>> entry : otherPartners.entrySet()) {
							for (PeerAddress p : entry.getValue()) {
								trigger(new LookupRequest(self, p, lookupReqId, textToSeach), networkPort);
							}
						}

						lookupRequest = event;
						// trigger(new WebResponse(searchPageHtml(textToSeach),
						// event, 1, 1), webPort);
					} else if (jettyReq.getMethod().equals("POST")) {
						// add
						try {
							String text = convertStreamToString(jettyReq.getInputStream());
							if (leader != null) {
								addRequestId++;
								AddRequest req = new AddRequest(self, leader, addRequestId, text, event, 1);
								mapAddReqs.put(addRequestId, req);
								trigger(req, networkPort);
								ScheduleTimeout st = new ScheduleTimeout(ADD_REQ_TIMEOUT);
								st.setTimeoutEvent(new AddRequestTimeout(st, addRequestId));
								trigger(st, timerPort);
							} else {
								PeerAddress toSendTo = getRndHigherPeer();
								addRequestId++;
								AddRequest req = new AddRequest(self, toSendTo, addRequestId, text, event, 1);
								mapAddReqs.put(addRequestId, req);
								if (toSendTo != null) {
									trigger(req, networkPort);
								}
								ScheduleTimeout st = new ScheduleTimeout(ADD_REQ_TIMEOUT);
								st.setTimeoutEvent(new AddRequestTimeout(st, addRequestId));
								trigger(st, timerPort);
							}
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}

		}
	};
    
    Handler<AddRequestAck> handlerAddRequestAck = new Handler<AddRequestAck>() {
		@Override
		public void handle(AddRequestAck event) {
			mapAddReqs.remove(event.getReqId());
		}
	};
    
    Handler<AddRequestTimeout> handlerAddRequestTimeout = new Handler<AddRequestTimeout>() {
		@Override
		public void handle(AddRequestTimeout event) {
			AddRequest req = mapAddReqs.get(event.getReqId());
			if (req != null) {
				trigger(req, networkPort);
				ScheduleTimeout st = new ScheduleTimeout(ADD_REQ_TIMEOUT);
				st.setTimeoutEvent(new AddRequestTimeout(st, event.getReqId()));
				trigger(st, timerPort);
			}
		}
	};

	Handler<AddRequest> handlerAddRequest = new Handler<AddRequest>() {
		@Override
		public void handle(AddRequest event) {
			if (isLeader) { // the node is the leader
				trace("AddRequest rcvd: nbHops: " + event.getNbHops());
				monotonicEntryId++;
				// add entry to index
				try {
					addEntry(event.getText(), String.valueOf(monotonicEntryId));
				} catch (IOException e) {
					logger.error(e.getLocalizedMessage());
					e.printStackTrace();
				}
				// order the election group to add the entry
				mapEntryIdAddReqs.put(monotonicEntryId, event);
				mapEntryIdNbAcks.put(monotonicEntryId, 0);
				for (PeerAddress peer : electionGroup) {
					trigger(new AddOrder(self, peer, event.getText(), monotonicEntryId), networkPort);
				}
			} else if (leader != null) { // the node is in the election group,
											// forward request to the leader
				trigger(new AddRequest(event.getPeerSource(), leader, event.getReqId(), event.getText(),
						event.getWebReq(), event.getNbHops() + 1), networkPort);
			} else { // the node is not in the election group, forward request
						// to a random higher peer in the gradient
				PeerAddress dest = getRndHigherPeer();
				if (dest != null) {// if no higher peer in the gradient, the
									// request is lost & will be resent after
									// timeout
					trigger(new AddRequest(event.getPeerSource(), dest, event.getReqId(), event.getText(),
							event.getWebReq(), event.getNbHops() + 1), networkPort);
				}

			}
		}
	};

	Handler<AddOrder> handlerAddOrder = new Handler<AddOrder>() {
		@Override
		public void handle(AddOrder event) {
			if (event.getPeerSource().equals(leader)) {
				try {
					addEntry(event.getText(), String.valueOf(event.getEntryId()));
					trigger(new AddOrderAck(self, leader, event.getEntryId()), networkPort);
				} catch (IOException e) {
					logger.error(e.getLocalizedMessage());
					e.printStackTrace();
				}
			}
		}
	};

	Handler<AddOrderAck> handlerAddOrderAck = new Handler<AddOrderAck>() {
		@Override
		public void handle(AddOrderAck event) {
			Integer nbAcks = mapEntryIdNbAcks.get(event.getEntryId());
			if (nbAcks != null) {
				nbAcks++;
				mapEntryIdNbAcks.put(event.getEntryId(), nbAcks);
				if (nbAcks >= (electionGroup.size() / 2) + 1) {
					AddRequest initialReq = mapEntryIdAddReqs.get(event.getEntryId());
					String resp = addEntryHtml(initialReq.getText(), String.valueOf(event.getEntryId()));
					trigger(new WebResponse(resp, initialReq.getWebReq(), 1, 1), webPort);
					trigger(new AddRequestAck(self, initialReq.getPeerSource(), initialReq.getReqId()), networkPort);
					mapEntryIdAddReqs.remove(event.getEntryId());
					mapEntryIdNbAcks.remove(event.getEntryId());
				}
			}
		}
	};

	private String searchPageHtml(String title) {
		StringBuilder sb = new StringBuilder("<html><head><meta http-equiv=\"Conten");
		sb.append("t-Type\" content=\"text/html; charset=utf-8\" />");
		sb.append("<title>Kompics P2P Bootstrap Server</title>");
		sb.append("<style type=\"text/css\"><!--.style2 {font-family: ");
		sb.append("Arial, Helvetica, sans-serif; color: #0099FF;}--></style>");
		sb.append("</head><body><h2 align=\"center\" class=\"style2\">");
		sb.append("ID2210 (Decentralized Search for Piratebay)</h2><br>\n");
		try {
			query(sb, title);
		} catch (ParseException ex) {
			java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
			sb.append(ex.getMessage());
		} catch (IOException ex) {
			java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
			sb.append(ex.getMessage());
		}
		sb.append("\n</body></html>");
		return sb.toString();
	}

	private String addEntryHtml(String title, String id) {
		StringBuilder sb = new StringBuilder("<html><head><meta http-equiv=\"Conten");
		sb.append("t-Type\" content=\"text/html; charset=utf-8\" />");
		sb.append("<title>Kompics P2P Bootstrap Server</title>");
		sb.append("<style type=\"text/css\"><!--.style2 {font-family: ");
		sb.append("Arial, Helvetica, sans-serif; color: #0099FF;}--></style>");
		sb.append("</head><body><h2 align=\"center\" class=\"style2\">");
		sb.append("ID2210 Uploaded Entry</h2><br>\n");
		try {
			addEntry(title, id);
			sb.append("Entry: ").append(title).append(" - ").append(id);
		} catch (IOException ex) {
			sb.append(ex.getMessage());
			java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
		}
		sb.append("</body></html>");
		return sb.toString();
	}

	private void addEntry(String title, String id) throws IOException {
		int intId = Integer.valueOf(id);
		if ((intId > lastIndex) || (missingIndices.contains(intId))) {
			IndexWriter w = new IndexWriter(index, config);
			Document doc = new Document();
			doc.add(new TextField("title", title, Field.Store.YES));
			doc.add(new IntField("id", intId, Field.Store.YES));
			w.addDocument(doc);
			w.close();

			Snapshot.updateEntries(self, intId);
			// trace("Add entry id:" + intId);
		}
		// trace("addEntry id: " + intId);
		missingIndices.remove(intId);
		if (lastIndex < intId) {
			// update missing indices
			for (int i = lastIndex + 1; i < intId; i++) {
				missingIndices.add(i);
			}
			lastIndex = intId;
		}

		incrEntityId(intId); // for stats
	}

	private String query(StringBuilder sb, String querystr) throws ParseException, IOException {
		// the "title" arg specifies the default field to use when no field is
		// explicitly specified in the query.
		Query q = new QueryParser(Version.LUCENE_42, "title", analyzer).parse(querystr);
		IndexSearcher searcher = null;
		IndexReader reader = null;
		try {
			reader = DirectoryReader.open(index);
			searcher = new IndexSearcher(reader);
		} catch (IOException ex) {
			java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
			System.exit(-1);
		}

		int hitsPerPage = 10;
		TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage, true);

		searcher.search(q, collector);
		ScoreDoc[] hits = collector.topDocs().scoreDocs;

		// display results
		sb.append("Found ").append(hits.length).append(" entries.<ul>");
		for (int i = 0; i < hits.length; ++i) {
			int docId = hits[i].doc;
			Document d = searcher.doc(docId);
			sb.append("\n<li> Id: ").append(d.get("id")).append(" ; Content: \"").append(d.get("title")).append("\"")
					.append("</li>");
		}
		sb.append("</ul>");

		// reader can only be closed when there
		// is no need to access the documents any more.
		reader.close();
		return sb.toString();
	}

	Handler<LookupRequest> handleLookupRequest = new Handler<LookupRequest>() {

		@Override
		public void handle(LookupRequest event) {
			trace("got lookup request for " + event.getQuerystr());

			trigger(new LookupResponse(self, event.getPeerSource(), event.getReqId(), event.getQuerystr(),
					lookup(event.getQuerystr())), networkPort);

		}
	};

	Handler<LookupResponse> handleLookupResponse = new Handler<LookupResponse>() {

		@Override
		public void handle(LookupResponse event) {
			if (event.getReqId() == lookupReqId) {
				int partitionId = event.getPeerSource().getPeerAddress().getId()%partitionNb;
				if (!lookupResults.containsKey(partitionId) || lookupResults.get(partitionId).isEmpty()) {
					lookupResults.put(partitionId, event.getResults());
				}
				// Wait 1 result from each partition
				if (lookupResults.size() == partitionNb) {
					lookupReqId++;
					createLookupResult(event.getQuerystr());
				}
				trace("got lookup response : lookupResults.size == " + lookupResults.size());
			}

		}
	};

	private Set<Document> getWantedIndices(Set<Integer> wantedIndices, int fromIndex) throws IOException {
		Set<Document> wantedDocs = new HashSet<Document>();

		IndexSearcher searcher = null;
		IndexReader reader = null;
		try {
			reader = DirectoryReader.open(index);
			searcher = new IndexSearcher(reader);
		} catch (IOException ex) {
			java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
			System.exit(-1);
		}

		// last range
		// Query query = NumericRangeQuery.newIntRange("id", 1, fromIndex,
		// lastIndex, false, true);
		// ScoreDoc[] hits = searcher.search(query, lastIndex + 1).scoreDocs;
		// trace("hits.length: " + hits.length);
		// for (int i = 0; i < hits.length; ++i) {
		// int docId = hits[i].doc;
		// Document d = searcher.doc(docId);
		// wantedDocs.add(d);
		// }
		for (int j = fromIndex + 1; j <= lastIndex; j++) {
			Query query1 = NumericRangeQuery.newIntRange("id", 1, j, j, true, true);
			ScoreDoc[] hits1 = searcher.search(query1, lastIndex + 1).scoreDocs;
			for (int i = 0; i < hits1.length; ++i) {
				int docId1 = hits1[i].doc;
				Document d1 = searcher.doc(docId1);
				wantedDocs.add(d1);
			}
		}

		// missing indices
		for (Integer missingIndex : wantedIndices) {
			Query query1 = NumericRangeQuery.newIntRange("id", 1, missingIndex, missingIndex, true, true);
			ScoreDoc[] hits1 = searcher.search(query1, lastIndex + 1).scoreDocs;
			for (int i = 0; i < hits1.length; ++i) {
				int docId1 = hits1[i].doc;
				Document d1 = searcher.doc(docId1);
				wantedDocs.add(d1);
			}
		}

		return wantedDocs;
	}

	private void updateIndex(Set<Document> docs) throws IOException {
		for (Document doc : docs) {
			addEntry(doc.get("title"), doc.get("id"));
		}
	}

	private void createLookupResult(String querystr) {
		// Create new index
		StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_42);
		Directory index1 = new RAMDirectory();

		IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_42, analyzer);
		IndexWriter w = null;
		try {
			w = new IndexWriter(index1, config);
		} catch (IOException e) {
			logger.debug(e.getLocalizedMessage());
			e.printStackTrace();
		}

		// int nbHits = 0;
		int id = 1;
		for (Map.Entry<Integer, List<Document>> entry : lookupResults.entrySet()) {
			// nbHits += entry.getValue().size();
			for (Document d : entry.getValue()) {
				try {
					addDoc(w, d.get("title"), id++);
				} catch (IOException e) {
					logger.debug(e.getLocalizedMessage());
					e.printStackTrace();
				}
			}

		}
		try {
			w.close();
		} catch (IOException e1) {
			logger.debug(e1.getLocalizedMessage());
			e1.printStackTrace();
		}

		Query q = null;
		try {
			q = new QueryParser(Version.LUCENE_42, "title", analyzer).parse(querystr);
		} catch (ParseException e1) {
			logger.debug(e1.getLocalizedMessage());
			e1.printStackTrace();
		}
		IndexSearcher searcher = null;
		IndexReader reader = null;
		try {
			reader = DirectoryReader.open(index1);
			searcher = new IndexSearcher(reader);
		} catch (IOException ex) {
			java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
			System.exit(-1);
		}

		int hitsPerPage = 100;
		TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage, true);

		try {
			searcher.search(q, collector);
		} catch (IOException e) {
			logger.debug(e.getLocalizedMessage());
			e.printStackTrace();
		}
		ScoreDoc[] hits = collector.topDocs().scoreDocs;

		// /////////////////

		StringBuilder sb = new StringBuilder("<html><head><meta http-equiv=\"Conten");
		sb.append("t-Type\" content=\"text/html; charset=utf-8\" />");
		sb.append("<title>Kompics P2P Bootstrap Server</title>");
		sb.append("<style type=\"text/css\"><!--.style2 {font-family: ");
		sb.append("Arial, Helvetica, sans-serif; color: #0099FF;}--></style>");
		sb.append("</head><body><h2 align=\"center\" class=\"style2\">");
		sb.append("ID2210 (Decentralized Search for Piratebay)</h2><br>\n");

		sb.append("Found ").append(hits.length).append(" entries.<ul>");

		for (int i = 0; i < hits.length; ++i) {
			int docId = hits[i].doc;
			Document d = null;
			try {
				d = searcher.doc(docId);
			} catch (IOException e) {
				logger.debug(e.getLocalizedMessage());
				e.printStackTrace();
			}
			sb.append("\n<li> Id: ").append(d.get("id")).append(" ; Content: \"").append(d.get("title")).append("\"")
					.append("</li>");
		}

		sb.append("\n</body></html>");

		// clear the map
		lookupResults.clear();

		trigger(new WebResponse(sb.toString(), lookupRequest, 1, 1), webPort);
	}

	private List<Document> lookup(String querystr) {

		IndexSearcher searcher = null;
		IndexReader reader = null;
		Query q = null;
		List<Document> results = new ArrayList<Document>();

		try {
			q = new QueryParser(Version.LUCENE_42, "title", analyzer).parse(querystr);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			reader = DirectoryReader.open(index);
			searcher = new IndexSearcher(reader);
		} catch (IOException ex) {
			java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
			System.exit(-1);
		}

		int hitsPerPage = 10;
		TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage, true);

		try {
			searcher.search(q, collector);
		} catch (IOException e) {
			logger.debug(e.getLocalizedMessage());
			e.printStackTrace();
		}
		ScoreDoc[] hits = collector.topDocs().scoreDocs;

		for (int i = 0; i < hits.length; ++i) {
			int docId = hits[i].doc;
			Document d = null;
			try {
				d = searcher.doc(docId);
			} catch (IOException e) {
				logger.debug(e.getLocalizedMessage());
				e.printStackTrace();
			}

			if (d != null) {
				results.add(d);
			}
		}

		try {
			reader.close();
		} catch (IOException e) {
			logger.debug(e.getLocalizedMessage());
			e.printStackTrace();
		}

		return results;

	}

	private static void addDoc(IndexWriter w, String title, int intId) throws IOException {

		Document doc = new Document();
		doc.add(new TextField("title", title, Field.Store.YES));
		doc.add(new IntField("id", intId, Field.Store.YES));
		w.addDocument(doc);
	}

	// ----------------------------INDEX SHUFFLING---------------------------

	// ----------------------------PULL-BASED------------------------------------

	Handler<UpdateIndexTimeout> handleUpdateIndexTimeout = new Handler<UpdateIndexTimeout>() {

		@Override
		public void handle(UpdateIndexTimeout event) {
			PeerAddress higherPeer = getRndHigherPeer();
			if (higherPeer != null) {
				trigger(new IndexPullRequest(self, higherPeer, missingIndices, lastIndex), networkPort);
			}
		}
	};

	Handler<IndexPullRequest> handleIndexPullRequest = new Handler<IndexPullRequest>() {

		@Override
		public void handle(IndexPullRequest event) {
			Set<Document> requestedDocs = null;
			try {
				requestedDocs = getWantedIndices(event.getMissing(), event.getLastIndex());
			} catch (IOException e) {
				logger.debug(e.getLocalizedMessage());
				e.printStackTrace();
			}

			trigger(new IndexPullResponse(self, event.getPeerSource(), requestedDocs), networkPort);
		}
	};

	Handler<IndexPullResponse> handleIndexPullResponse = new Handler<IndexPullResponse>() {

		@Override
		public void handle(IndexPullResponse event) {
			// update index
			try {
				updateIndex(event.getEntries());
			} catch (IOException e) {
				logger.debug(e.getLocalizedMessage());
				e.printStackTrace();
			}

		}

	};


	// -------------------------------ANTI-ENTROPY----------------------------------

	Handler<IndexShuffleRequest> handleIndexShuffleRequest = new Handler<IndexShuffleRequest>() {
		@Override
		public void handle(IndexShuffleRequest event) {
			Set<Document> requestedDocs = null;
			try {
				requestedDocs = getWantedIndices(event.getMissing(), event.getLastIndex());
			} catch (IOException e) {
				logger.debug(e.getLocalizedMessage());
				e.printStackTrace();
			}

			trigger(new IndexShuffleResp1(self, event.getPeerSource(), requestedDocs, missingIndices, lastIndex),
					networkPort);

		}
	};

	Handler<IndexShuffleResp1> handleIndexShuffleResp1Handler = new Handler<IndexShuffleResp1>() {

		@Override
		public void handle(IndexShuffleResp1 event) {
			Set<Document> requestedDocs = null;

			// update index
			try {
				updateIndex(event.getSentEntries());
			} catch (IOException e) {
				logger.debug(e.getLocalizedMessage());
				e.printStackTrace();
			}

			// find requested index entries
			try {
				requestedDocs = getWantedIndices(event.getWantedEntries(), event.getLastIndex());
			} catch (IOException e) {
				logger.debug(e.getLocalizedMessage());
				e.printStackTrace();
			}

			trigger(new IndexShuffleResp2(self, event.getPeerSource(), requestedDocs), networkPort);
		}
	};

	Handler<IndexShuffleResp2> handleIndexShuffleResp2Handler = new Handler<IndexShuffleResp2>() {

		@Override
		public void handle(IndexShuffleResp2 event) {
			// update index
			try {
				updateIndex(event.getSentEntries());
			} catch (IOException e) {
				logger.debug(e.getLocalizedMessage());
				e.printStackTrace();
			}
		}
	};

	// ---------------------------------------------------------------------------------

	Handler<CyclonSample> handleCyclonSample = new Handler<CyclonSample>() {
		@Override
		public void handle(CyclonSample event) {
			// receive a new list of neighbours
			cyclonPartners = event.getSample();
			Snapshot.updateCyclonPartners(self, cyclonPartners);
			if (cyclonPartners.isEmpty()) {
				// trace("received empty cyclon sample");
			}
			if (ANTI_ENTROPY_ON) {
				// Pick a node or more, and exchange index with them
				if (!cyclonPartners.isEmpty()) {
					Random random = new Random();
					PeerAddress peer = cyclonPartners.get(random.nextInt(cyclonPartners.size()));
					Snapshot.updateRandSelectedPeer(self, peer);
					trigger(new IndexShuffleRequest(self, peer, missingIndices, lastIndex), networkPort);
				}
			}
		}
	};

	Handler<OtherPartitionsPartner> handleOtherPartitionPartner = new Handler<OtherPartitionsPartner>() {

		@Override
		public void handle(OtherPartitionsPartner event) {
			PeerAddress partner = event.getPartner();

			int mapId = partner.getPeerAddress().getId() % partitionNb;
			if (!otherPartners.containsKey(mapId)) {
				otherPartners.put(mapId, new ArrayList<PeerAddress>());
			}
			int mapEntrySize = otherPartners.get(mapId).size();
			if (mapEntrySize >= otherPatnersNb) {
				otherPartners.get(mapId).remove(0);
			}
			otherPartners.get(mapId).add(partner);

		}
	};

	Handler<TManSample> handleTManSample = new Handler<TManSample>() {
		@Override
		public void handle(TManSample event) {
			nbGossipRound++;
			// receive a new list of neighbours
			List<PeerAddress> tmanSamples = event.getSample();
			if (tmanSamples.equals(tmanPartners)) {
				currentConvergence++;
				if (currentConvergence >= CONVERGENCE_TRHESHOLD) {
					gradientHasConverged = true;
					if (!onGoingElection && leader == null) {

						// try to see if I am the leader
						boolean possibleLeader = true;
						synchronized (tmanPartners) {
							try {
						for (PeerAddress peer : tmanPartners) {
							if (uComparator.peerUtility(self) < uComparator.peerUtility(peer)) {
								possibleLeader = false;
							}
								}
							} catch (Exception e) {

							}
						}
						if (possibleLeader) {
							// trace("gradient converged, leader null, I am possible leader");
							if (nbFailedElections == 0) {
								trace("The gradient has converged in " + nbGossipRound + " gossip rounds");
							}
							// launch new election
							trace("Launching new election");
							onGoingElection = true;
							leaderElectionAcks = 0;
							electionId++;
							for (PeerAddress peer : tmanPartners) {
								// start quorum based leader election
								trigger(new LeaderElectionRequest(self, peer, electionId), networkPort);
							}
						}
					} else if (isLeader && newElectionGroup) {
						// new election group
						electionGroup = new ArrayList<PeerAddress>(tmanPartners); // copy
						for (PeerAddress peer : tmanPartners) {
							trigger(new LeaderElectionResult(self, peer, electionGroup), networkPort);
						}

						newElectionGroup = false;
					}
				}
			} else {
				gradientHasConverged = false;
				currentConvergence = 0;
			}

			tmanPartners = tmanSamples;
		}
	};

	Handler<LeaderElectionRequest> handleLeaderElectionRequest = new Handler<LeaderElectionRequest>() {
		@Override
		public void handle(LeaderElectionRequest event) {
			// trace("Leader election request recveid");
			PeerAddress asker = event.getPeerSource();
			boolean askerIsLeader = true;
			if (isLeader || leader != null || !gradientHasConverged
					|| uComparator.peerUtility(self) > uComparator.peerUtility(asker)) {
				askerIsLeader = false;
			} else {
				synchronized (tmanPartners) {
					try {
				for (PeerAddress peer : tmanPartners) {
					if (uComparator.peerUtility(peer) > uComparator.peerUtility(asker)) {
						askerIsLeader = false;
					}
				}
					} catch (Exception e) {

					}
				}
			}
			// trace("Resp to " + asker.getPeerAddress().getId() +
			// " he is the leader: " + askerIsLeader);
			trigger(new LeaderElectionResponse(self, asker, askerIsLeader, event.getElectionId()), networkPort);
		}
	};

	Handler<LeaderElectionResponse> handleLeaderElectionResponse = new Handler<LeaderElectionResponse>() {
		@Override
		public void handle(LeaderElectionResponse event) {
			if (event.getElectionId() == electionId) {
				// trace("Receive response that I am the leader:" +
				// event.isLeader());
				if (event.isLeader()) {
					leaderElectionAcks++;
					if (leaderElectionAcks > (tmanPartners.size() / 2 + 1)) {
						// self is elected as leader
						trace("Election succeed. I am the leader. Nb of failed attempts: " + nbFailedElections);
						isLeader = true;
						leader = self;
						electionGroup = new ArrayList<PeerAddress>(tmanPartners); // copy
						for (PeerAddress peer : tmanPartners) {
							trigger(new LeaderElectionResult(self, peer, electionGroup), networkPort);
						}
						onGoingElection = false;
						electionId++;

						// ScheduleTimeout st = new ScheduleTimeout(4000);
						// KillLeaderTimeout tmt = new KillLeaderTimeout(st);
						// st.setTimeoutEvent(tmt);
						// trigger(st, timerPort);
						
						nbFailedElections = 0;
					}
				} else {
					trace("Election failed");
					electionId++;
					leaderElectionAcks = 0;
					onGoingElection = false;
					nbFailedElections++;
				}
			}
		}
	};

	Handler<KillLeaderTimeout> handleKillLeaderTimeout = new Handler<KillLeaderTimeout>() {

		@Override
		public void handle(KillLeaderTimeout event) {

			trigger(new Stop(), control);

		}
	};

	Handler<LeaderElectionResult> handleLeaderElectionResult = new Handler<LeaderElectionResult>() {
		@Override
		public void handle(LeaderElectionResult event) {
			// trace("Received new leader result");
			leader = event.getPeerSource();
			electionGroup = event.getElectionGroup();
			
		}
	};

	synchronized private List<PeerAddress> getHigherTmanPartners() {
		List<PeerAddress> higherPeers = Collections.synchronizedList(new ArrayList<PeerAddress>());
		try {
			synchronized (tmanPartners) {
				for (PeerAddress peer : tmanPartners) {
					if (uComparator.peerUtility(peer) > uComparator.peerUtility(self)) {
						higherPeers.add(peer);
					} else {
						break;
					}
				}
			}			
		} catch (Exception e) {

		}
		return higherPeers;
	}

	synchronized private PeerAddress getRndHigherPeer() {
		List<PeerAddress> higherPeers = getHigherTmanPartners();

		if (!higherPeers.isEmpty()) {
			if (higherPeers.size() > 1) {
				Random rand = new Random();
				return higherPeers.get(rand.nextInt(higherPeers.size() - 1));
			} else {
				return higherPeers.get(0);
			}

		} else {
			return null;
		}
	}

	Handler<sendHeartbeatTimeout> handleSendHeartbeatTimeout = new Handler<sendHeartbeatTimeout>() {

		@Override
		public void handle(sendHeartbeatTimeout event) {
			if (isLeader) {
				for (PeerAddress p : electionGroup) {
					trigger(new Heartbeat(self, p), networkPort);
				}
			} else if (leader != null) {
				trigger(new Heartbeat(self, leader), networkPort);
			}
		}
	};

	Handler<Heartbeat> handleHeartbeat = new Handler<Heartbeat>() {

		@Override
		public void handle(Heartbeat event) {

			if (isLeader) {
				int peerId = event.getPeerSource().getPeerAddress().getId();
				if (electionGroupHb.containsKey(peerId)) {
					CancelTimeout ct = new CancelTimeout(electionGroupHb.get(peerId));
					trigger(ct, timerPort);

					ScheduleTimeout st = new ScheduleTimeout(HEARTBEAT_TIMEOUT);
					HeartbeatTimeout hbTimeout = new HeartbeatTimeout(st);
					electionGroupHb.put(event.getPeerSource(), hbTimeout.getTimeoutId());
					st.setTimeoutEvent(hbTimeout);
					trigger(st, timerPort);
				} else {// leader alive

					if (event.getPeerSource().equals(leader)) {
						CancelTimeout ct = new CancelTimeout(leaderHb);
					trigger(ct, timerPort);

					ScheduleTimeout st = new ScheduleTimeout(HEARTBEAT_TIMEOUT);
					HeartbeatTimeout hbTimeout = new HeartbeatTimeout(st);
						leaderHb = hbTimeout.getTimeoutId();
					st.setTimeoutEvent(hbTimeout);
					trigger(st, timerPort);
				}

				}

			}

		}
	};

	Handler<HeartbeatTimeout> handleHeartbeatTimeout = new Handler<HeartbeatTimeout>() {

		@Override
		public void handle(HeartbeatTimeout event) {
			if (!isLeader && event.getTimeoutId() == leaderHb) {
				// suspect leader
				trace("I suspect the leader to be dead");
				for (PeerAddress p : electionGroup) {
					trigger(new LeaderDeadMessage(self, p), networkPort);
				}
			} else {
				PeerAddress peer = null;
				for (Entry<PeerAddress, UUID> e : electionGroupHb.entrySet()) {
					if (e.getValue().equals(event.getTimeoutId())) {
						peer = e.getKey();
					}
				}
				tmanPartners.remove(peer);
				currentConvergence = 0;
				gradientHasConverged = false;
				newElectionGroup = true;

				trace("I am the leader and I have hadetected a death in election group");

			}
		}
	};

	Handler<LeaderDeadMessage> handleLeaderDeadMsg = new Handler<LeaderDeadMessage>() {

		@Override
		public void handle(LeaderDeadMessage event) {
			leaderSuspicion++;

			if (!(leader == null) && leaderSuspicion == ((electionGroup.size() / 2) + 1)) {
				// quorum of election group suspect leader
				trace("Elecion group quorum says leader dead");

				leaderSuspicion = 0;
				tmanPartners.remove(leader);
				electionGroup = null;
				leader = null;
				gradientHasConverged = false;
				currentConvergence = 0;

				//don't send heartbeat to dead leader
				CancelTimeout ct1 = new CancelTimeout(sendHBtimeout);
				trigger(ct1, timerPort);

			}

		}
	};

	// -------------------------------------------------------------------
	Handler<AddIndexText> handleAddIndexText = new Handler<AddIndexText>() {
		@Override
		public void handle(AddIndexText event) {
			// Random r = new Random(System.currentTimeMillis());
			// String id = Integer.toString(r.nextInt(100000));
			// logger.info(self.getPeerAddress().getId()
			// + " - adding index entry: {}-{}", event.getText(), id);
			// try {
			// addEntry(event.getText(), id);
			// } catch (IOException ex) {
			// java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE,
			// null, ex);
			// throw new IllegalArgumentException(ex.getMessage());
			// }
		}
	};

	public void trace(String mess) {
		String toWrite = "Node" + self.getPeerAddress().getId() + ". " + mess;
		logger.info(toWrite);
	}
	
	public static String convertStreamToString(java.io.InputStream is) {
	    java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
	    return s.hasNext() ? s.next() : "";
	}

	// Used for collecting statistics
	public int nbGossipRound = 0; // tman round
	public int nbFailedElections = 0;
	public static Map<Integer, Integer> mapConsistency = new HashMap<Integer, Integer>();

	synchronized public static void incrEntityId(int entityId) {
		Integer nb = mapConsistency.get(entityId);
		if (nb == null) {
			logger.info("First node updated with entity id " + entityId);
			mapConsistency.put(entityId, 1);
		} else {
			mapConsistency.put(entityId, nb + 1);
			int NB_NODES = 29;
			if ((nb + 1) == NB_NODES) {
				logger.info("All nodes updated with entity id " + entityId);
			}
		}
	}
}
