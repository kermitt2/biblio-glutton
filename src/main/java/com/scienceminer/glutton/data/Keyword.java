 package com.scienceminer.glutton.data;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.collections.Transformer;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.io.*;

import org.joda.time.Partial;

/**
 *  A class for representing keywords. A keyword can be assocated to a source (origin) 
 *  and a boolean value indicating if it is a major topic or not.  
 */
public class Keyword implements Serializable {

    private String value;
    private String origin;
    private Boolean isMajorTopic;

    public Keyword() {
    }

    public Keyword(String value, String origin, Boolean isMajorTopic) {
        setValue(value);
        setOrigin(origin);
        setIsMajorTopic(isMajorTopic);
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public Boolean getIsMajorTopic() {
        return isMajorTopic;
    }

    public void setIsMajorTopic(Boolean isMajorTopic) {
        this.isMajorTopic = isMajorTopic;
    }

    @Override
    public String toString() {
        return "Keyword{" +
                "value='" + value + '\'' +
                ", origin='" + origin + '\'' +
                ", isMajorTopic='" + isMajorTopic + '\'' +
                '}';
    }

}