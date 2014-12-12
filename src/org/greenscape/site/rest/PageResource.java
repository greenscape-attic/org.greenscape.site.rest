package org.greenscape.site.rest;

import java.util.Date;
import java.util.List;

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

import org.greenscape.core.ResourceRegistry;
import org.greenscape.core.WebletResource;
import org.greenscape.core.model.Page;
import org.greenscape.core.model.PageModel;
import org.greenscape.core.model.Pagelet;
import org.greenscape.core.model.PageletModel;
import org.greenscape.core.service.Service;
import org.greenscape.web.rest.AbstractRestService;
import org.greenscape.web.rest.RestService;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.log.LogService;

@Component(name = PageResource.FACTORY_DS, property = { Constants.SERVICE_RANKING + "=1000" })
public class PageResource extends AbstractRestService implements RestService {
	static final String FACTORY_DS = "org.greenscape.site.rest.PageResource";
	private static final String MODEL_PAGE = "page";
	private static final String MODEL_PAGELET = "pagelet";
	private static final String PATH_DEF_PAGE_ID = "{pageId}";

	private LogService logService;

	@Override
	public String getResourceName() {
		return MODEL_PAGE;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path(PATH_DEF_PAGE_ID)
	public PageModel getPage(@PathParam("pageId") String pageId) {
		Page page = null;
		try {
			page = service.findByModelId(MODEL_PAGE, pageId);
		} catch (Exception e) {
			logService.log(LogService.LOG_ERROR, e.getMessage(), e);
			throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity("Server error").build());
		}
		return page;
	}

	@DELETE
	@Path(PATH_DEF_PAGE_ID)
	public void deleteModel(@Context UriInfo uriInfo, @PathParam("pageId") String id) {
		String resourceName = uriInfo.getPathParameters().get("name").get(0);
		checkPermission(resourceName + ":1:" + "DELETE");
		service.delete(MODEL_PAGE, id);
	}

	/*
	 * Pagelet APIs
	 */

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path(PATH_DEF_PAGE_ID + "/pagelet")
	public List<Pagelet> getPagelets(@PathParam("pageId") String pageId) {
		List<Pagelet> pagelets = service.find(MODEL_PAGELET, Pagelet.PAGE_ID, pageId);
		List<WebletResource> weblets = resourceRegistry.getResources(WebletResource.class);
		for (Pagelet pagelet : pagelets) {
			for (WebletResource weblet : weblets) {
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
		Page page = service.findByModelId(MODEL_PAGE, pageId);
		if (page == null) {
			throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity("Page does not exist").build());
		}
		List<WebletResource> weblets = resourceRegistry.getResources(WebletResource.class);
		WebletResource weblet = null;
		for (WebletResource item : weblets) {
			if (item.getId().equals(pageletParam.getWebletId())) {
				weblet = item;
				break;
			}
		}
		if (weblet == null) {
			throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity("Weblet does not exist").build());
		}
		List<Pagelet> pagelets = service.find(MODEL_PAGELET, Pagelet.PAGE_ID, pageId);
		// validate weblet is multi-instanceable on same page
		validateWebletInstanceability(weblet, pagelets);
		reorderPagelets(pagelets, pageletParam);

		Pagelet pagelet = new Pagelet();
		pagelet.setActive(true);
		pagelet.setColumn(pageletParam.getColumn());
		Date now = new Date();
		pagelet.setCreatedDate(now).setModifiedDate(now);
		pagelet.setOrder(pageletParam.getOrder());
		pagelet.setOrganizationId(page.getOrganizationId());
		pagelet.setPageId(pageId).setRow(pageletParam.getRow()).setTitle(weblet.getTitle())
		.setWebletId(pageletParam.getWebletId());
		service.save("pagelet", pagelet);
		return pagelet;
	}

	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Path(PATH_DEF_PAGE_ID + "/pagelet/{pageletId}")
	public PageletModel updatePagelet(@PathParam("pageId") String pageId, @PathParam("pageletId") String pageletId,
			PageletModelParam pageletParam) {
		// validate pageId
		Page page = service.findByModelId(MODEL_PAGE, pageId);
		if (page == null) {
			throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity("Page does not exist").build());
		}
		if (pageletParam.getRow() != null && pageletParam.getColumn() != null && pageletParam.getOrder() != null) {
			List<Pagelet> pagelets = service.find(MODEL_PAGELET, Pagelet.PAGE_ID, pageId);
			reorderPagelets(pagelets, pageletParam);
		}
		Pagelet pagelet = service.findByModelId(MODEL_PAGELET, pageletId);
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
		Page page = service.findByModelId(MODEL_PAGE, pageId);
		if (page == null) {
			throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity("Page does not exist").build());
		}
		service.delete(MODEL_PAGELET, pageletId);
		List<Pagelet> pagelets = service.find(MODEL_PAGELET, Pagelet.PAGE_ID, pageId);
		for (Pagelet pg : pagelets) {
			if (pg.getRow() == row && pg.getColumn() == column && pg.getOrder() > order) {
				pg.setOrder(pg.getOrder().intValue() - 1);
				service.update(pg);
			}
		}
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

	private void validateWebletInstanceability(WebletResource weblet, List<Pagelet> pagelets) {
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
