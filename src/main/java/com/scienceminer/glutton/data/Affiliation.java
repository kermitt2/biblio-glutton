package com.scienceminer.glutton.data;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.List;

/**
 * Class for representing and exchanging affiliation information.
 *
 * @author Patrice Lopez
 */
public class Affiliation implements Serializable {

    private String acronym = null;
    private String name = null;
    private String url = null;
    private List<String> institutions = null; // for additional institutions
    private List<String> departments = null; // for additional departments
    private List<String> laboratories = null; // for additional laboratories

    private String country = null;
    private String postCode = null;
    private String postBox = null;
    private String region = null;
    private String settlement = null;
    private String addrLine = null;
    private String marker = null;

    private String addressString = null; // unspecified address field
    private String affiliationString = null; // unspecified affiliation field

    private boolean failAffiliation = true; // tag for unresolved affiliation attachment

    // an identifier for the affiliation independent from the marker, present in the TEI result
    private String key = null;

    // list of identifiers, e.g. orcid, etc.
    private List<Identifier> identifiers = null;

    public Affiliation() {
    }

    public Affiliation(String raw) {
        this.affiliationString = raw;
    }

    public Affiliation(com.scienceminer.glutton.data.Affiliation aff) {
        acronym = aff.getAcronym();
        name = aff.getName();
        url = aff.getURL();
        addressString = aff.getAddressString();
        country = aff.getCountry();
        marker = aff.getMarker();
        departments = aff.getDepartments();
        institutions = aff.getInstitutions();
        laboratories = aff.getLaboratories();
        postCode = aff.getPostCode();
        postBox = aff.getPostBox();
        region = aff.getRegion();
        settlement = aff.getSettlement();
        addrLine = aff.getAddrLine();
        affiliationString = aff.getAffiliationString();
        identifiers = aff.getIdentifiers();
    }

    public String getAcronym() { 
		return acronym; 
	}
	
    public String getName() {
        return name;
    }

    public String getURL() {
        return url;
    }

    public String getAddressString() {
        return addressString;
    }

    public String getCountry() {
        return country;
    }

    public String getMarker() {
        return marker;
    }

    public String getPostCode() {
        return postCode;
    }

    public String getPostBox() {
        return postBox;
    }

    public String getRegion() {
        return region;
    }

    public String getSettlement() {
        return settlement;
    }

    public String getAddrLine() {
        return addrLine;
    }

    public String getAffiliationString() {
        return affiliationString;
    }

    public List<String> getInstitutions() {
        return institutions;
    }

    public List<String> getLaboratories() {
        return laboratories;
    }

    public List<String> getDepartments() {
        return departments;
    }

    public String getKey() {
        return key;
    }

    public List<Identifier> getIdentifiers() {
        return this.identifiers;
    }

    public void setAcronym(String s) { 
		acronym = s; 
	}
	
    public void setName(String s) {
        name = s;
    }

    public void setURL(String s) {
        url = s;
    }

    public void setAddressString(String s) {
        addressString = s;
    }

    public void setCountry(String s) {
        country = s;
    }

    public void setMarker(String s) {
        marker = s;
    }

    public void setPostCode(String s) {
        postCode = s;
    }

    public void setPostBox(String s) {
        postBox = s;
    }

    public void setRegion(String s) {
        region = s;
    }

    public void setSettlement(String s) {
        settlement = s;
    }

    public void setAddrLine(String s) {
        addrLine = s;
    }

    public void setAffiliationString(String s) {
        affiliationString = s;
    }

    public void setInstitutions(List<String> affs) {
        institutions = affs;
    }

    public void addInstitution(String aff) {
        if (institutions == null)
            institutions = new ArrayList<String>();
        institutions.add(aff);
    }

    public void setDepartments(List<String> affs) {
        departments = affs;
    }

    public void addDepartment(String aff) {
        if (departments == null)
            departments = new ArrayList<String>();
        departments.add(aff);
    }

    public void setIdentifiers(List<Identifier> identifiers) {
        this.identifiers = identifiers;
    }

    public void addIdentifier(Identifier identifier) {
        if (identifiers == null) {
            identifiers = new ArrayList<Identifier>();
        }
        identifiers.add(identifier);
    }

    public void setLaboratories(List<String> affs) {
        laboratories = affs;
    }

    public void addLaboratory(String aff) {
        if (laboratories == null)
            laboratories = new ArrayList<String>();
        laboratories.add(aff);
    }

