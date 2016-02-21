package corpus_statistics;

import com.inamik.utils.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.*;

//The Corpus_Statistics Class
public class Corpus_Statistics {

    //The input file
    private static final String inputFile = "caesar-polo-esau.txt";

    //Create a stack for the paragraphs
    private static final Stack<Paragraph> paragraphs = new Stack();

    //Create a stack for the terms
    private static final Stack<Term> termStack = new Stack();

    //Total Paragraphs
    private static int totalParagraphs;

    //The Spelling Corrector
    private static SpellingCorrector spellingCorrector;

    //Temporary Index
    private static final HashMap<String, Term> temporaryIndex = new HashMap<>();

    //Define Console Scanner
    private static final Scanner console = new Scanner(System.in);

    //Create the thread workers
    private static final Thread[] threadWorker = new Thread[4];

    //Create the mutex for the stack
    private static Semaphore mutexStack = new Semaphore(1);

    //Create the mutex for the invertedIndex
    private static Semaphore mutexInvertedIndex = new Semaphore(1);

    //Sort term by frequency
    private static List<Term> invertedIndex;

    //The Main Method
    public static void main(String[] args) {

        System.out.println("Starting Retrieval System");

        try {

            //Load index
            loadIndex();

        } catch (Exception exception) {

            //Create the startups threads
            Thread threadSpelling = new Thread(new spellingStartup());
            Thread threadFileParsing = new Thread(new fileParsingStartup());

            System.out.println("--------------------------------");
            System.out.println("Retrieving Paragraphs and Building Dictionary");

            //Start threads
            threadSpelling.start();
            threadFileParsing.start();

            //Wait for the join
            try {
                threadFileParsing.join();
                threadSpelling.join();

            } catch (InterruptedException ex) {

            }

            //Sum the total paragraphs
            totalParagraphs = paragraphs.size();

            System.out.println("--------------------------------");
            System.out.println("Retrieving Words");

            int i = 0;

            //Create the workers
            for (Thread thread : threadWorker) {

                //Create the workers
                thread = new Thread(new worker());

                //Add thread to the list
                threadWorker[i++] = thread;

                //Start Thread
                thread.start();

            }

            i = 0;

            //Wait for the join
            try {
                for (Thread thread : threadWorker) {

                    //Add thread to the list
                    threadWorker[i++].join();

                }
            } catch (InterruptedException ex) {

            }

            //Put entries on the stack
            termStack.addAll(temporaryIndex.values());
            temporaryIndex.clear();

            System.out.println("--------------------------------");
            System.out.println("Correcting Words");

            //Launch Thread Monitor
            Thread threadMonitor = new Thread(new monitor());
            threadMonitor.start();

            i = 0;

            //Create the workers
            for (Thread thread : threadWorker) {

                //Create the workers
                thread = new Thread(new SpellingCorrection());

                //Add thread to the list
                threadWorker[i++] = thread;

                //Start Thread
                thread.start();

            }

            i = 0;

            //Wait for the join
            try {
                for (Thread thread : threadWorker) {

                    //Add thread to the list
                    threadWorker[i++].join();

                }
            } catch (InterruptedException ex) {

            }

            //Populate the array
            invertedIndex = new ArrayList<>(temporaryIndex.values());

            //Sort by frequency
            Collections.sort(invertedIndex, (Term t1, Term t2) -> t2.getFrequency() - t1.getFrequency());

            //Save the index to the disk
            saveIndex();

        }

        //Show statistics
        showStatistics();

    }

    //Class implements thread to startup the spelling corrector
    private static class spellingStartup implements Runnable {

        //Run Method
        public void run() {

            try {

                //Create the Spelling Corrector
                spellingCorrector = new SpellingCorrector("database_words");

            } catch (Exception exception) {

                System.out.println("Error with dictionary file.");
                System.exit(1);
            }

        }
    }

    //Class implements thread monitor
    private static class monitor implements Runnable {

        long wordsInitial = 0;
        long wordsFinal = 0;

