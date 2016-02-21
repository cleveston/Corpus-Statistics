package corpus_statistics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.*;
import static java.lang.Integer.max;
import java.util.regex.*;

//SpellingCorrector Class
public class SpellingCorrector {

    // word to count map - how may times a word is present - or a weight attached to a word
    private static Map<String, Integer> dictionary = new HashMap<String, Integer>();

    //Max word
    private int maxWord = 0;

    private float totalWords = 0;

    //The Constructor method
    public SpellingCorrector(String directory) throws IOException {

        //Open the directory
        File path = new File(directory);

        File[] files = path.listFiles();

        //For each file in the directory
        for (File file : files) {

            BufferedReader in = new BufferedReader(new FileReader(file));

            Pattern p = Pattern.compile("\\w+");

            for (String temp = ""; temp != null; temp = in.readLine()) {

                Matcher m = p.matcher(temp.toLowerCase());
                while (m.find()) {
                    dictionary.put((temp = m.group()), dictionary.containsKey(temp) ? dictionary.get(temp) + 1 : 1);
                }

            }

            in.close();
        }

        //Get the max word
        maxWord = Collections.max(dictionary.keySet()).length();

        for (Integer integer : dictionary.values()) {
            totalWords += integer.longValue();
        }

    }

    //Correct the word
    public String correct(String word) {

        //Check if it is on the dictionary
        if (dictionary.containsKey(word)) {
            return word;
        }

        // getting all possible edits of input word
        List<String> edits = edits(word);

        // Here we can either iterate through list of edits and find the 1st word that is in dictionary and return.
        // Or we can go to below logic to return the word with most weight (that we would have already stored in dictionary map)
        // map to hold the possible matches
        Map<Integer, String> candidates = new HashMap<Integer, String>();

        // keep checking the dictionary and populate the possible matches
        // it stores the count (or weight) of word and the actual word
        for (String s : edits) {
            if (dictionary.containsKey(s)) {
                candidates.put(dictionary.get(s), s);
            }
        }

        // one we have found possible matches - we want to return most popular (most weight) word
        if (candidates.size() > 0) {
            return candidates.get(Collections.max(candidates.keySet()));
        }

        // If none matched.
        // We will pick every word from edits and again do edits (using same previous logic) on that to see if anything matches
        // We don't do recursion here because we don't the loop to continue infinitely if none matches
        // If even after doing edits of edits, we don't find the correct word - then return.
        for (String s : edits) {

            List<String> newEdits = edits(s);
            for (String ns : newEdits) {
                if (dictionary.containsKey(ns)) {
                    candidates.put(dictionary.get(ns), ns);
                }
            }
        }
        if (candidates.size() > 0) {
            return candidates.get(Collections.max(candidates.keySet()));
        } else {
            return word;
        }
    }

    // Word EDITS
    // Getting all possible corrections c of a given word w. 
    // It is the edit distance between two words: the number of edits it would take to turn one into the other
    private static ArrayList<String> edits(String word) {

        if (word == null || word.isEmpty()) {
            return null;
        }

        ArrayList<String> list = new ArrayList<String>();

        String w = null;

        // deletes (remove one letter)
        for (int i = 0; i < word.length(); i++) {
            w = word.substring(0, i) + word.substring(i + 1); // ith word skipped
            list.add(w);
        }

        // transpose (swap adjacent letters)
        // note here i is less than word.length() - 1
        for (int i = 0; i < word.length() - 1; i++) { // < w.len -1 :: -1 because we swapped last 2 elements in go.
            w = word.substring(0, i) + word.charAt(i + 1) + word.charAt(i) + word.substring(i + 2); // swapping ith and i+1'th char
            list.add(w);
        }

        // replace (change one letter to another)
        for (int i = 0; i < word.length(); i++) {
            for (char c = 'a'; c <= 'z'; c++) {
                w = word.substring(0, i) + c + word.substring(i + 1); // replacing ith char with all possible alphabets
                list.add(w);
            }
        }

        // insert (add a letter)
        // note here i is less than and EQUAL to
        for (int i = 0; i <= word.length(); i++) { // <= because we want to insert the new char at the end as well
            for (char c = 'a'; c <= 'z'; c++) {
                w = word.substring(0, i) + c + word.substring(i); // inserting new char at ith position
                list.add(w);
            }
        }

        return list;
    }

    //Separate the words
    public ArrayList<String> verbatim(String text) {

        ArrayList<Float> probs = new ArrayList();
        ArrayList<Integer> lasts = new ArrayList();

        ArrayList<String> words = new ArrayList();

        probs.add(1.0F);
        lasts.add(0);

        for (int i = 1; i < text.length() + 1; i++) {

            float prob_k = 0.0F;
            Integer k = 0;

            ArrayList<Float[]> v1 = new ArrayList();

            for (int j = max(0, i - maxWord); j < i; j++) {

                Float p1 = probs.get(j) * word_prob(text.substring(j, i));

                Float p2 = Float.valueOf(j);

                Float[] pp = {p1, p2};

                v1.add(pp);

            }

            int newId = 0;
            float max = 0F;

            //Find the biggest value
            for (int id = 0; id < v1.size(); id++) {

                Float[] data = v1.get(id);

                if (max < data[0]) {

                    max = data[0];

                    newId = id;

                }

            }

            Float[] finalValues = v1.get(newId);

            prob_k = finalValues[0];

            k = Math.round(finalValues[1]);

            probs.add(prob_k);
            lasts.add(k);

        }

        int i = text.length();

        while (0 < i) {

            int cutInitial = lasts.get(i);

            words.add(text.substring(cutInitial, i));
            i = cutInitial;

        }

        //Reverse arraylist
        Collections.reverse(words);

        return words;

    }

    //Get the word probability
    private Float word_prob(String word) {

        Integer i = dictionary.get(word);

        if (i == null) {
            return 0 / totalWords;
        } else {
            return i / totalWords;
        }
    }

}
