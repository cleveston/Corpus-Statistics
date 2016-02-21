package corpus_statistics;

//The Paragraph Class
public class Paragraph {

    private final int id;
    private String content;

    //Constructor Method
    public Paragraph(int id) {

        this.id = id;

    }

    //Set the content
    public void setContent(String content) {

        this.content = content;
    }

    //Get the id
    public int getId() {

        return this.id;
    }

    //Get the content
    public String getContent() {

        return this.content;
    }

}
