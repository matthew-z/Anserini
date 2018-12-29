package io.anserini.search.similarity;

import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.search.similarities.TFIDFSimilarity;
import org.apache.lucene.util.BytesRef;


public class BM25PRFSimilarity extends TFIDFSimilarity {
    private float k1;
    private float b;

    public BM25PRFSimilarity(float k1, float b) {
        this.k1 = k1;
        this.b = b;
    }

    //boosts results which match more query terms
    @Override
    public float coord(int overlap, int maxOverlap) {
        return 1f;
    }

    //constant per query, normalizes scores somewhat based on query
    @Override
    public float queryNorm(float sumOfSquaredWeights) {
        return 1f;
    }


    @Override
    public final long encodeNormValue(float f) {
        return 1L;
    }

    @Override
    public final float decodeNormValue(long norm) {
        return 1f;
    }

    //Weighs shorter fields more heavily
    @Override
    public float lengthNorm(FieldInvertState state) {
        return 1f;
    }

    //Higher frequency terms (more matches) scored higher
    @Override
    public float tf(float freq) {
        //return (float)Math.sqrt(freq);  The standard tf impl
        return freq;
    }

    //Weigh matches on rarer terms more heavily.
    @Override
    public float idf(long docFreq, long numDocs) {
        return 1f;
    }

    //Scores closer matches higher when using a sloppy phrase query
    @Override
    public float sloppyFreq(int distance) {
        return 1.0f;
    }

    //ClassicSimilarity doesn't really do much with payloads.  This is unmodified
    @Override
    public float scorePayload(int doc, int start, int end, BytesRef payload) {
        return 1f;
    }


    @Override
    public String toString() {
        return "SimpleSimilarity";
    }


}
