package org.greenscape.site.rest;

import java.util.ArrayList;
import java.util.Date;
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
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.greenscape.core.WebletItem;
import org.greenscape.core.model.Pagelet;
import org.greenscape.core.model.PageletModel;
import org.greenscape.core.service.Service;
import org.greenscape.persistence.ModelRegistryEntry;
import org.greenscape.persistence.annotations.Model;
import org.greenscape.site.Page;
import org.greenscape.site.PageModel;
import org.greenscape.web.rest.RestService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.log.LogService;

@Component(name = PageResource.FACTORY_DS, property = { Constants.SERVICE_RANKING + "=1000" })
public class PageResource implements RestService {
	static final String FACTORY_DS = "org.greenscape.site.rest.PageResource";
	private static final String PATH_DEF_PAGE_ID = "{pageId}";

	private Service service;
	private ModelRegistryEntry modelRegistryEntry;
	private Class<Page> clazz;
	private final List<WebletItem> weblets = new ArrayList<WebletItem>();

	private BundleContext context;
	private LogService logService;

	@Override
	public String getResourceName() {
		return clazz.getAnnotation(Model.class).name();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<Page> list(@Context UriInfo uriInfo) {
		List<Page> list = service.find(clazz, uriInfo.getQueryParameters());
		return list;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path(PATH_DEF_PAGE_ID)
	public PageModel getPage(@PathParam("pageId") String pageId) {
		Page page = null;
		try {
			page = service.find(clazz, pageId);
		} catch (Exception e) {
			logService.log(LogService.LOG_ERROR, e.getMessage(), e);
			throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity("Server error").build());
		}
		return page;
	}

	@DELETE
	@Path(PATH_DEF_PAGE_ID)
	public void deleteModel(@PathParam("pageId") String id) {
		service.delete(clazz, id);
	}

	/*
	 * Pagelet APIs
	 */

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path(PATH_DEF_PAGE_ID + "/pagelet")
	public List<Pagelet> getPagelets(@PathParam("pageId") String pageId) {
		List<Pagelet> pagelets = service.find(Pagelet.class, Pagelet.PAGE_ID, pageId);
		for (Pagelet pagelet : pagelets) {
			for (WebletItem weblet : weblets) {
				if (pagelet.getWebletId().equals(weblet.getId())) {
					pagelet.setWeblet(weblet);
					break;
				}
			}
		}
		return pagelets;
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Path(PATH_DEF_PAGE_ID + "/pagelet")
	public PageletModel addPagelet(@PathParam("pageId") String pageId, PageletModelParam pageletParam) {
		// validate pageId
		Page page = service.find(Page.class, pageId);
		if (page == null) {
			throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity("Page does not exist").build());
		}
		WebletItem weblet = null;
		for (WebletItem item : weblets) {
			if (item.getId().equals(pageletParam.getWebletId())) {
				weblet = item;
				break;
			}
		}
		if (weblet == null) {
			throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity("Weblet does not exist").build());
		}
		List<Pagelet> pagelets = service.find(Pagelet.class, Pagelet.PAGE_ID, pageId);
		// validate weblet is multi-instanceable on same page
		validateWebletInstanceability(weblet, pagelets);
		reorderPagelets(pagelets, pageletParam);

		Pagelet pagelet = new Pagelet();
		pagelet.setActive(true);
		pagelet.setColumn(pageletParam.getColumn());
		Date now = new Date();
		pagelet.setCreateDate(now).setModifiedDate(now);
		pagelet.setOrder(pageletParam.getOrder());
		pagelet.setOrganizationId(page.getOrganizationId());
		pagelet.setPageId(pageId).setRow(pageletParam.getRow()).setTitle(weblet.getTitle())
		.setWebletId(pageletParam.getWebletId());
		service.save(pagelet);
		return pagelet;
	}

	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Path(PATH_DEF_PAGE_ID + "/pagelet/{pageletId}")
	public PageletModel updatePagelet(@PathParam("pageId") String pageId, @PathParam("pageletId") String pageletId,
			PageletModelParam pageletParam) {
		// validate pageId
		Page page = service.find(Page.class, pageId);
		if (page == null) {
			throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity("Page does not exist").build());
		}
		if (pageletParam.getRow() != null && pageletParam.getColumn() != null && pageletParam.getOrder() != null) {
			List<Pagelet> pagelets = service.find(Pagelet.class, Pagelet.PAGE_ID, pageId);
			reorderPagelets(pagelets, pageletParam);
		}
		Pagelet pagelet = service.find(Pagelet.class, pageletId);
		Date now = new Date();
		pagelet.setModifiedDate(now);
		if (pageletParam.getRow() != null) {
			pagelet.setRow(pageletParam.getRow());
		}
		if (pageletParam.getColumn() != null) {
			pagelet.setColumn(pageletParam.getColumn());
		}
		if (pageletParam.getOrder() != null) {
			pagelet.setOrder(pageletParam.getOrder());
		}
		if (pageletParam.getTitle() != null) {
			pagelet.setTitle(pageletParam.getTitle());
		}

