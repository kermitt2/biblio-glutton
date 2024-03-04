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
 *  A class for MeSH classification classes
 */
public class MeSHClass extends ClassificationClass implements Serializable {
	private String descriptorUI = null;
	private String descriptorName = null;
	private List<String> qualifierUIs = null;
	private List<String> qualifierNames = null;
	private boolean majorTopicDescriptor = false;
	private List<Boolean> majorTopicQualifiers = null;

	private String chemicalNameOfSubstance = null;
	private String chemicalRegistryNumber = null;
	private String chemicalUI = null;

	public MeSHClass() {
		super("MeSH");
	}

	public String getDescriptorUI() {
		return this.descriptorUI;
	}

	public void setDescriptorUI(String ui) {
		this.descriptorUI = ui;
	}

	public String getDescriptorName() {
		return this.descriptorName;
	}

	public void setDescriptorName(String name) {
		this.descriptorName = name;
	}

	public List<String> getQualifierUIs() {
		return this.qualifierUIs;
	}

	public void setQualifierUIs(List<String> uis) {
		this.qualifierUIs = uis;
	}

	public void addQualifierUIs(String ui) {
		if (this.qualifierUIs == null)
			this.qualifierUIs = new ArrayList<String>();
		this.qualifierUIs.add(ui);
	}

	public List<String> getQualifierNames() {
		return this.qualifierNames;
	}

	public void setQualifierNames(List<String> names) {
		this.qualifierNames = names;
	}

	public void addQualifierName(String name) {
		if (this.qualifierNames == null)
			this.qualifierNames = new ArrayList<String>();
		this.qualifierNames.add(name);
	}

	public boolean getMajorTopicDescriptor() {
		return majorTopicDescriptor;
	}

	public void setMajorTopicDescriptor(boolean major) {
		this.majorTopicDescriptor = major;
	}

	public List<Boolean> getMajorTopicQualifiers() {
		return majorTopicQualifiers;
	}

	public void setMajorTopicQualifiers(List<Boolean> major) {
		this.majorTopicQualifiers = major;
	}

	public void addMajorTopicQualifiers(Boolean major) {
		if (this.majorTopicQualifiers == null)
			this.majorTopicQualifiers = new ArrayList<Boolean>();
		this.majorTopicQualifiers.add(major);
	}

	public String getChemicalNameOfSubstance() {
		return this.chemicalNameOfSubstance;
	}

	public void setChemicalNameOfSubstance(String name) {
		this.chemicalNameOfSubstance = name;
	}

	public String getChemicalRegistryNumber() {
		return this.chemicalRegistryNumber;
	}

	public void setChemicalRegistryNumber(String number) {
		this.chemicalRegistryNumber = number;
	}

	public String getChemicalUI() {
		return this.chemicalUI;
	}

	public void setChemicalUI(String ui) {
		this.chemicalUI = ui;
	}

	public String toJson() {
		StringBuilder json = new StringBuilder();
		JsonStringEncoder encoder = JsonStringEncoder.getInstance();
		json.append("{");
		if ( (descriptorName != null) && (descriptorName.length() > 0) ) {
			json.append("\"descriptor\":{");

			byte[] encodedDescriptorName = encoder.quoteAsUTF8(descriptorName);
            String outputDescriptorName  = new String(encodedDescriptorName);

			json.append("\"term\":\"").append(outputDescriptorName).append("\"");
			if ( (descriptorUI != null) && (descriptorUI.length() > 0) ) {
				json.append(",\"meshId\":\"").append(descriptorUI).append("\"");
			}
			json.append(", \"majorTopic\":\"").append(majorTopicDescriptor).append("\"");
			json.append("}");
			if ( (qualifierNames != null) && (qualifierNames.size() > 0) ) {
				json.append(",\"qualifiers\":[");
				for(int i=0; i < qualifierNames.size(); i++) {
					if (i !=0 ) 
						json.append(",");
					json.append("{\"qualifier\":{");

					byte[] encodedQualifierName = encoder.quoteAsUTF8(qualifierNames.get(i));
            		String outputQualifierName  = new String(encodedQualifierName);

					json.append("\"term\":\"").append(outputQualifierName).append("\"");
					if ( (qualifierUIs != null) && (qualifierUIs.size() > i) ) {
						json.append(", \"meshId\":\"").append(qualifierUIs.get(i)).append("\"");
					}
					if ( (majorTopicQualifiers != null) && (majorTopicQualifiers.size() > i) ) {
						json.append(",\"majorTopic\":\"").append(majorTopicQualifiers.get(i)).append("\"");
					}
					json.append("}}");
				}
				json.append("]");
			}
		} else if ( (chemicalNameOfSubstance != null) && (chemicalNameOfSubstance.length() > 0) ) {
			json.append("\"chemical\":{");

			byte[] encodedChemicalNameOfSubstance = encoder.quoteAsUTF8(chemicalNameOfSubstance);
            String outputChemicalNameOfSubstance  = new String(encodedChemicalNameOfSubstance);

			json.append("\"term\":\"").append(outputChemicalNameOfSubstance).append("\"");
			if ( (chemicalUI != null) && (chemicalUI.length() > 0) ) {
				json.append(", \"meshId\":\"").append(chemicalUI).append("\"");
			}
			if ( (chemicalRegistryNumber != null) && (chemicalRegistryNumber.length() > 0)) {
				json.append(",\"chemicalRegistryNumber\":\"").append(chemicalRegistryNumber).append("\"");
			}
			json.append("}");
		}
		json.append("}");

		return json.toString();
	}
}