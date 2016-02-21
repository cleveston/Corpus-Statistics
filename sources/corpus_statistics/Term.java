 package corpus_statistics;

import java.io.Serializable;
import java.util.HashMap;

//The Term Class
public class Term implements Serializable{

    //The private variables
    private String term;
    private final HashMap<Integer, Integer> documents = new HashMap<>();

    //The Constructor Method
    public Term(String term, Integer document) {

        //Initialize the object
        this.term = term.toLowerCase().trim();
        this.documents.put(document, 1);

    }

    //Add new occurence
    public void addOccurence(Integer document) {

        //If the document is not in the list
        if (this.documents.containsKey(document)) {

            //Get the previous value
            Integer newCounter = this.documents.get(document) + 1;

            //Update the value
            this.documents.replace(document, newCounter);
        } else {
            //Add the new value
            this.documents.put(document, 1);

        }

    }

    //Set the term
    public void setTerm(String term) {

        this.term = term;

    }

    //Get the term
    public String getTerm() {

        return this.term;

    }

    //Get the frequency
    public int getFrequency() {

        int frequency = 0;

        //Sum the frequency in each document
        return this.documents.values().stream().map((f) -> f).reduce(frequency, Integer::sum);

    }

    //Get the documents
    public HashMap getDocuments() {

        return this.documents;

    }
}
