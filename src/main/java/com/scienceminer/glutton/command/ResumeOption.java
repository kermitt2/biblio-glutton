package com.scienceminer.glutton.command;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

/**
 * The option every loading command takes to start over. Without it a command run again carries
 * on from where the earlier run stopped, see {@link com.scienceminer.glutton.storage.LoadProgress}.
 */
final class ResumeOption {

    private static final String FRESH = "fresh";

    private ResumeOption() {
    }

    static void addTo(Subparser subparser) {
        subparser.addArgument("--fresh")
                .dest(FRESH)
                .action(Arguments.storeTrue())
                .help("Load everything again, instead of carrying on from where an earlier run "
                        + "that did not complete stopped");
    }

    static boolean isFresh(Namespace namespace) {
        return Boolean.TRUE.equals(namespace.getBoolean(FRESH));
    }
}
