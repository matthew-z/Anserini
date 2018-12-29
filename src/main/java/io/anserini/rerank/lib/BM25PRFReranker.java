package io.anserini.rerank.lib;

import io.anserini.rerank.Reranker;
import io.anserini.rerank.RerankerContext;
import io.anserini.rerank.ScoredDocuments;
import org.apache.lucene.analysis.Analyzer;

public class BM25PRFReranker implements Reranker {

    private final int rerankNum;
    private final Analyzer analyzer;
    private final String field;
    private final float originalQueryWeight;
    private final boolean outputQuery;

    BM25PRFReranker(Analyzer analyzer, String field, int rerankNum, float originalQueryWeight, boolean outputQuery){
        this.rerankNum = rerankNum;
        this.analyzer = analyzer;
        this.originalQueryWeight = originalQueryWeight;
        this.outputQuery = outputQuery;
        this.field = field;
    }


    @Override
    public ScoredDocuments rerank(ScoredDocuments docs, RerankerContext context) {
        return null;
    }

    @Override
    public String tag() {
        return "BM25PRF(rerankNum="+rerankNum+",originalQueryWeight:"+originalQueryWeight+")";
    }
}
