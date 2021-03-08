package com.scienceminer.lookup.utils;

import org.nustaq.serialization.FSTConfiguration;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashString {

    public static String hashDOI(String doi) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(doi.getBytes());
        byte[] digest = md.digest();
        return DatatypeConverter
                .printHexBinary(digest);
    }


}
