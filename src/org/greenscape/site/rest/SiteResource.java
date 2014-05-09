package org.greenscape.site.rest;

import java.util.List;
import java.util.Map;

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
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.greenscape.persistence.DocumentModel;
import org.greenscape.persistence.ModelRegistryEntry;
import org.greenscape.persistence.annotations.Model;
import org.greenscape.site.Page;
import org.greenscape.site.PageModel;
import org.greenscape.site.Site;
import org.greenscape.site.SiteModel;
import org.greenscape.site.service.SiteService;
import org.greenscape.web.rest.RestService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.log.LogService;

@Component(name = SiteResource.FACTORY_DS, property = { Constants.SERVICE_RANKING + "=1000" })
public class SiteResource implements RestService {
	static final String FACTORY_DS = "org.greenscape.site.rest.SiteResource";
	private static final String PARAM_DEF_ORG_ID = "orgId";
	private static final String PATH_DEF_SITE_ID = "id/{siteId}";
	private static final String PATH_DEF_SITE_NAME = "name/{name}";

	private SiteService siteService;
	private ModelRegistryEntry modelRegistryEntry;
	private Class<? extends DocumentModel> clazz;

	private BundleContext context;
	private LogService logService;

	@Override
	public String getResourceName() {
		return clazz.getAnnotation(Model.class).name();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<SiteModel> list(@Context UriInfo uriInfo) {
		@SuppressWarnings("unchecked")
		List<SiteModel> list = (List<SiteModel>) siteService.find(clazz, uriInfo.getQueryParameters());
		return list;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path(PATH_DEF_SITE_ID)
	public SiteModel getSite(@PathParam("siteId") String siteId) {
		Site site = null;
		try {
			site = siteService.find(clazz, siteId);
		} catch (Exception e) {
			logService.log(LogService.LOG_ERROR, e.getMessage(), e);
			throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity("Server error").build());
		}
		return site;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path(PATH_DEF_SITE_NAME)
	public SiteModel getSiteByName(@PathParam("name") String name) {
		Site site = null;
		try {
			List<Site> sites = siteService.find(clazz, SiteModel.SITE_NAME, name);
			if (sites == null || sites.size() == 0) {
				throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity("Site does not exist")
						.build());
			}
			site = sites.get(0);
			List<Page> pages = siteService.find(Page.class, Page.SITE_ID, site.getModelId());
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
		try {
			entity = (Site) clazz.newInstance();
			copy(entity, param);
			DocumentModel model = siteService.save(entity);
			return model.getModelId();
		} catch (InstantiationException | IllegalAccessException e) {
			throw new WebApplicationException(Response.status(Status.NOT_ACCEPTABLE).entity(e.getMessage()).build());
		}
	}

	@PUT
	@Path(PATH_DEF_SITE_ID)
	@Consumes(MediaType.APPLICATION_JSON)
	public String updateSite(@PathParam("siteId") String siteId, SiteModelParam param) {
		Site entity = siteService.find(clazz, siteId);
		if (entity == null) {
			throw new WebApplicationException(Response.status(Status.NOT_FOUND)
					.entity("No site with id " + siteId + " exists").build());
		}
		copy(entity, param);
		siteService.save(entity);
		return "OK";
	}

	@DELETE
	public String deleteModel() {
		siteService.delete(clazz);
		return "OK";
	}

	@DELETE
	@Path(PATH_DEF_SITE_ID)
	public String deleteModel(@PathParam("siteId") String id) {
		return "OK";
	}

	/*
	 * Pages REST API
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path(PATH_DEF_SITE_ID + "/page")
	public List<PageModel> getPages(@PathParam("siteId") String siteId) {
		List<PageModel> pages = siteService.find(Page.class, PageModel.SITE_ID, siteId);
		return pages;
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Path(PATH_DEF_SITE_ID + "/page")
	public PageModel addPage(@PathParam("siteId") String siteId, PageModelParam pageParam) {
		// TODO: validate siteId
		// TODO: validate page properties
		List<Page> pages = siteService.find(Page.class, PageModel.SITE_ID, siteId);
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
		Page entity = siteService.save(page);
		return entity;
	}

	@Activate
	public void activate(ComponentContext ctx, Map<String, Object> config) {
		context = ctx.getBundleContext();
		init(context);
	}

	@Reference(target = "(modelClass=org.greenscape.site.Site)", policy = ReferencePolicy.DYNAMIC)
	public void setModelRegistryEntry(ModelRegistryEntry modelRegistryEntry) {
		this.modelRegistryEntry = modelRegistryEntry;
		init(context);
	}

	public void unsetModelRegistryEntry(ModelRegistryEntry modelRegistryEntry) {
		this.modelRegistryEntry = null;
	}

	@Reference(policy = ReferencePolicy.DYNAMIC)
	public void setSiteService(SiteService siteService) {
		this.siteService = siteService;
	}

	public void unsetSiteService(SiteService siteService) {
		this.siteService = null;
	}

	@Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
	public void setLogService(LogService logService) {
		this.logService = logService;
	}

	public void unsetLogService(LogService logService) {
		this.logService = null;
	}

	@SuppressWarnings("unchecked")
	private void init(BundleContext ctx) {
		if (ctx == null) {
			return;
		}
		try {
			clazz = (Class<? extends DocumentModel>) ctx.getBundle(modelRegistryEntry.getBundleId()).loadClass(
					modelRegistryEntry.getModelClass());
		} catch (Exception e) {
			logService.log(LogService.LOG_ERROR, e.getMessage(), e);
		}
	}

	private void copy(Site entity, SiteModelParam param) {
		entity.setActive(param.isActive());
		entity.setDefault(param.isDefault());
		entity.setHomeURL(param.getHomeURL().toLowerCase());
		entity.setName(param.getName());
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