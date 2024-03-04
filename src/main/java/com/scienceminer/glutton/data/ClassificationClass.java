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

import org.joda.time.Partial;

/**
 *  A class for classification classes
 */
public class ClassificationClass implements Serializable {

	private String scheme;

	public ClassificationClass(String scheme) {
		this.scheme = scheme;
	}

	public String getScheme() {
		return this.scheme;
	}

}