package org.greenscape.site.rest;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.greenscape.core.ResourceRegistry;
import org.greenscape.core.model.Page;
import org.greenscape.core.model.PageModel;
import org.greenscape.core.model.Site;
import org.greenscape.core.model.SiteModel;
import org.greenscape.core.service.Service;
import org.greenscape.persistence.DocumentModel;
import org.greenscape.site.service.SiteService;
import org.greenscape.web.rest.AbstractRestService;
import org.greenscape.web.rest.RestService;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.log.LogService;

@Component(name = SiteResource.FACTORY_DS, property = { Constants.SERVICE_RANKING + "=2000" })
public class SiteResource extends AbstractRestService implements RestService {
	static final String FACTORY_DS = "org.greenscape.site.rest.SiteResource";
	private static final String MODEL_PAGE = "page";
	// private static final String PARAM_DEF_ORG_ID = "orgId";
	private static final String PATH_DEF_SITE_ID = "{siteId}";
	private static final String PATH_DEF_SITE_NAME = "name/{name}";

	private SiteService siteService;

	@Override
	public String getResourceName() {
		return "site";
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path(PATH_DEF_SITE_ID)
	public SiteModel getSite(@PathParam("siteId") String siteId) {
		SiteModel site = null;
		try {
			site = siteService.find(siteId);
		} catch (Exception e) {
			logService.log(LogService.LOG_ERROR, e.getMessage(), e);
			throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity("Server error").build());
		}
		return site;
	}

	// @GET
	// @Produces(MediaType.APPLICATION_JSON)
	// @Path(PATH_DEF_SITE_NAME)
	public SiteModel getSiteByName(@PathParam("name") String name) {
		SiteModel site = null;
		try {
			List<SiteModel> sites = siteService.find(SiteModel.SITE_NAME, name);
			if (sites == null || sites.size() == 0) {
				throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity("Site does not exist")
						.build());
			}
			site = sites.get(0);
			List<Page> pages = service.find(MODEL_PAGE, Page.SITE_ID, site.getModelId());
			if (pages != null) {
				site.getPages().addAll(pages);
			}
		} catch (Exception e) {
			logService.log(LogService.LOG_ERROR, e.getMessage(), e);
			throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity(e.getMessage()).build());
		}
		return site;
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public String addSite(SiteModelParam param) {
		Site entity;
		entity = new Site();
		copy(entity, param);
		DocumentModel model = siteService.save(entity);
		return model.getModelId();
	}

	@PUT
	@Path(PATH_DEF_SITE_ID)
	@Consumes(MediaType.APPLICATION_JSON)
	public String updateSite(@PathParam("siteId") String siteId, SiteModelParam param) {
		SiteModel entity = siteService.find(siteId);
		if (entity == null) {
			throw new WebApplicationException(Response.status(Status.NOT_FOUND)
					.entity("No site with id " + siteId + " exists").build());
		}
		copy(entity, param);
		siteService.save(entity);
		return "OK";
	}

	@DELETE
	public void deleteModel() {
		siteService.delete();
	}

	@DELETE
	@Path(PATH_DEF_SITE_ID)
	public void deleteModel(@PathParam("siteId") String id) {
	}

	/*
	 * Pages REST API
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path(PATH_DEF_SITE_ID + "/page")
	public List<? extends PageModel> getPages(@PathParam("siteId") String siteId) {
		List<Page> pages = service.find(MODEL_PAGE, PageModel.SITE_ID, siteId);
		return pages;
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Path(PATH_DEF_SITE_ID + "/page")
	public PageModel addPage(@PathParam("siteId") String siteId, PageModelParam pageParam) {
		// TODO: validate siteId
		// TODO: validate page properties
		List<Page> pages = service.find(MODEL_PAGE, PageModel.SITE_ID, siteId);
		String path = pageParam.getName().toLowerCase().replace(' ', '-');
		String newPath = path;
		int i = 1;
		while (hasPath(pages, newPath)) {
			newPath = path + i++;
		}
		Page page = new Page();
		page.setActive(true);
		page.setName(pageParam.getName());
		page.setPathURL(newPath);
		page.setLayoutURL("/common/layout/2-col.html");
		page.setSiteId(siteId);
		Page entity = service.save(PageModel.MODEL_NAME, page);
		return entity;
	}

	@PUT
	@Path(PATH_DEF_SITE_ID + "/page/" + "{pageId}")
	@Consumes(MediaType.APPLICATION_JSON)
	public void updatePage(@PathParam("siteId") String siteId, @PathParam("pageId") String pageId,
			@Context UriInfo uriInfo) {
		Page entity = service.findByModelId(MODEL_PAGE, pageId);
		if (entity == null) {
			throw new WebApplicationException(Response.status(Status.NOT_FOUND)
					.entity("No page with id " + pageId + " exists").build());
		}
		copy(entity, uriInfo.getQueryParameters());
		service.update(PageModel.MODEL_NAME, entity);
	}

	@DELETE
	@Path(PATH_DEF_SITE_ID + "/page/" + "{pageId}")
	public void deletePage(@PathParam("siteId") String siteId, @PathParam("pageId") String pageId) {
		try {
			siteService.deletePage(siteId, pageId);
		} catch (Exception e) {
			throw new WebApplicationException(Response.status(Status.NOT_ACCEPTABLE).entity(e.getMessage()).build());
		}
	}

	@Reference(policy = ReferencePolicy.DYNAMIC)
	public void setSiteService(SiteService siteService) {
		this.siteService = siteService;
	}

	public void unsetSiteService(SiteService siteService) {
		this.siteService = null;
	}

	@Override
	@Reference(policy = ReferencePolicy.DYNAMIC)
	public void setService(Service service) {
		this.service = service;
	}

	public void unsetService(Service service) {
		this.service = null;
	}

	@Override
	@Reference(policy = ReferencePolicy.DYNAMIC)
	public void setResourceRegistry(ResourceRegistry resourceRegistry) {
		this.resourceRegistry = resourceRegistry;
	}

	public void unsetResourceRegistry(ResourceRegistry resourceRegistry) {
		this.resourceRegistry = null;
	}

	@Override
	@Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
	public void setLogService(LogService logService) {
		this.logService = logService;
	}

	public void unsetLogService(LogService logService) {
		this.logService = null;
	}

	@Override
	public String toString() {
		return getResourceName();
	}

	private void copy(SiteModel entity, SiteModelParam param) {
		entity.setActive(param.isActive());
		entity.setDefault(param.isDefault());
		entity.setHomeURL(param.getHomeURL());
		entity.setName(param.getName());
	}

	private void copy(Page entity, MultivaluedMap<String, String> param) {
		// entity.setActive(param.isActive());
		// entity.setDefault(param.isDefault());
		entity.setPathURL(param.getFirst("pathURL"));
		try {
			entity.setName(URLDecoder.decode(param.getFirst("name"), "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			logService.log(LogService.LOG_ERROR, e.getMessage(), e);
		}
	}

	private boolean hasPath(List<Page> pages, String path) {
		for (Page page : pages) {
			if (page.getPathURL().equals(path)) {
				return true;
			}
		}
		return false;
	}
}
