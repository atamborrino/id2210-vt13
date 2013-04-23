package search.system.peer.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timer;
import se.sics.kompics.web.Web;
import se.sics.kompics.web.WebRequest;
import se.sics.kompics.web.WebResponse;
import search.simulator.snapshot.Snapshot;
import search.system.peer.AddIndexText;
import search.system.peer.IndexPort;
import tman.system.peer.tman.TManSample;
import tman.system.peer.tman.TManSamplePort;

import common.configuration.SearchConfiguration;
import common.peer.PeerAddress;

import cyclon.system.peer.cyclon.CyclonSample;
import cyclon.system.peer.cyclon.CyclonSamplePort;

/**
 * Should have some comments here.
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

    ArrayList<PeerAddress> neighbours = new ArrayList<PeerAddress>();
    Random randomGenerator = new Random();
    private PeerAddress self;
    private long period;
    private double num;
    private SearchConfiguration searchConfiguration;
    // Apache Lucene used for searching
    StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_42);
    Directory index = new RAMDirectory();
    IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_42, analyzer);

	// Anti-entropy search
	int lastIndex = 0;
	Set<Integer> missingIndices = new HashSet<Integer>();

//-------------------------------------------------------------------	
    public Search() {

        subscribe(handleInit, control);
        subscribe(handleWebRequest, webPort);
        subscribe(handleCyclonSample, cyclonSamplePort);
        subscribe(handleTManSample, tmanSamplePort);
        subscribe(handleAddIndexText, indexPort);

		subscribe(handleIndexShuffleRequest, networkPort);
		subscribe(handleIndexShuffleResp1Handler, networkPort);
		subscribe(handleIndexShuffleResp2Handler, networkPort);
    }
//-------------------------------------------------------------------	
    Handler<SearchInit> handleInit = new Handler<SearchInit>() {
        public void handle(SearchInit init) {
            self = init.getSelf();
            num = init.getNum();
            searchConfiguration = init.getConfiguration();
            period = searchConfiguration.getPeriod();

            SchedulePeriodicTimeout rst = new SchedulePeriodicTimeout(period, period);
            rst.setTimeoutEvent(new UpdateIndexTimeout(rst));
            trigger(rst, timerPort);

            Snapshot.updateNum(self, num);
            try {
                String title = "The Art of Computer Science";
				String id = "1";
                addEntry(title,id);
            } catch (IOException ex) {
                java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
                System.exit(-1);
            }
        }
    };


    Handler<WebRequest> handleWebRequest = new Handler<WebRequest>() {
        public void handle(WebRequest event) {
            if (event.getDestination() != self.getPeerAddress().getId()) {
                return;
            }

            String[] args = event.getTarget().split("-");

            logger.debug("Handling Webpage Request");
            WebResponse response;
            if (args[0].compareToIgnoreCase("search") == 0) {
                response = new WebResponse(searchPageHtml(args[1]), event, 1, 1);
            } else if (args[0].compareToIgnoreCase("add") == 0) {
                response = new WebResponse(addEntryHtml(args[1], args[2]), event, 1, 1);
            } else {
                response = new WebResponse(searchPageHtml(event
                        .getTarget()), event, 1, 1);
            }
            trigger(response, webPort);
        }
    };

    private String searchPageHtml(String title) {
        StringBuilder sb = new StringBuilder("<!DOCTYPE html PUBLIC \"-//W3C");
        sb.append("//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR");
        sb.append("/xhtml1/DTD/xhtml1-transitional.dtd\"><html xmlns=\"http:");
        sb.append("//www.w3.org/1999/xhtml\"><head><meta http-equiv=\"Conten");
        sb.append("t-Type\" content=\"text/html; charset=utf-8\" />");
        sb.append("<title>Kompics P2P Bootstrap Server</title>");
        sb.append("<style type=\"text/css\"><!--.style2 {font-family: ");
        sb.append("Arial, Helvetica, sans-serif; color: #0099FF;}--></style>");
        sb.append("</head><body><h2 align=\"center\" class=\"style2\">");
        sb.append("ID2210 (Decentralized Search for Piratebay)</h2><br>");
        try {
            query(sb, title);
        } catch (ParseException ex) {
            java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
            sb.append(ex.getMessage());
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
            sb.append(ex.getMessage());
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private String addEntryHtml(String title, String id) {
        StringBuilder sb = new StringBuilder("<!DOCTYPE html PUBLIC \"-//W3C");
        sb.append("//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR");
        sb.append("/xhtml1/DTD/xhtml1-transitional.dtd\"><html xmlns=\"http:");
        sb.append("//www.w3.org/1999/xhtml\"><head><meta http-equiv=\"Conten");
        sb.append("t-Type\" content=\"text/html; charset=utf-8\" />");
        sb.append("<title>Adding an Entry</title>");
        sb.append("<style type=\"text/css\"><!--.style2 {font-family: ");
        sb.append("Arial, Helvetica, sans-serif; color: #0099FF;}--></style>");
        sb.append("</head><body><h2 align=\"center\" class=\"style2\">");
        sb.append("ID2210 Uploaded Entry</h2><br>");
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
			// You may need to make the StringField searchable by
			// NumericRangeQuery. See:
			// http://stackoverflow.com/questions/13958431/lucene-4-0-indexwriter-updatedocument-for-numeric-term
			// http://lucene.apache.org/core/4_2_0/core/org/apache/lucene/document/IntField.html
			doc.add(new IntField("id", intId, Field.Store.YES));
			w.addDocument(doc);
			w.close();

			Snapshot.updateEntries(self, intId);
			// trace("Add entry id:" + intId);
		}

		// anti-entropy
		trace("addEntry id: " + intId);
		missingIndices.remove(intId);
		if (lastIndex < intId) {
			// update missing indices
			for (int i = lastIndex + 1; i < intId; i++) {
				missingIndices.add(i);
			}
			lastIndex = intId;
		}

    }

    private String query(StringBuilder sb, String querystr) throws ParseException, IOException {

        // the "title" arg specifies the default field to use when no field is explicitly specified in the query.
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
            sb.append("<li>").append(i + 1).append(". ").append(d.get("id")).append("\t").append(d.get("title")).append("</li>");
        }
        sb.append("</ul>");

        // reader can only be closed when there
        // is no need to access the documents any more.
        reader.close();
        return sb.toString();
    }
    
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
			Query query1 = NumericRangeQuery.newIntRange("id", 1, j, j, true,
					true);
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

	Handler<IndexShuffleRequest> handleIndexShuffleRequest = new Handler<IndexShuffleRequest>() {
		@Override
		public void handle(IndexShuffleRequest event) {
			Set<Document> requestedDocs = null;
			try {
				requestedDocs = getWantedIndices(event.getMissing(), event.getLastIndex());
			} catch (IOException e) {
				// TODO Auto-generated catch block
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
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// find requested index entries
			try {
				requestedDocs = getWantedIndices(event.getWantedEntries(), event.getLastIndex());
			} catch (IOException e) {
				// TODO Auto-generated catch block
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
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	};

    Handler<CyclonSample> handleCyclonSample = new Handler<CyclonSample>() {
        @Override
        public void handle(CyclonSample event) {
            // receive a new list of neighbours
            neighbours = event.getSample();
            // Pick a node or more, and exchange index with them
			Snapshot.updateCyclonPartners(self, neighbours);
			if (neighbours.isEmpty()) {
				trace("received empty cyclon sample");
			}

			// ANTI-ENTROPY
			if (!neighbours.isEmpty()) {
				Random random = new Random();
				PeerAddress peer = neighbours.get(random.nextInt(neighbours.size()));
				Snapshot.updateRandSelectedPeer(self, peer);
				trigger(new IndexShuffleRequest(self, peer, missingIndices, lastIndex), networkPort);
			}
        }
    };
    
    Handler<TManSample> handleTManSample = new Handler<TManSample>() {
        @Override
        public void handle(TManSample event) {
            // receive a new list of neighbours
            ArrayList<PeerAddress> sampleNodes = event.getSample();
            // Pick a node or more, and exchange index with them
        }
    };
    
//-------------------------------------------------------------------	
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

}
