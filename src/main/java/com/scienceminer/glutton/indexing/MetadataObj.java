package com.scienceminer.glutton.indexing;

import java.util.*;

/** 
 * Object to be indexed for search 
 **/ 
public class MetadataObj {
    public String _id;
    public List<String> title;
    public String DOI;
    public String halId;
    public String first_author;
    public String author;
    public String first_page;
    public List<String> journal;
    public List<String> abbreviated_journal;
    public String volume;
    public String issue;
    public String year;
    public String bibliographic;
    public String query;
    public String type;

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("_id: ").append(_id).append(", ");
        builder.append("title: ").append(title.toString()).append(", ");
        builder.append("DOI: ").append(DOI).append(", ");
        builder.append("halId: ").append(halId).append(", ");
        builder.append("first_author: ").append(first_author).append(", ");
        builder.append("author: ").append(author).append(", ");
        builder.append("first_page: ").append(first_page).append(", ");
        builder.append("journal: ").append(journal.toString()).append(", ");
        builder.append("abbreviated_journal: ").append(abbreviated_journal.toString()).append(", ");
        builder.append("volume: ").append(volume).append(", ");
        builder.append("issue: ").append(issue).append(", ");
        builder.append("year: ").append(year).append(", ");
        builder.append("bibliographic: ").append(bibliographic).append(", ");
        builder.append("type: ").append(type);

        return builder.toString();
    }
}
