package com.scienceminer.glutton.data;

import java.io.Serializable;

/** 
 * Encapsulates a property of the Biblio attribute. 
 */
@SuppressWarnings("serial")
public class BiblioAttribute implements Serializable {

	// source
	public static final int	PUBLISHER		= 0;
	public static final int	EXTRACTION		= 1;
	public static final int	USER			= 2;

	// user status
	public static final int	NOT_VALIDATED	= 0;
	public static final int	VALIDATED		= 1;

	private String attributeName = null;
	private Object attributeValue = null;
	private int attributeSource = -1;
	private int userStatus = -1;
	private double confidence = 0.0;

	public static BiblioAttribute createPublisherAttribute(String name, Object value) {
		BiblioAttribute att = new BiblioAttribute();
		att.attributeName = name;
		att.attributeValue = value;
		att.attributeSource = PUBLISHER;
		att.userStatus = NOT_VALIDATED;
		att.confidence = 1.0;
		return att;
	}

	public static BiblioAttribute createExtractedAttribute(String name, Object value, double conf) {
		BiblioAttribute att = new BiblioAttribute();
		att.attributeName = name;
		att.attributeValue = value;
		att.attributeSource = EXTRACTION;
		att.userStatus = NOT_VALIDATED;
		att.confidence = conf;
		return att;
	}

	public void setAttributeName(String attributeName) {
		this.attributeName = attributeName;
	}

	public String getAttributeName() {
		return attributeName;
	}

	public void setAttributeValue(Object attributeValue) {
		this.attributeValue = attributeValue;
	}

	public Object getAttributeValue() {
		return attributeValue;
	}

	public void setAttributeSource(int attributeSource) {
		this.attributeSource = attributeSource;
	}

	public int getAttributeSource() {
		return attributeSource;
	}

	public void setUserStatus(int userStatus) {
		this.userStatus = userStatus;
	}

	public int getUserStatus() {
		return userStatus;
	}

	public void setConfidence(double confidence) {
		this.confidence = confidence;
	}

	public double getConfidence() {
		return confidence;
	}

	public String toString() {
		return attributeName + ":" + attributeValue;
	}
}
