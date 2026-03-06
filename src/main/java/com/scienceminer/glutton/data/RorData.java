package com.scienceminer.glutton.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class RorData implements Serializable {

    private String rorId;
    private String name;
    private List<String> altNames;
    private String countryCode;
    private String countryName;
    private List<String> types;
    private String status;
    private Integer established;
    private List<String> fundrefIds;
    private String gridId;
    private String isni;
    private String wikidataId;
    private List<RorRelationship> relationships;
    private String website;

    public String getRorId() {
        return rorId;
    }

    public void setRorId(String rorId) {
        this.rorId = rorId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getAltNames() {
        return altNames;
    }

    public void setAltNames(List<String> altNames) {
        this.altNames = altNames;
    }

    public void addAltName(String altName) {
        if (this.altNames == null) {
            this.altNames = new ArrayList<>();
        }
        this.altNames.add(altName);
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCountryName() {
        return countryName;
    }

    public void setCountryName(String countryName) {
        this.countryName = countryName;
    }

    public List<String> getTypes() {
        return types;
    }

    public void setTypes(List<String> types) {
        this.types = types;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getEstablished() {
        return established;
    }

    public void setEstablished(Integer established) {
        this.established = established;
    }

    public List<String> getFundrefIds() {
        return fundrefIds;
    }

    public void setFundrefIds(List<String> fundrefIds) {
        this.fundrefIds = fundrefIds;
    }

    public void addFundrefId(String fundrefId) {
        if (this.fundrefIds == null) {
            this.fundrefIds = new ArrayList<>();
        }
        this.fundrefIds.add(fundrefId);
    }

    public String getGridId() {
        return gridId;
    }

    public void setGridId(String gridId) {
        this.gridId = gridId;
    }

    public String getIsni() {
        return isni;
    }

    public void setIsni(String isni) {
        this.isni = isni;
    }

    public String getWikidataId() {
        return wikidataId;
    }

    public void setWikidataId(String wikidataId) {
        this.wikidataId = wikidataId;
    }

    public List<RorRelationship> getRelationships() {
        return relationships;
    }

    public void setRelationships(List<RorRelationship> relationships) {
        this.relationships = relationships;
    }

    public void addRelationship(RorRelationship relationship) {
        if (this.relationships == null) {
            this.relationships = new ArrayList<>();
        }
        this.relationships.add(relationship);
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    @Override
    public String toString() {
        return "RorData{" +
                "rorId='" + rorId + '\'' +
                ", name='" + name + '\'' +
                ", altNames=" + altNames +
                ", countryCode='" + countryCode + '\'' +
                ", countryName='" + countryName + '\'' +
                ", types=" + types +
                ", status='" + status + '\'' +
                ", established=" + established +
                ", fundrefIds=" + fundrefIds +
                ", gridId='" + gridId + '\'' +
                ", isni='" + isni + '\'' +
                ", wikidataId='" + wikidataId + '\'' +
                ", relationships=" + relationships +
                ", website='" + website + '\'' +
                '}';
    }

    public static class RorRelationship implements Serializable {
        private String type;
        private String rorId;
        private String label;

        public RorRelationship() {
        }

        public RorRelationship(String type, String rorId, String label) {
            this.type = type;
            this.rorId = rorId;
            this.label = label;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getRorId() {
            return rorId;
        }

        public void setRorId(String rorId) {
            this.rorId = rorId;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return "RorRelationship{" +
                    "type='" + type + '\'' +
                    ", rorId='" + rorId + '\'' +
                    ", label='" + label + '\'' +
                    '}';
        }
    }
}