        //Run Method
        public void run() {

            wordsInitial = termStack.size();

            while (wordsInitial > 0) {

                try {

                    //Initial time
                    long startTime = System.currentTimeMillis();

                    //Wait for 3seg
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ex) {

                    }

                    wordsFinal = termStack.size();

                    //Final time
                    long finalTime = (System.currentTimeMillis() - startTime) / 1000;

                    System.out.println("Processed Words: " + wordsFinal + " - Words/sec: " + (wordsInitial - wordsFinal) / finalTime);

                    wordsInitial = wordsFinal;

                } catch (Exception e) {

                    //Stop the thread
                    Thread.currentThread().interrupt();
                }

            }

        }
    }

    //Class implements the file parsing
    private static class fileParsingStartup implements Runnable {

        //Run Method
        public void run() {

            BufferedReader in;

            try {

                //Read file
                in = new BufferedReader(new FileReader(inputFile));

                //The initial pattern
                Pattern begin = Pattern.compile("<P ID=(\\d+)>", Pattern.CASE_INSENSITIVE);

                //The final pattern
                Pattern end = Pattern.compile("</P>", Pattern.CASE_INSENSITIVE);

                String content = "";

                Paragraph paragraph = null;

                //For each line
                for (String temp = ""; temp != null; temp = in.readLine()) {

                    //Try to match the line with the patterns
                    Matcher m = begin.matcher(temp);
                    Matcher o = end.matcher(temp);

                    //Try to find matches
                    if (m.find()) {

                        //Parse the id from the string
                        int id = Integer.parseInt(m.group().replaceAll("\\D+", ""));

                        //Create a new paragraph
                        paragraph = new Paragraph(id);

                    } else if (o.find()) {

                        //Set the context to the paragraph
                        paragraph.setContent(content);

                        //Add the new paragraph to the stack
                        paragraphs.push(paragraph);

                        //Reset variable
                        content = "";

                    } else {

                        //Concat the content
                        content += temp;

                    }

                }

                //Close the file
                in.close();

            } catch (Exception ex) {
            }

        }
    }

    //Class implements the workers
    private static class worker implements Runnable {

        //Run Method
        public void run() {

            Paragraph paragraph = null;

            try {
                //Acquire mutex
                mutexStack.acquire();
            } catch (InterruptedException ex) {

            }

            //While it has elements in the stack
            while (!paragraphs.empty()) {

                //Get the paragraph
                paragraph = paragraphs.pop();

                //Release mutex
                mutexStack.release();

                //Remove characteres that are non-alphanumerical
                String temporary = paragraph.getContent().replaceAll("[^a-zA-Z0-9 ]", "");

                String[] strings = temporary.split(" ");

                //For each occurrence
                for (String str : strings) {

                    String correctWord = str.toLowerCase().trim();

                    //Ignore words with one letter
                    if (correctWord.length() == 1 && (!correctWord.equals("i") || !correctWord.equals("a"))) {
                        continue;
                    }

                    //If the string is not empty
                    if (!correctWord.isEmpty()) {

                        try {

                            //Acquire mutex
                            mutexInvertedIndex.acquire();

                            Term term = temporaryIndex.get(correctWord);

                            //Put it it not exist                       
                            if (term != null) {

                                term.addOccurence(paragraph.getId());
                                temporaryIndex.replace(correctWord, term);

                            } else {

                                //Create the term
                                term = new Term(correctWord, paragraph.getId());

                                temporaryIndex.put(correctWord, term);
                            }

                        } catch (InterruptedException exception) {

                        } finally {
                            mutexInvertedIndex.release();
                        }

                    }
                }

            }

        }

    }

    //Correct entries
    private static class SpellingCorrection implements Runnable {

        public void run() {

            Term term = null;

            //While it has elements in the stack
            while (!termStack.empty()) {

                //Get the paragraph
                term = termStack.pop();

                //Correct word
                String correctWord = term.getTerm();//spellingCorrector.correct(term.getTerm());

                try {

                    //Acquire mutex
                    mutexInvertedIndex.acquire();

                    //Get the term
                    Term newTerm = temporaryIndex.get(correctWord);

                    //Verify if it exists
                    if (newTerm != null) {

                        HashMap<Integer, Integer> documents = term.getDocuments();

                        documents.keySet().stream().forEach((doc) -> {
                            newTerm.addOccurence(doc);
                        });

                        //Replace the current word
                        temporaryIndex.replace(correctWord, newTerm);

                    } else {

                        //If term has changed
                        if (!term.getTerm().equals(correctWord)) {

                            term.setTerm(correctWord);

                        }

                        //Insert the new term
                        temporaryIndex.put(correctWord, term);

                    }

                } catch (InterruptedException exception) {

                } finally {
                    mutexInvertedIndex.release();
                }

            }
        }

    }

    //Show Statistics
    private static void showStatistics() {

        System.out.println("CORPUS STATISTICS");
        System.out.println("----------------------------------------");

        //Terms that appers just in one document
        List<Term> justOneDocument = new ArrayList<>();

        //Total Words
        int totalWords = 0;

        for (Term t : invertedIndex) {

            totalWords += t.getFrequency();

            //If term is found in just one document
            if (t.getFrequency() == 1) {

                //Add term to the array
                justOneDocument.add(t);
            }

        }

        System.out.println("Total Paragraphs: " + totalParagraphs);
        System.out.println("Total Words: " + totalWords);
        System.out.println("Unique Words: " + invertedIndex.size());

        System.out.println("----------------------------------------");

        while (true) {

            //Create the header for the table
            TableFormatter tf = new SimpleTableFormatter(true)
                    .nextRow()
                    .nextCell(TableFormatter.ALIGN_CENTER, TableFormatter.VALIGN_CENTER)
                    .addLine("Rank")
                    .nextCell(TableFormatter.ALIGN_CENTER, TableFormatter.VALIGN_CENTER)
                    .addLine("Term")
                    .nextCell(TableFormatter.ALIGN_CENTER, TableFormatter.VALIGN_CENTER)
                    .addLine("Frequency")
                    .nextCell(TableFormatter.ALIGN_CENTER, TableFormatter.VALIGN_CENTER)
                    .addLine("N Documents");

            System.out.println();
            System.out.println("---------- MENU ------------");
            System.out.println("1 - 20 Most Common Words ");
            System.out.println("2 - 100 Most Common Words ");
            System.out.println("3 - 500 Most Common Words ");
            System.out.println("4 - 1000 Most Common Words ");
            System.out.println("5 - Unique Terms ");
            System.out.println("6 - Search Posting List ");
            System.out.println("7 - Search Frequency ");
            System.out.println("8 - Terminal");
            System.out.print("Please enter your option: ");

            //Get user selection
            String pathType = console.nextLine().toLowerCase();

            //Verify if the path plan changed
            switch (pathType) {
                case "1":
                    showCommonTerms(20, tf);
                    break;
                case "2":
                    showCommonTerms(100, tf);
                    break;
                case "3":
                    showCommonTerms(500, tf);
                    break;
                case "4":
                    showCommonTerms(1000, tf);
                    break;
                case "5":
                    //Show the unique terms
                    justOneDocument.stream().forEach((t) -> {
                        System.out.println(t.getTerm());
                    });
                    break;
                case "6":
                    //Show postings list
                    showPostings();
                    break;
                case "7":
                    //Show document frequency
                    showFrequency();
                    break;
                case "8":
                    pathType = console.nextLine().toLowerCase();
                    ArrayList<String> result = spellingCorrector.verbatim(pathType);
                    String output = result.stream().map((str) -> str + " ").reduce("", String::concat);
                    System.out.println();
                    System.out.println(output);
                    System.out.println();
                    break;
                default:
                    break;
            }

            System.out.println();
            System.out.println("--------- Enter to Continue --------");
            console.nextLine();

        }

    }

    //Show the document posting list
    private static void showPostings() {

        //Create the header for the table
        TableFormatter tf = new SimpleTableFormatter(true)
                .nextRow()
                .nextCell(TableFormatter.ALIGN_CENTER, TableFormatter.VALIGN_CENTER)
                .addLine("Document")
                .nextCell(TableFormatter.ALIGN_CENTER, TableFormatter.VALIGN_CENTER)
                .addLine("Frequency");

        System.out.print("Enter the word: ");
        //Get the word
        final String command = console.nextLine().toLowerCase();

        Term term;

        try {

            //Filter the frequency
            term = invertedIndex.stream().filter(a -> a.getTerm().equals(command)).findFirst().get();

            term.getDocuments().keySet().stream().forEach((key) -> {
                tf.nextRow()
                        .nextCell()
                        .addLine(String.valueOf(key))
                        .nextCell(TableFormatter.ALIGN_CENTER, TableFormatter.VALIGN_CENTER)
                        .addLine(String.valueOf(term.getDocuments().get(key)));
            });
        } catch (Exception exception) {

        }

        String[] table = tf.getFormattedTable();

        for (int o = 0, sizeTable = table.length; o < sizeTable; o++) {

            System.out.println(table[o]);
        }

    }

    //Show the document frequency
    private static void showFrequency() {

        System.out.print("Enter the word: ");
        //Get the word
        final String command = console.nextLine().toLowerCase();

        int freq = 0;

        try {

            //Filter the frequency
            freq = invertedIndex.stream().filter(a -> a.getTerm().equals(command)).findFirst().get().getFrequency();

        } catch (Exception exception) {

        }

        //Create the header for the table
        TableFormatter tf = new SimpleTableFormatter(true)
                .nextRow()
                .nextCell(TableFormatter.ALIGN_CENTER, TableFormatter.VALIGN_CENTER)
                .addLine("Term")
                .nextCell(TableFormatter.ALIGN_CENTER, TableFormatter.VALIGN_CENTER)
                .addLine("Frequency")
                .nextRow()
                .nextCell()
                .addLine(command)
                .nextCell(TableFormatter.ALIGN_CENTER, TableFormatter.VALIGN_CENTER)
                .addLine(String.valueOf(freq));

        String[] table = tf.getFormattedTable();

        for (int o = 0, sizeTable = table.length; o < sizeTable; o++) {

            System.out.println(table[o]);
        }
    }

    //Show the most common terms
    private static void showCommonTerms(int size, TableFormatter tf) {

        //Get the 100 msot frequent
        List<Term> mostFrequent = (List) invertedIndex.subList(0, invertedIndex.size() > size ? size : invertedIndex.size());

        //Rank
        int rank = 0;

        //For each term
        for (Term t : mostFrequent) {

            //Get the documents
            HashMap<Integer, Integer> documents = t.getDocuments();

            //Add data  to the table
            tf.nextRow()
                    .nextCell()
                    .addLine(String.valueOf(++rank))
                    .nextCell()
                    .addLine(t.getTerm())
                    .nextCell(TableFormatter.ALIGN_CENTER, TableFormatter.VALIGN_CENTER)
                    .addLine(String.valueOf(t.getFrequency()))
                    .nextCell(TableFormatter.ALIGN_CENTER, TableFormatter.VALIGN_CENTER)
                    .addLine(String.valueOf(documents.size()));

        }

        String[] table = tf.getFormattedTable();

        System.out.println("----------------------------------------");
        System.out.println(size + " MOST COMMON WORDS");

        for (int o = 0, sizeTable = table.length; o < sizeTable; o++) {
            System.out.println(table[o]);
        }

    }

    //Save the invertedIndex to the disk
    private static void saveIndex() {

        try {

            //Open the file
            ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream("invertedIndex.bin"));

            //Write the index
            os.writeObject(invertedIndex);

            //Close the file
            os.close();

        } catch (Exception exception) {
            System.out.println(exception.getMessage());
        }
    }

    //Load index
    private static void loadIndex() throws Exception {
        try {
            //Open the file
            FileInputStream fileIn = new FileInputStream("invertedIndex.bin");

            //Read the file
            ObjectInputStream in = new ObjectInputStream(fileIn);

            //Set the invertedIndex
            invertedIndex = (List<Term>) in.readObject();
            in.close();
            fileIn.close();
        } catch (Exception c) {
            throw new Exception();
        }

    }
}