    public void extendFirstInstitution(String theExtend) {
        if (institutions == null) {
            institutions = new ArrayList<String>();
            institutions.add(theExtend);
        } else {
            String first = institutions.get(0);
            first = first + theExtend;
            institutions.set(0, first);
        }
    }

    public void extendLastInstitution(String theExtend) {
        if (institutions == null) {
            institutions = new ArrayList<String>();
            institutions.add(theExtend);
        } else {
            String first = institutions.get(institutions.size() - 1);
            first = first + theExtend;
            institutions.set(institutions.size() - 1, first);
        }
    }

    public void extendFirstDepartment(String theExtend) {
        if (departments == null) {
            departments = new ArrayList<String>();
            departments.add(theExtend);
        } else {
            String first = departments.get(0);
            first = first + theExtend;
            departments.set(0, first);
        }
    }

    public void extendLastDepartment(String theExtend) {
        if (departments == null) {
            departments = new ArrayList<String>();
            departments.add(theExtend);
        } else {
            String first = departments.get(departments.size() - 1);
            first = first + theExtend;
            departments.set(departments.size() - 1, first);
        }
    }

    public void extendFirstLaboratory(String theExtend) {
        if (laboratories == null) {
            laboratories = new ArrayList<String>();
            laboratories.add(theExtend);
        } else {
            String first = laboratories.get(0);
            first = first + theExtend;
            laboratories.set(0, first);
        }
    }

    public void extendLastLaboratory(String theExtend) {
        if (laboratories == null) {
            laboratories = new ArrayList<String>();
            laboratories.add(theExtend);
        } else {
            String first = laboratories.get(laboratories.size() - 1);
            first = first + theExtend;
            laboratories.set(laboratories.size() - 1, first);
        }
    }

    public boolean notNull() {
        return !((departments == null) &
                (institutions == null) &
                (laboratories == null) &
                (country == null) &
                (postCode == null) &
                (postBox == null) &
                (region == null) &
                (settlement == null) &
                (addrLine == null) &
                (affiliationString == null) &
                (addressString == null));
    }

    public void setFailAffiliation(boolean b) {
        failAffiliation = b;
    }

    public boolean getFailAffiliation() {
        return failAffiliation;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void clean() {
        if (departments != null) {
            List<String> newDepartments = new ArrayList<String>();
            for (String department : departments) {
                if (department.length() > 2) {
                    newDepartments.add(department);
                }
            }
            departments = newDepartments;
        }

        if (institutions != null) {
            List<String> newInstitutions = new ArrayList<String>();
            for (String institution : institutions) {
                if (institution.length() > 1) {
                    newInstitutions.add(institution);
                }
            }
            institutions = newInstitutions;
        }

        if (laboratories != null) {
            List<String> newLaboratories = new ArrayList<String>();
            for (String laboratorie : laboratories) {
                if (laboratorie.length() > 2) {
                    newLaboratories.add(laboratorie);
                }
            }
            laboratories = newLaboratories;
        }

        if (country != null) {
			if (country.endsWith(")")) {
				// for some reason the ) at the end of this field is not removed
				country = country.substring(0,country.length()-1);
			}
            if (country.length() < 2)
                country = null;
        }
        if (postCode != null) {
            if (postCode.length() < 2)
                postCode = null;
        }
        if (postBox != null) {
            if (postBox.length() < 2)
                postBox = null;
        }
        if (region != null) {
            if (region.length() < 2)
                region = null;
        }
        if (settlement != null) {
            if (settlement.length() < 2)
                settlement = null;
        }
        if (addrLine != null) {
            if (addrLine.length() < 2)
                addrLine = null;
        }
        if (addressString != null) {
            if (addressString.length() < 2)
                addressString = null;
        }
        if (affiliationString != null) {
            if (affiliationString.length() < 2)
                affiliationString = null;
        }
        if (marker != null) {
			marker = marker.replace(" ", "");
        }
    }

    @Override
    public String toString() {
        return "Affiliation{" +
                "name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", key='" + key + '\'' +
                ", institutions=" + institutions +
                ", departments=" + departments +
                ", laboratories=" + laboratories +
                ", country='" + country + '\'' +
                ", postCode='" + postCode + '\'' +
                ", postBox='" + postBox + '\'' +
                ", region='" + region + '\'' +
                ", settlement='" + settlement + '\'' +
                ", addrLine='" + addrLine + '\'' +
                ", marker='" + marker + '\'' +
                ", addressString='" + addressString + '\'' +
                ", affiliationString='" + affiliationString + '\'' +
                ", failAffiliation=" + failAffiliation +
                '}';
    }

    
}