import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.document.Field;

public class indexer {
    Directory corpus;

    private String analyzer = "bm25";
    private boolean stopwords;
    private boolean stemmer;
    private IndexWriterConfig iwc;

    public indexer(Directory d, String analyzer, boolean stopwords, boolean stemmer) {
        this.corpus = d;
        this.analyzer = analyzer;
        this.stopwords = stopwords;
        this.stemmer = stemmer;
    }


    public void index(ArrayList<HashMap<String, String>> docs, boolean ind) throws IOException
    {
        Analyzer analyz;
        IndexWriterConfig config;

        if (analyzer.equals("VS") && stopwords && stemmer)
        {
            //VSM cosine similarity with TFIDF + stopwords + stemmer
            CharArraySet stopWords = EnglishAnalyzer.getDefaultStopSet();
            analyz = new EnglishAnalyzer(stopWords);
            config = new IndexWriterConfig(analyz);
            config.setSimilarity(new ClassicSimilarity());
        }
        else if (analyzer.equals("VS") && !stopwords && stemmer)
        {
            //VSM cosine similarity with TFIDF - stopwords + stemmer
            analyz = new EnglishAnalyzer(CharArraySet.EMPTY_SET);
            config = new IndexWriterConfig(analyz);
            config.setSimilarity(new ClassicSimilarity());
        }
        else if (analyzer.equals("VS") && stopwords && !stemmer)
        {
            //VSM cosine similarity with TFIDF - stopwords - stemmer
            CharArraySet stopWords = StandardAnalyzer.STOP_WORDS_SET;
            analyz = new StandardAnalyzer(stopWords);
            config = new IndexWriterConfig(analyz);
            config.setSimilarity(new ClassicSimilarity());
        }
        else if (analyzer.equals("OK") && stopwords && stemmer)
        {
            //Analyzer + stopwords + stemmer
            CharArraySet stopWords = EnglishAnalyzer.getDefaultStopSet();
            analyz = new EnglishAnalyzer(stopWords);
            config = new IndexWriterConfig(analyz);
            //BM25 ranking method
            config.setSimilarity(new BM25Similarity());
        }
        else if (analyzer.equals("OK") && !stopwords && stemmer)
        {
            //Analyzer - stopwords + stemmer
            analyz = new EnglishAnalyzer(CharArraySet.EMPTY_SET);
            config = new IndexWriterConfig(analyz);
            //BM25 ranking method
            config.setSimilarity(new BM25Similarity());
        }
        else if (analyzer.equals("OK") && stopwords && !stemmer)
        {
            //Analyzer + stopwords - stemmer
            CharArraySet stopWords = StandardAnalyzer.STOP_WORDS_SET;
            analyz = new StandardAnalyzer(stopWords);
            config = new IndexWriterConfig(analyz);
            //BM25 ranking method
            config.setSimilarity(new BM25Similarity());
        }
        else
        {
            //some default
            analyz = new StandardAnalyzer();
            config = new IndexWriterConfig(analyz);
            config.setSimilarity(new ClassicSimilarity());
        }


        if (ind) {
            IndexWriter w = new IndexWriter(corpus, config);

            for (HashMap<String, String> doc1: docs) {
                Document doc = new Document();
                doc.add(new TextField("title", doc1.get("title"), Field.Store.YES));
                doc.add(new TextField("content", doc1.get("content"), Field.Store.YES));
                doc.add(new TextField("path", doc1.get("path"), Field.Store.YES));
                w.addDocument(doc);
            }

            w.close();
        }


        this.iwc = config;
    }

    public ArrayList<HashMap<String, String>> search(String searchQuery, String[] fields) throws IOException {
        QueryParser qp = new MultiFieldQueryParser(
                fields,
                this.iwc.getAnalyzer());

        Query stemmedQuery = null;

        try {
            stemmedQuery = qp.parse(searchQuery);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        IndexReader reader = DirectoryReader.open(corpus);
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(this.iwc.getSimilarity());

        TopDocs docs = searcher.search(stemmedQuery, 10);
        ScoreDoc[] scored = docs.scoreDocs;

        ArrayList<HashMap<String, String>> results = new ArrayList<>();

        SimpleHTMLFormatter htmlFormatter = new SimpleHTMLFormatter();
        Highlighter highlighter = new Highlighter(htmlFormatter, new QueryScorer(stemmedQuery));

        int dc = 1;

        for (ScoreDoc aDoc : scored) {
            Document d = searcher.doc(aDoc.doc);

            HashMap<String, String> tmp = new HashMap<String, String>();




            String text = d.get("content");
            String title = d.get("title");

            TokenStream tokenStream = TokenSources.getAnyTokenStream(reader, aDoc.doc, "content", this.iwc.getAnalyzer());
            TextFragment[] frag = new TextFragment[0];
            try {
                frag = highlighter.getBestTextFragments(tokenStream, text, false, 1);
            } catch (InvalidTokenOffsetsException e) {
                e.printStackTrace();
            }

            String sum = "";

            for (int j = 0; j < frag.length; j++) {
                if ((frag[j] != null) && (frag[j].getScore() > 0)) {
                    sum = frag[j].toString();
                }
            }

            if (sum.length() == 0 ) {
                TokenStream tokenStream1 = TokenSources.getAnyTokenStream(reader, aDoc.doc, "title", this.iwc.getAnalyzer());
                TextFragment[] frag1 = new TextFragment[0];
                try {
                    frag1 = highlighter.getBestTextFragments(tokenStream1, title, false, 1);
                } catch (InvalidTokenOffsetsException e) {
                    e.printStackTrace();
                }
                for (int j = 0; j < frag1.length; j++) {
                    if ((frag1[j] != null) && (frag1[j].getScore() > 0)) {
                        sum = frag1[j].toString();
                    }
                }
            }
            tmp.put("rank", Integer.toString(dc));
            tmp.put("score", Float.toString(aDoc.score));
            tmp.put("summary", sum);
            tmp.put("path", d.get("path"));
            tmp.put("title", title);

            results.add(tmp);
            dc++;

        }
        return results;
    }

    public IndexReader getIndexReader() throws IOException {
        return DirectoryReader.open(corpus);
    }
}