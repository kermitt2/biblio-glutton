package com.scienceminer.glutton.data;

import java.io.Serializable;
import com.fasterxml.jackson.core.io.JsonStringEncoder;

/**
 * A generic representation for an identifier, because there are so many identifiers around now
 */
public class Identifier implements Serializable {
	private String identifierName;
	private String identifierValue;

	public Identifier(String idType, String value) {
		this.identifierName = idType;
		this.identifierValue = value;
	}

	public String getIdentifierName() {
		return this.identifierName;
	}

	public void setIdentifierName(String idType) {
		this.identifierName = idType;
	}

	public String getIdentifierValue() {
		return this.identifierValue;
	}

	public void setIdentifierValue(String value) {
		this.identifierValue = value;
	}

	public String toJson() {
		JsonStringEncoder encoder = JsonStringEncoder.getInstance();
		StringBuilder json = new StringBuilder();
		json.append("{");
		if (identifierValue != null) {
			byte[] encoded = encoder.quoteAsUTF8(identifierValue);
            String output = new String(encoded);
			json.append("\"id\":\""+output+"\"");
		}
		if (identifierName != null) {
			json.append(", \"type\":\""+identifierName+"\"");
		}
		json.append("}");
		return json.toString();
	}
}