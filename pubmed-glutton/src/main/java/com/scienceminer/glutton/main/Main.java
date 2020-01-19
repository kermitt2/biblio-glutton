package com.scienceminer.glutton.main;

import org.apache.commons.lang3.StringUtils;

import com.scienceminer.glutton.utilities.GluttonConfig;
import com.scienceminer.glutton.data.db.KBEnvironment;
import com.scienceminer.glutton.data.db.KBStagingEnvironment;
import com.scienceminer.glutton.data.db.KBServiceEnvironment;
import com.scienceminer.glutton.ingestion.IstexPubMedMapper;
import com.scienceminer.glutton.ingestion.PubMedIndexer;
import com.scienceminer.glutton.export.PubMedExporter;
import com.scienceminer.glutton.export.PubMedExporter.Format;
//import com.scienceminer.glutton.ingestion.CoreIngester;

import java.io.*;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * The entrance point for starting the tool from command line
 *
 * @author Patrice Lopez
 */
public class Main {

    private static List<String> availableCommands = Arrays.asList("istexPMID", "pubmed", "pubmedExport");

    /**
     * Arguments of the command.
     */
    private static MainArgs gbdArgs;

    /**
     * Build the path to grobid.properties from the path to grobid-home.
     *
     * @param pPath2GbdHome The path to Grobid home.
     * @return the path to grobid.properties.
     */
    protected final static String getPath2GbdProperties(final String pPath2GbdHome) {
        return pPath2GbdHome + File.separator + "config" + File.separator + "grobid.properties";
    }

    /**
     * @return String to display for help.
     */
    protected static String getHelp() {
        final StringBuilder help = new StringBuilder();
        help.append("HELP biblio glutton\n");
        help.append("-h: displays help\n");
        help.append("-tdata: gives the path to an input directory - if required.\n");
		help.append("-out: directory path for the result files - if required\n");
        help.append("-exe: gives the command to execute. The value should be one of these:\n");
        help.append("\t" + availableCommands + "\n");
        return help.toString();
    }

    /**
     * Process command given the args.
     *
     * @param pArgs The arguments given to the batch.
     */
    protected static boolean processArgs(final String[] pArgs) {
        boolean result = true;
        if (pArgs.length == 0) {
            System.out.println(getHelp());
            result = false;
        } 
		else {
            String currArg;
            for (int i = 0; i < pArgs.length; i++) {
                currArg = pArgs[i];
                if (currArg.equals("-h")) {
                    System.out.println(getHelp());
                    result = false;
                    break;
                }
                if (currArg.equals("-tdata")) {
                    if (pArgs[i + 1] != null) {
                        gbdArgs.setPathInputDirectory(pArgs[i + 1]);
                    }
                    i++;
                    continue;
                }
                if (currArg.equals("-out")) {
                    if (pArgs[i + 1] != null) {
                        gbdArgs.setResultDirectoryPath(pArgs[i + 1]);
                    }
                    i++;
                    continue;
                }
                if (currArg.equals("-exe")) {
                    final String command = pArgs[i + 1];
                    if (availableCommands.contains(command)) {
                        gbdArgs.setProcessMethodName(command);
                        i++;
                        continue;
                    } else {
                        System.err.println("-exe value should be one value from this list: " + availableCommands);
                        result = false;
                        break;
                    }
                }
                if (currArg.equals("-addMeSH")) {
                    gbdArgs.setAddMeSH(true);
                    continue;
                }
                /*if (currArg.equals("-n")) {
                    if (pArgs[i + 1] != null) {
						String nb = pArgs[i + 1];
						int nbThreads = 1;
						try {
							nbThreads = Integer.parseInt(nb);
						}
						catch(Exception e) {
							System.err.println("-n value should be an integer");
						}
                        gbdArgs.setNbThreads(nbThreads);
                    }
                    i++;
                    continue;
                }*/
            }
        }
        return result;
    }

    /**
     * Starts nerd from command line using the following parameters:
     *
     * @param args The arguments
     */
    public static void main(final String[] args) throws Exception {
        gbdArgs = new MainArgs();

        File confFile = new File("config/glutton.yaml");
        if (!confFile.canRead()) {
            System.out.println("'" + args[0] + "' cannot be read");
            System.exit(1);
        }
        
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        GluttonConfig conf = mapper.readValue(confFile, GluttonConfig.class);

        if (processArgs(args) && (gbdArgs.getProcessMethodName() != null)) {

            if (gbdArgs.getProcessMethodName().equals("istexpmid")) {
                System.out.println("Mapping ISTEX ID to PMID");
                KBStagingEnvironment env = null;
                try {
                    env = new KBStagingEnvironment(conf);
                    env.buildEnvironment(false);

                    IstexPubMedMapper istex = new IstexPubMedMapper(env);
                    istex.addPMID(gbdArgs.getResultDirectoryPath(), gbdArgs.getAddMeSH());
                } finally {
                    if (env != null)  
                        env.close();
                }
            } else if (gbdArgs.getProcessMethodName().equals("pubmed")) {
                System.out.println("Indexing PubMed data with indexing of MeSH classification");
                KBStagingEnvironment env = null;
                try {
                    env = new KBStagingEnvironment(conf);
                    env.buildEnvironment(false);

                    PubMedIndexer pubmed = new PubMedIndexer(env, conf);
                    pubmed.process();
                } finally {
                    if (env != null)  
                        env.close();
                }
            } else if (gbdArgs.getProcessMethodName().equals("pubmedexport")) {
                System.out.println("Export PubMed data based on MeSH classification");
                KBStagingEnvironment env = null;
                try {
                    env = new KBStagingEnvironment(conf);
                    env.buildEnvironment(false);

                    PubMedExporter pubmed = new PubMedExporter(env, conf);
                    pubmed.export(gbdArgs.getPathInputDirectory(), gbdArgs.getResultDirectoryPath(), Format.CSV);
                } finally {
                    if (env != null)  
                        env.close();
                }
            } 


            /*else if (gbdArgs.getProcessMethodName().equals("coreharvesting")) {
                System.out.println("Harvesting CORE data");
                KBStagingEnvironment env1 = null;
                KBServiceEnvironment env2 = null;
                try {
                    env1 = new KBStagingEnvironment(conf);
                    env1.buildEnvironment(false);

                    env2 = new KBServiceEnvironment(conf);
                    env2.buildEnvironment(false);

                    CoreIngester coreIngester = new CoreIngester(env1, env2);
                
                    // repositories
                    int nb = coreIngester.fillRepositoryDb();
                    System.out.println(nb + " repositories added");

                    // biblio entries
                    //nb = coreIngester.harvest();
                    //System.out.println(nb + " entries added");
                } finally {
                    if (env1 != null)
                        env1.close();
                    if (env2 != null)
                        env2.close();
                }
            }*/
        }
    }

}
