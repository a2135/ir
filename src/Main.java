import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jsoup.Jsoup;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

public class Main {
    public static void main(String[] args) throws IOException {


        if (args.length < 4) {
            System.out.println("Not enough arguments");
            System.out.println("Usage: java -jar \"IR_P01.jar\" [path to document folder] [path to index folder] [VS/OK] \"[query]\"");
            System.exit(0);
        }

        String docsFolder = args[0];
        String indexFolder = args[1];
        String model = args[2];
        String query = args[3];


        Directory directory = FSDirectory.open(Paths.get(indexFolder));

        indexer indexer;


        if (DirectoryReader.indexExists(directory)) {
            // Index exists
            System.out.println("Found index in folder, using it");

            indexer = new indexer(directory, model, false, true);

            indexer.index(new ArrayList<>(), false);

            IndexReader read = indexer.getIndexReader();

            for (int i=0; i<read.maxDoc(); i++) {
                org.apache.lucene.document.Document doc = read.document(i);
                String path = doc.get("path");

                System.out.println("Found file: " + path);

            }

            read.close();

        } else {
            // No index
            System.out.println("No index in folder, creating new one");
            ArrayList<HashMap<String, String>> docs = new ArrayList<>();


            for (File file : listf(docsFolder)) {
                System.out.println("Indexing " + file.getAbsolutePath());

                HashMap<String, String> tmp = new HashMap<>();
                org.jsoup.nodes.Document doc = Jsoup.parse(file, "UTF-8");

                String title = doc.title();
                String body = doc.body().text();

                tmp.put("title", title);
                tmp.put("content", body);
                tmp.put("path", file.getAbsolutePath());

                docs.add(tmp);
            }

            indexer = new indexer(directory, model, false, true);
            indexer.index(docs, true);
        }

        System.out.println("Using " + model + " similarity");



        ArrayList<HashMap<String, String>> results = indexer.search(query, new String[]{"title", "content"});

        System.out.println(results.size() + " hit(s):");

        for (HashMap<String, String> result : results) {
            System.out.println(result.get("rank") + ". " + result.get("title"));
            System.out.println("Rank: " + result.get("rank"));
            System.out.println("Score: " + result.get("score"));
            System.out.println("Summary: " + result.get("summary"));
            System.out.println("Path: " + result.get("path"));
            System.out.println("---------------");
        }


    }

   static File[] findHTML (String dirName) {
       File dir = new File(dirName);

       return dir.listFiles(new FilenameFilter() {
           public boolean accept(File dir, String filename)
           { return filename.endsWith(".html") || filename.endsWith(".htm"); }
       } );

   }

    public static ArrayList<File> listf(String directoryName) {
        File directory = new File(directoryName);

        ArrayList<File> resultList = new ArrayList<File>();


        // get all the files from a directory
        File[] fList = directory.listFiles();

        for (File file : fList) {
            if (file.isFile() && (file.getName().endsWith(".html") || file.getName().endsWith(".htm"))) {
                resultList.add(file);
            }
            if (file.isDirectory()) {
                resultList.addAll(listf(file.getAbsolutePath()));
            }
        }
        return resultList;
    }

}