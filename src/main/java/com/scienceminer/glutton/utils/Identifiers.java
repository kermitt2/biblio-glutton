package com.scienceminer.glutton.utils;

import com.scienceminer.glutton.exception.ServiceException;

import java.util.Locale;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Checks and tidies the identifiers a request comes with before they are looked up.
 *
 * Every identifier ends up as an LMDB key, which has a size limit, so an identifier that is not
 * one at all (a whole Google Scholar URL sent as a DOI, say) used to overflow the key buffer and
 * come back as a server error with a stack trace. Here it is refused with a 400 saying what was
 * expected. On the way, the forms an identifier is commonly pasted in are accepted: a DOI as a
 * doi.org URL or with a {@code doi:} prefix, a PMC identifier with or without {@code PMC}, a HAL
 * identifier as its URL.
 */
public final class Identifiers {

    /** Well under the LMDB key limit once serialised, and far beyond any real identifier. */
    static final int MAX_LENGTH = 200;
    /** How much of a refused value is echoed back, so a huge input is not sent back in full. */
    private static final int ECHO_LENGTH = 60;

    private static final String[] DOI_PREFIXES = {
            "https://doi.org/", "http://doi.org/", "https://dx.doi.org/", "http://dx.doi.org/",
            "doi.org/", "dx.doi.org/", "doi:"
    };
    private static final String[] HAL_PREFIXES = {
            "https://hal.science/", "http://hal.science/",
            "https://hal.archives-ouvertes.fr/", "http://hal.archives-ouvertes.fr/",
            "https://inria.hal.science/", "https://theses.hal.science/"
    };

    private static final Pattern DOI = Pattern.compile("10\\.\\d{4,9}/\\S+");
    private static final Pattern DIGITS = Pattern.compile("\\d{1,12}");
    private static final Pattern PMC = Pattern.compile("(?i)PMC\\d{1,12}");
    private static final Pattern HAL = Pattern.compile("[a-z0-9]+-[a-z0-9-]+(v\\d+)?");
    private static final Pattern ISTEX = Pattern.compile("[0-9A-F]{40}");
    private static final Pattern PII = Pattern.compile("[A-Za-z0-9()\\-.]+");

    private Identifiers() {
    }

    /** The DOI proper, without a doi.org URL or a {@code doi:} prefix, as case given. */
    public static String doi(String value) {
        String doi = stripPrefix(clean(value, "DOI"), DOI_PREFIXES);
        if (!DOI.matcher(doi).matches()) {
            throw refused("DOI", value, "a DOI looks like 10.1234/abc");
        }
        return doi;
    }

    /** The PMID as digits, without a {@code PMID:} prefix. */
    public static String pmid(String value) {
        String pmid = stripPrefix(clean(value, "PMID"), "pmid:", "pmid");
        if (!DIGITS.matcher(pmid).matches()) {
            throw refused("PMID", value, "a PMID is a number");
        }
        return pmid;
    }

    /** The PMC identifier as {@code PMC} followed by digits, whether or not the prefix was given. */
    public static String pmc(String value) {
        String pmc = stripPrefix(clean(value, "PMC ID"), "pmcid:", "pmc:");
        if (DIGITS.matcher(pmc).matches()) {
            pmc = "PMC" + pmc;
        }
        if (!PMC.matcher(pmc).matches()) {
            throw refused("PMC ID", value, "a PMC ID looks like PMC1234567");
        }
        return "PMC" + pmc.substring(3);
    }

    /** The HAL identifier in lower case, without the HAL URL it may have been pasted with. */
    public static String halId(String value) {
        String halId = stripPrefix(clean(value, "HAL ID"), HAL_PREFIXES).toLowerCase(Locale.ROOT);
        if (!HAL.matcher(halId).matches()) {
            throw refused("HAL ID", value, "a HAL ID looks like hal-01234567");
        }
        return halId;
    }

    /** The ISTEX identifier, 40 hexadecimal characters, in upper case. */
    public static String istexId(String value) {
        String istexId = clean(value, "ISTEX ID").toUpperCase(Locale.ROOT);
        if (!ISTEX.matcher(istexId).matches()) {
            throw refused("ISTEX ID", value, "an ISTEX ID is 40 hexadecimal characters");
        }
        return istexId;
    }

    /** The PII as given, checked to be one. */
    public static String pii(String value) {
        String pii = clean(value, "PII");
        if (!PII.matcher(pii).matches()) {
            throw refused("PII", value, "a PII looks like S0266462305050762");
        }
        return pii;
    }

    /**
     * A free text parameter (a title, a raw citation) checked against a length beyond which it is
     * not what it says it is. Null and blank pass through: whether the parameter is needed is the
     * caller's business.
     */
    public static String text(String name, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new ServiceException(400, "The supplied " + name + " is " + value.length()
                    + " characters long, more than the " + maxLength + " accepted.");
        }
        return value;
    }

    private static String clean(String value, String what) {
        if (isBlank(value)) {
            throw new ServiceException(400, "The supplied " + what + " is empty.");
        }
        String cleaned = value.trim();
        if (cleaned.length() > MAX_LENGTH) {
            throw new ServiceException(400, "The supplied " + what + " is " + cleaned.length()
                    + " characters long, which is not " + article(what) + " " + what + ": " + echo(cleaned));
        }
        return cleaned;
    }

    private static String stripPrefix(String value, String... prefixes) {
        String lower = value.toLowerCase(Locale.ROOT);
        for (String prefix : prefixes) {
            if (lower.startsWith(prefix)) {
                return value.substring(prefix.length()).trim();
            }
        }
        return value;
    }

    private static ServiceException refused(String what, String value, String hint) {
        return new ServiceException(400, "The supplied " + what + " is not " + article(what) + " " + what
                + " (" + hint + "): " + echo(value.trim()));
    }

    private static String article(String what) {
        return "AEIOU".indexOf(Character.toUpperCase(what.charAt(0))) >= 0 ? "an" : "a";
    }

    private static String echo(String value) {
        return value.length() <= ECHO_LENGTH ? value : value.substring(0, ECHO_LENGTH) + "...";
    }
}
