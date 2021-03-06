import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

import javax.print.Doc;
import javax.swing.plaf.basic.BasicToolBarUI.DockingListener;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;

import org.bson.Document;

import ca.rmen.porterstemmer.PorterStemmer;

public class Indexer implements Runnable {
    // static HashMap<String, Long> keyWords = new HashMap<String, Long>();
    // word -> {{url1,TF1},{url2,TF2},{url3,TF3},{url4,TF4}}
    public HashMap<String, ArrayList<String>> indexer = new HashMap<String, ArrayList<String>>();
    public ArrayList<String> stopWords = new ArrayList<String>();
    public Hashtable<String, List<Document>> docsKeyURLS = new Hashtable<String, List<Document>>();
    public MongoDBManager dbManager = new MongoDBManager();
    public MongoCursor<Document> cursor;
    public AtomicInteger i = new AtomicInteger();

    Indexer(Hashtable<String, List<Document>> indx) {
        try {
            // first we need to indetify the stop words we shouldn't index
            fillStopWords();

            docsKeyURLS = indx;

            long start = System.currentTimeMillis();
            this.cursor = dbManager.retrieveFromCrawler().iterator();
            long end = System.currentTimeMillis();
            System.out.println("\n******************************\nretrive time: " + (end - start) / 1000
                    + "s\n******************************\n");

        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    public void run() {
        try {
            Long start = System.currentTimeMillis();
            //for (Document doc : cursor) {
            while(checkCursor()){
                Document doc = getCursor();
                System.out.println(i + "- " + doc.get("URL"));

                String pageContent = (doc.get("HTML_Document").toString());
                String url = doc.get("URL").toString();
                String title = doc.get("title").toString();
                String description = doc.get("Description").toString();
                pageContent = pageContent.replaceAll("<style([\\s\\S]+?)</style>", "");
                pageContent = pageContent.replaceAll("<script([\\s\\S]+?)</script>", "");
                pageContent = pageContent.replaceAll("<meta[^>]*>", "");
                pageContent = pageContent.replaceAll("<link[^>]*>", "");
                // pageContent = pageContent.replaceAll("<script[^>]*>[^>]*</script>", "");
                pageContent = pageContent.replaceAll("<head>[^>]*</head>", "");
                pageContent = pageContent.replaceAll("<[^>]*>", "");

                // here we index each page one by one
                Index(pageContent, url, title, description);

                synchronized (this.i) {
                    i.incrementAndGet();
                }

                // synchronized(this.docsKeyURLS){
                //     if(i.get()>=500){
                //         InsertIntoDB(dbManager);
                //         docsKeyURLS.clear();
                //         i.set(0);
                //     }
                // }
            }

            // create the inverted file and store it in the indexer
            // writeToFile(indexer);
            // InsertIntoDB(dbManager);

            Long end = System.currentTimeMillis();
            System.out.println("\n******************************\nindexing time: " + (end - start) / 1000
                    + "s\n******************************\n");

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private void Index(String pageContent, String url, String title, String desc) {
        try {
            String[] words = pageContent.split(" ");
            Hashtable<String, Long> indexerKeysTF = new Hashtable<String, Long>();
            ArrayList<String> wordTags = new ArrayList<String>();
            for (int i = 0; i < words.length; i++) {

                String pageWord = words[i];
                pageWord.toLowerCase();

                // 1- we remove special chars from the word before processing
                // and then check if it was a special we continue looping
                // if not we process
                String word = specialCharStemmer(pageWord);
                if (word.length() == 0 || stopWords.contains(word))
                    continue;

                // 2- we stem the word
                PorterStemmer ps = new PorterStemmer();
                word = ps.stemWord(word);

                // 3- we check if this word was indexed before
                if (indexer.containsKey(word)) {
                    // if well we just append the url to the list
                    if (!indexer.get(word).contains(url))
                        indexer.get(word).add(url);
                } else {
                    // if not we add the word and url to the table
                    ArrayList<String> temp = new ArrayList<>();
                    temp.add(url);
                    indexer.put(word, temp);
                }

                // 4-
                if (indexerKeysTF.containsKey(word)) {
                    indexerKeysTF.put(word, indexerKeysTF.get(word) + 1);
                } else {
                    indexerKeysTF.put(word, (long) 1);
                }
            }
            for (String key : indexerKeysTF.keySet()) {
                Document tempDoc = new Document();
                tempDoc.append("URL", url);
                tempDoc.append("Title", title);
                tempDoc.append("Description", desc);
                tempDoc.append("TF", indexerKeysTF.get(key));
                tempDoc.append("URL",url);
                tempDoc.append("Title",title);
                tempDoc.append("Description",desc);
                tempDoc.append("TF",indexerKeysTF.get(key));

                synchronized (this.docsKeyURLS) {
                    if (docsKeyURLS.get(key) == null) {
                        List temp = new ArrayList<Document>();
                        temp.add(tempDoc);
                        docsKeyURLS.put(key, temp);
                    } else {
                        docsKeyURLS.get(key).add(tempDoc);
                    }
                }
                //docsKeyURLS.notifyAll();
            }

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private boolean checkCursor(){
        try {
            synchronized(this.cursor){
                return cursor.hasNext();
            }
        } catch (Exception e) {
            //TODO: handle exception
            return false;
        }
    }

    private Document getCursor(){
        try {
            synchronized(this.cursor){
                return cursor.next();
            }
        } catch (Exception e) {
            //TODO: handle exception
            return null;
        }
    }

    private void InsertIntoDB(MongoDBManager dbManager) {
        try {
            dbManager = new MongoDBManager();
            dbManager.insertIntoIndexer2(this.docsKeyURLS);
            //dbManager.CloseConnection();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private void fillStopWords() {
        try {
            // we just read the txt file and store it in an array list
            Scanner In = new Scanner(new File("StopWords_dataSet\\English.txt"));
            while (In.hasNext()) {
                stopWords.add(In.next());
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private String specialCharStemmer(String preWord) {
        try {
            int i = 0;
            while (i < preWord.length() && (preWord.charAt(i) < 'A'
                    || (preWord.charAt(i) > 'Z' && preWord.charAt(i) < 'a') || preWord.charAt(i) > 'z'))
                i++;

            if (i == preWord.length()) {
                char[] empty = new char[0];
                return new String(empty);
            }

            int j = preWord.length() - 1;
            while (preWord.charAt(j) < 'A' || (preWord.charAt(j) > 'Z' && preWord.charAt(j) < 'a')
                    || preWord.charAt(j) > 'z')
                j--;

            char[] charArr = new char[j - i + 1];
            int l = i;
            for (int k = 0; k < j - i + 1; k++) {
                charArr[k] = preWord.charAt(l);
                l++;
            }

            return new String(charArr);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "";
        }
    }


    // private  void writeToFile(HashMap<String, ArrayList<String>> indexer) {
    //     try {
    //         PrintWriter out = new PrintWriter("index\\index.txt");
    //         for (String keyWord : indexer.keySet()) {
    //             out.write(keyWord + ':');
    //             for (int i = 0; i < indexer.get(keyWord).size(); i++) {
    //                 out.write(indexer.get(keyWord).get(i) + " --- ");
    //             }
    //             out.write('\n');
    //         }
    //         out.close();
    //     } catch (Exception ex) {
    //         System.out.println(ex.getMessage());
    //     }
    // }
}