		service.update(pagelet);
		return pagelet;
	}

	@DELETE
	@Path(PATH_DEF_PAGE_ID + "/pagelet/{pageletId}")
	public void removePagelet(@PathParam("pageId") String pageId, @PathParam("pageletId") String pageletId,
			@QueryParam("row") int row, @QueryParam("column") int column, @QueryParam("order") int order) {
		// validate pageId
		Page page = service.find(Page.class, pageId);
		if (page == null) {
			throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity("Page does not exist").build());
		}
		service.delete(Pagelet.class, pageletId);
		List<Pagelet> pagelets = service.find(Pagelet.class, Pagelet.PAGE_ID, pageId);
		for (Pagelet pg : pagelets) {
			if (pg.getRow() == row && pg.getColumn() == column && pg.getOrder() > order) {
				pg.setOrder(pg.getOrder().intValue() - 1);
				service.update(pg);
			}
		}
	}

	@Activate
	public void activate(ComponentContext ctx, Map<String, Object> config) {
		context = ctx.getBundleContext();
		init(context);
	}

	@Modified
	public void modified(ComponentContext ctx, Map<String, Object> config) {
		context = ctx.getBundleContext();
		init(context);
	}

	@Reference(target = "(modelClass=org.greenscape.site.Page)", policy = ReferencePolicy.DYNAMIC)
	public void setModelRegistryEntry(ModelRegistryEntry modelRegistryEntry) {
		this.modelRegistryEntry = modelRegistryEntry;
	}

	public void unsetModelRegistryEntry(ModelRegistryEntry modelRegistryEntry) {
		this.modelRegistryEntry = null;
	}

	@Reference(policy = ReferencePolicy.DYNAMIC)
	public void setService(Service service) {
		this.service = service;
	}

	public void unsetService(Service service) {
		this.service = null;
	}

	@Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
	public void setWeblet(WebletItem weblet) {
		weblets.add(weblet);
	}

	public void unsetWeblet(WebletItem weblet) {
		weblets.remove(weblet);
	}

	@Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
	public void setLogService(LogService logService) {
		this.logService = logService;
	}

	public void unsetLogService(LogService logService) {
		this.logService = null;
	}

	@Override
	public String toString() {
		return clazz.getName();
	}

	@SuppressWarnings("unchecked")
	private void init(BundleContext ctx) {
		try {
			clazz = (Class<Page>) ctx.getBundle(modelRegistryEntry.getBundleId()).loadClass(
					modelRegistryEntry.getModelClass());
		} catch (Exception e) {
			logService.log(LogService.LOG_ERROR, e.getMessage(), e);
		}
	}

	private void validateWebletInstanceability(WebletItem weblet, List<Pagelet> pagelets) {
		if (!weblet.isInstanceable()) {
			for (Pagelet pagelet : pagelets) {
				if (pagelet.getWebletId().equals(weblet.getId())) {
					throw new WebApplicationException(Response.status(Status.PRECONDITION_FAILED)
							.entity("Weblet already instantiated on this page").build());
				}
			}
		}
	}

	private void reorderPagelets(List<Pagelet> pagelets, PageletModelParam pageletParam) {
		// first push other pagelets in new (row,col) below
		Pagelet old = null;
		for (Pagelet pg : pagelets) {
			if (pg.getRow() == pageletParam.getRow() && pg.getColumn() == pageletParam.getColumn()
					&& pg.getOrder() >= pageletParam.getOrder()) {
				pg.setOrder(pg.getOrder() + 1);
				service.save(pg);
			}
			if (pageletParam.getPageletId() != null && pageletParam.getPageletId().equals(pg.getModelId())) {
				old = pg;
			}
		}
		// pull pagelets in old (row,col) up
		if (old != null) {
			for (Pagelet pg : pagelets) {
				if (pg.getRow() == old.getRow() && pg.getColumn() == old.getColumn() && pg.getOrder() > old.getOrder()) {
					pg.setOrder(pg.getOrder() - 1);
					service.save(pg);
				}
			}
		}
	}

}
