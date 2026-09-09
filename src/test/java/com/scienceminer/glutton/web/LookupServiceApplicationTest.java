package com.scienceminer.glutton.web;

import org.junit.Test;

import java.util.Set;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The {@code corsAllowedOrigins} setting was written for Jetty 11's {@code CrossOriginFilter},
 * which took literal origins and globs. Jetty 12's {@code CrossOriginHandler} reads regular
 * expressions, so these cover the translation between the two.
 */
public class LookupServiceApplicationTest {

    /** Mirrors how CrossOriginHandler applies the patterns it is given. */
    private static boolean matches(Set<String> patterns, String origin) {
        return patterns.stream().anyMatch(p -> "*".equals(p) || Pattern.matches(p, origin));
    }

    @Test
    public void testAnyOrigin_shouldStayTheWildcard() {
        Set<String> patterns = LookupServiceApplication.toAllowedOriginPatterns("*");

        assertThat(patterns, is(Set.of("*")));
        assertTrue(matches(patterns, "https://anything.example.org"));
    }

    @Test
    public void testSubdomainGlob_shouldMatchSubdomains() {
        Set<String> patterns = LookupServiceApplication.toAllowedOriginPatterns("https://*.example.com");

        assertTrue(matches(patterns, "https://api.example.com"));
        // CrossOriginFilter was greedy on purpose, so a glob spans several subdomains.
        assertTrue(matches(patterns, "https://a.b.example.com"));
        assertFalse(matches(patterns, "https://example.org"));
        assertFalse(matches(patterns, "https://api.example.com.evil.net"));
    }

    @Test
    public void testExactOrigin_shouldBeTreatedAsALiteral() {
        Set<String> patterns = LookupServiceApplication.toAllowedOriginPatterns("https://api.example.com");

        assertTrue(matches(patterns, "https://api.example.com"));
        // Unquoted, the dots would be regex wildcards and this would wrongly be allowed.
        assertFalse(matches(patterns, "https://apiXexample.com"));
        assertFalse(matches(patterns, "https://other.example.com"));
    }

    @Test
    public void testSeveralOrigins_shouldAllBeTranslated() {
        Set<String> patterns =
            LookupServiceApplication.toAllowedOriginPatterns("https://a.example.com, https://*.example.org");

        assertThat(patterns.size(), is(2));
        assertTrue(matches(patterns, "https://a.example.com"));
        assertTrue(matches(patterns, "https://www.example.org"));
        assertFalse(matches(patterns, "https://b.example.com"));
    }

    @Test
    public void testBlankEntries_shouldBeDropped() {
        // A trailing comma must not turn into an empty pattern, which would match nothing useful
        // but would still be handed to Jetty.
        Set<String> patterns = LookupServiceApplication.toAllowedOriginPatterns("https://a.example.com, ,");

        assertThat(patterns, is(Set.of(Pattern.quote("https://a.example.com"))));
    }
}
