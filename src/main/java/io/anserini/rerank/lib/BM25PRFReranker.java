package io.anserini.rerank.lib;

import io.anserini.rerank.Reranker;
import io.anserini.rerank.RerankerContext;
import io.anserini.rerank.ScoredDocuments;
import io.anserini.search.similarity.BM25PRFSimilarity;
import io.anserini.util.AnalyzerUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.*;

import static io.anserini.index.generator.LuceneDocumentGenerator.FIELD_BODY;

public class BM25PRFReranker implements Reranker {
    private static final Logger LOG = LogManager.getLogger(BM25PRFReranker.class);

    private final int fbDocs;
    private final Analyzer analyzer;
    private final String field;
    private final boolean outputQuery;
    private final int fbTerms;

    public BM25PRFReranker(Analyzer analyzer, String field, int fbTerms, int fbDocs, boolean outputQuery) {
        this.analyzer = analyzer;
        this.outputQuery = outputQuery;
        this.field = field;
        this.fbTerms = fbTerms;
        this.fbDocs = fbDocs;
    }

    @Override
    public ScoredDocuments rerank(ScoredDocuments docs, RerankerContext context) {

        // set similarity to BM25PRF
        IndexSearcher searcher = context.getIndexSearcher();
        BM25Similarity originalSimilarity = (BM25Similarity) searcher.getSimilarity(true);
        float k1 = originalSimilarity.getK1();
        float b = originalSimilarity.getB();

        searcher.setSimilarity(new BM25PRFSimilarity(k1, b));
        IndexReader reader = searcher.getIndexReader();
        List<String> originalQueryTerms = AnalyzerUtils.tokenize(analyzer, context.getQueryText());

        PRFFeatures fv = expandQuery(originalQueryTerms, docs, reader);
        Query newQuery = fv.toQuery(fbTerms);

        if (this.outputQuery) {
            LOG.info("QID: " + context.getQueryId());
            LOG.info("Original Query: " + context.getQuery().toString(this.field));
            LOG.info("Running new query: " + newQuery.toString(this.field));
        }

        TopDocs rs;

        try {
            rs = searcher.search(newQuery, context.getSearchArgs().hits);
        } catch (IOException e) {
            e.printStackTrace();
            return docs;
        }
        // set similarity back
        searcher.setSimilarity(originalSimilarity);
        return ScoredDocuments.fromTopDocs(rs, searcher);
    }


    class PRFFeature {
        int df;
        int dfRel;
        int numDocs;
        int numDocsRel;

        PRFFeature(int df, int dfRel, int numDocs, int numDocsRel) {
            this.df = df;
            this.dfRel = dfRel;
            this.numDocs = numDocs;
            this.numDocsRel = numDocsRel;
        }

        double getRelWeight() {
            return Math.log((dfRel + 0.5D) * (numDocs - df - numDocsRel + dfRel + 0.5D) /
                    ((df - dfRel + 0.5D) * (numDocsRel - dfRel + 0.5D)));
        }

        double getOfferWeight() {
            return getRelWeight() * dfRel;
        }
    }


    class PRFFeatures {
        private HashMap<String, PRFFeature> features;

        PRFFeatures() {
            this.features = new HashMap<>();
        }

        void addFeature(String term, int df, int dfRel, int numDocs, int numDocsRel) {
            features.put(term, new PRFFeature(df, dfRel, numDocs, numDocsRel));
        }

        double getRelWeight(String term) {
            if (!features.containsKey(term)) {
                return 0;
            }
            return features.get(term).getRelWeight();
        }


        double getOfferWeight(String term) {
            if (!features.containsKey(term)) {
                return 0;
            }
            return features.get(term).getOfferWeight();
        }


        Query toQuery(int numTerms){
            BooleanQuery.Builder feedbackQueryBuilder = new BooleanQuery.Builder();
            pruneToSize(numTerms);

            for (Map.Entry<String, PRFFeature> f : features.entrySet()) {
                String term = f.getKey();
                float prob = (float)f.getValue().getRelWeight();
                feedbackQueryBuilder.add(new BoostQuery(new TermQuery(new Term(field, term)), prob), BooleanClause.Occur.SHOULD);
            }
            return feedbackQueryBuilder.build();
        }


        private List<KeyValuePair> getOrderedFeatures() {
            List<KeyValuePair> kvpList = new ArrayList<KeyValuePair>(features.size());
            for (String feature : features.keySet()) {
                PRFFeature value = features.get(feature);
                KeyValuePair keyValuePair = new KeyValuePair(feature, value);
                kvpList.add(keyValuePair);
            }

            Collections.sort(kvpList, new Comparator<KeyValuePair>() {
                public int compare(KeyValuePair x, KeyValuePair y) {
                    double xVal = x.getValue();
                    double yVal = y.getValue();

                    return (Double.compare(yVal, xVal));
                }
            });

            return kvpList;
        }


        PRFFeatures pruneToSize(int k) {
            List<KeyValuePair> pairs = getOrderedFeatures();
            HashMap<String, PRFFeature> pruned = new HashMap<>();

            for (KeyValuePair pair : pairs) {
                pruned.put(pair.getKey(), pair.getFeature());
                if (pruned.size() >= k) {
                    break;
                }
            }

            this.features = pruned;
            return this;
        }


        private class KeyValuePair {
            private String key;
            private PRFFeature value;

            public KeyValuePair(String key, PRFFeature value) {
                this.key = key;
                this.value = value;
            }

            public String getKey() {
                return key;
            }

            @Override
            public String toString() {
                return value + "\t" + key;
            }

            public float getValue() {
                return (float) value.getOfferWeight();
            }


            public PRFFeature getFeature() {
                return value;
            }
        }


    }


    private PRFFeatures expandQuery(List<String> originalQueryTerms, ScoredDocuments docs, IndexReader reader) {
        PRFFeatures f = new PRFFeatures();
        Set<String> vocab = new HashSet<>(originalQueryTerms);

        Map<Integer, Set<String>> docToTermsMap = new HashMap<>();
        int numRelDocs = docs.documents.length < fbDocs ? docs.documents.length : fbDocs;
        int numDocs = reader.numDocs();

        for (int i = 0; i < numRelDocs; i++) {
            try {
                Terms terms = reader.getTermVector(docs.ids[i], field);
                Set<String> termsStr = getTermsStr(terms);
                docToTermsMap.put(docs.ids[i], termsStr);
                vocab.addAll(termsStr);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        for (String term : vocab) {
            try {
                int df = reader.docFreq(new Term(FIELD_BODY, term));
                int dfRel = 0;

                for (int i = 0; i < numRelDocs; i++) {
                    Set<String> terms = docToTermsMap.get(docs.ids[i]);
                    if (terms.contains(term)) {
                        dfRel++;
                    }
                }
                f.addFeature(term, df, dfRel, numDocs, numRelDocs);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return f;
    }


    @Override
    public String tag() {
        return "BM25PRF(fbDocs="+fbDocs+",fbTerms="+fbTerms;
    }


    private Set<String> getTermsStr(Terms terms) {
        Set<String> termsStr = new HashSet<>();

        try {
            TermsEnum termsEnum = terms.iterator();

            BytesRef text;
            while ((text = termsEnum.next()) != null) {
                String term = text.utf8ToString();
//                if (term.length() < 2 || term.length() > 20) continue;
//                if (!term.matches("[a-z0-9]+")) continue;
                termsStr.add(term);

            }
        } catch (Exception e) {
            e.printStackTrace();
            // Return empty feature vector
            return termsStr;
        }

        return termsStr;
    }

}
