package org.greenscape.site.rest;


public class SiteModelParam {
	private String organizationId;
	private String name;
	private String homeURL;
	private String parentSiteId;
	private boolean isActive;
	private boolean isDefault;

	public String getOrganizationId() {
		return organizationId;
	}

	public SiteModelParam setOrganizationId(String organizationId) {
		this.organizationId = organizationId;
		return this;
	}

	public String getName() {
		return name;
	}

	public SiteModelParam setName(String name) {
		this.name = name;
		return this;
	}

	public String getHomeURL() {
		return homeURL;
	}

	public SiteModelParam setHomeURL(String homeURL) {
		this.homeURL = homeURL;
		return this;
	}

	public String getParentSiteId() {
		return parentSiteId;
	}

	public SiteModelParam setParentSiteId(String parentSiteId) {
		this.parentSiteId = parentSiteId;
		return this;
	}

	public Boolean isActive() {
		return isActive;
	}

	public SiteModelParam setActive(Boolean active) {
		this.isActive = active;
		return this;
	}

	public Boolean isDefault() {
		return isDefault;
	}

	public SiteModelParam setDefault(Boolean isDefault) {
		this.isDefault = isDefault;
		return this;
	}

}
