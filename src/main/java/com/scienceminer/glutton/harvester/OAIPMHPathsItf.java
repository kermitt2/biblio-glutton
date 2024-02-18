package com.scienceminer.glutton.harvester;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author Achraf
 */

/* to be reviewed */

public interface OAIPMHPathsItf {
    public final static String ListRecordsElement = "ListRecords";
    public final static String RecordElement = "record";
    public final static String TeiElement = "metadata";
    public final static String IdElementPath = "text/body/listBibl/biblFull/publicationStmt/idno[@type='halId']";
    public final static String ResumptionToken = "resumptionToken";
    public final static String AnnexesUrlsElement = "text/body/listBibl/biblFull/editionStmt/edition[@type='current']/ref[@type='annex']";
    public final static String FileElement = "text/body/listBibl/biblFull/editionStmt/edition[@type='current']/ref[@type='file'][1]";
    public final static String EditionElement = "text/body/listBibl/biblFull/editionStmt/edition[@type='current']";
    public final static String RefPATH = "text/body/listBibl/biblFull/publicationStmt/idno[@type='halRef']";
    public final static String DoiPATH = "text/body/listBibl/biblFull/sourceDesc/biblStruct/idno[@type='doi']";
    public final static String PublicationTypePATH = "text/body/listBibl/biblFull/profileDesc/textClass/classCode[@scheme='halTypology']";
    public final static String DomainsPATH = "text/body/listBibl/biblFull/profileDesc/textClass/classCode[@scheme='halDomain']";

    /* note: these are HAL specific types */    
    enum ConsideredTypes {
        ART, COMM, OUV, POSTER, DOUV, PATENT, REPORT, THESE, HDR, LECTURE, COUV, OTHER, UNDEFINED  //IMG, VIDEO, AUDIOS, SON, MAP
    };
}
