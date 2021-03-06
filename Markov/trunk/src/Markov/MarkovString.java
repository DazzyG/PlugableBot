package Markov;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 
 * @author A.Cassidy (a.cassidy@bytz.co.uk)
 */
public class MarkovString implements Runnable {

    private LinkedBlockingQueue<String> learnQueue;
    private MarkovDatabase db;
    private boolean isShuttingDown = false;

    // private static final String REGEX =
    // "[a-z][a-z]*\\.[a-z][a-z]*(\\.[a-z][a-z]*)*|[a-z]+((-|')[a-z]+)*";
    private static final String REGEX = "((ht|f)tp(s?)\\:\\/\\/|~/|/)?([\\w]+:\\w+@)?([a-zA-Z]{1}([\\w\\-]+\\.)+([\\w]{2,5}))(:[\\d]{1,5})?((/?\\w+/)+|/?)(\\w+\\.[\\w]{3,4})?((\\?\\w+=\\w+)?(&\\w+=\\w+)*)" // urls
            + "|" + "([a-z0-9]+([-:|'][a-z0-9]+)*)" // word optionaly seperated
            // with -:|' i.e. hello
            // hello-world
            // hello-cruel-world
            + "|" + "((:\\))|(:\\()|(\\(:)|(\\):)|(:p))"; // Smilies
    // :), :(,
    // ):, (:,
    // :p
    //+ "|" + "!!!|!|?"; // bob learn him some punctuation
    private Pattern p = Pattern.compile(REGEX);

    public MarkovString(LinkedBlockingQueue<String> learnQueue,
            MarkovDatabase db) {
        this.learnQueue = learnQueue;
        this.db = db;
    }

    private ArrayList<String> split(String sentence) {
        ArrayList<String> strings = new ArrayList<String>();
        java.util.regex.Matcher m = p.matcher(sentence);
        while (m.find()) {
            strings.add(m.group());
        }
        return strings;
    }

    public void Learn(String sentence) {
        Logger.getLogger(MarkovString.class.getName()).log(Level.INFO, "Learning Start");
        MarkovNode n, parent;
        parent = db.getNode("[");
        ArrayList<String> words = split(sentence.toLowerCase());
        for (String word : words) {
            // if the word is blank, ignore it
            if (word.trim().equals("")) {
                continue;
            }
            // get the word from the database if we already have it
            MarkovNode query = db.getNode(word);
            if (query == null) {
                // if we dont have it, add it
                n = new MarkovNode(word);
                    db.add(n);

            } else {
                n = query;
            }

            // add to the parent node
            MarkovLink link = db.getLink(new MarkovLink(parent, n));
            
            if (link == null) {
            	link = new MarkovLink(parent, n);
            	db.add(link);
            } else {
            	link.setWeight(link.getWeight() + 1);
            }
            
            // move to the next node
            parent = n;
        }

        // not sure why this'd ever be null ...
        if (parent != null) {
            // add the end marker at the end
        	MarkovNode end = db.getNode("]");
        	MarkovLink link = db.getLink(new MarkovLink(parent, end));
            if (link == null) {
            	link = new MarkovLink(parent, end);
            	db.add(link);
            } else {
            	link.setWeight(link.getWeight() + 1);
            }
        }
        Logger.getLogger(MarkovString.class.getName()).log(Level.INFO, "Learning End");
    }

    public void shutdown() {
        Logger.getLogger(MarkovString.class.getName()).log(Level.INFO, "Markov Learn Thread Shutdown singalled");
        isShuttingDown = true;
    }

    public void run() {
        Logger.getLogger(MarkovString.class.getName()).log(Level.INFO, "Markov Learn Thread Running");
        while (!isShuttingDown || learnQueue.peek() != null) {
            try {
                String s = learnQueue.take();
                if (!s.equals("")) {
                    Learn(s);
                    if (isShuttingDown)
                            Logger.getLogger(MarkovDatabase.class.getName()).log(Level.INFO, "Learn Items Left: " + learnQueue.size());
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(MarkovString.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        Logger.getLogger(MarkovString.class.getName()).log(Level.INFO, "Markov Learn Thread Finished");
    }
}
