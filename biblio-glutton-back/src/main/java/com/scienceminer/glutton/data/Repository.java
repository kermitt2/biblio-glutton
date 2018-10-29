package com.scienceminer.glutton.data;

import java.io.Serializable;

public class Repository implements Serializable {
	private int id = -1;
	private String name = null;
	private int openDoarId = -1;
	private int coreId = -1;
	private String uri = null;
	private String countryCode = null;
	private Double latitude = null;
	private Double longitude = null;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getOpenDoarId() {
		return openDoarId;
	}

	public void setOpenDoarId(int id) {
		this.openDoarId = id;
	}

	public int getCoreId() {
		return coreId;
	}

	public void setCoreId(int id) {
		this.coreId = id;
	}

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public void setCountryCode(String code) {
		this.countryCode = code;
	}

	public Double getLatitude() {
		return latitude;
	}

	public void setLatitude(Double latitude) {
		this.latitude = latitude;
	}

	public Double getLongitude() {
		return longitude;
	}

	public void setLongitude(Double longitude) {
		this.longitude = longitude;
	}

	public String toJson() {
		StringBuilder json = new StringBuilder();
		json.append("{");
		json.append("\"id\":"+id);
		
		if (coreId != -1) {
			json.append(", \"coreId\":"+coreId);
		}
		if (openDoarId != -1) {
			json.append(", \"openDoarId\":"+openDoarId);
		}
		if (name != null) {
			json.append(", \"name\":\""+name+"\"");
		}
		if (uri != null) {
			json.append(", \"uri\":\""+uri+"\"");
		}
		if (countryCode != null) {
			json.append(", \"countryCode\":\""+countryCode+"\"");
		}
		if (latitude != null) {
			json.append(", \"latitude\":"+latitude);
		}
		if (longitude != null) {
			json.append(", \"longitude\":"+longitude);
		}
		json.append("}");
		return json.toString();
	}
}