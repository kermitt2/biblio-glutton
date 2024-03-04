package com.scienceminer.glutton.data;

import java.io.Serializable;
import java.util.*;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;

/** 
 * Encapsulates information about an author or editor.
 * All name parts are normalized on setting (ie. MASON would be converted to Mason). 
 */
@SuppressWarnings("serial")
public class Person implements Serializable {

	private String	firstName;
	private String	middleName;
	private String	lastName;
	private String	title;
	private String	suffix;
	private String	inits;

	// the author identifiers, e.g. ORCID, VIAF, ...
	private List<Identifier> identifiers = null;

	// the affiliations of the person
	private List<Affiliation> affiliations = null;

	public Person() {
	}

	public Person(String lastName) {
		setLastName(lastName);
	}

	public Person(String lastName, String middle, String firstName) {
		setLastName(lastName);
		setMiddleName(middle);
		setFirstName(firstName);
	}

	public static Person parse(String text) {
		throw new NotImplementedException("Can not parse a java.lang.String to org.epo.spp.citenpl.beans.Person object");
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = normalizeName(firstName);
	}

	public String getMiddleName() {
		return middleName;
	}

	public void setMiddleName(String middleName) {
		this.middleName = normalizeName(middleName);
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = normalizeName(lastName);
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getSuffix() {
		return suffix;
	}

	public void setSuffix(String suffix) {
		this.suffix = suffix;
	}

	public String getInits() {
		return inits;
	}

	public void setInits(String inits) {
		this.inits = normalizeName(inits);
	}

	public boolean isEmptyPerson() {
		return StringUtils.isBlank(lastName) && StringUtils.isBlank(firstName) && StringUtils.isBlank(middleName);
	}

	public List<Identifier> getIdentifiers() {
		return identifiers;
	}

	public void setIdentifiers(List<Identifier> ids) {
		this.identifiers = ids;
	}

	public void addIdentifier(Identifier id) {
		if (identifiers == null)
			identifiers = new ArrayList<Identifier>();
		identifiers.add(id);
	}

	public List<Affiliation> getAffiliations() {
		return this.affiliations;
	}

	public void setAffiliations(List<Affiliation> affiliations) {
		this.affiliations = affiliations;
	} 

	public void addAffiliation(Affiliation affiliation) {
		if (affiliations == null)
			affiliations = new ArrayList<Affiliation>();
		affiliations.add(affiliation);
	}

	@Override
	public String toString() {
		return printFullName();
	}

	public String printFullName() {
		StringBuilder builder = new StringBuilder();

		if (StringUtils.isNotBlank(lastName)) {
			builder.append(lastName);
		}
		if (StringUtils.isNotBlank(firstName)) {
			builder.append(" " + firstName);
		}
		if (StringUtils.isNotBlank(middleName)) {
			builder.append(" " + middleName);
		}

		return builder.toString();
	}

	/** All Author and Editor names should be normalized so that only the first letter is capitalized. */
	public static String normalizeName(String inputName) {
		return WordUtils.capitalizeFully(inputName, BiblioDefinitions.NAME_DELIMITERS);
	}
}
