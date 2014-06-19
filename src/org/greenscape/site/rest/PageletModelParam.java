package org.greenscape.site.rest;

public class PageletModelParam {

	private String pageletId;
	private String webletId;
	private String title;
	private Integer row;
	private Integer column;
	private Integer order;

	/**
	 * @return the pageletId
	 */
	public String getPageletId() {
		return pageletId;
	}

	/**
	 * @param pageletId
	 *            the pageletId to set
	 */
	public void setPageletId(String pageletId) {
		this.pageletId = pageletId;
	}

	/**
	 * @return the webletId
	 */
	public String getWebletId() {
		return webletId;
	}

	/**
	 * @param webletId
	 *            the webletId to set
	 */
	public void setWebletId(String webletId) {
		this.webletId = webletId;
	}

	/**
	 * @return the title
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * @param title
	 *            the title to set
	 */
	public void setTitle(String title) {
		this.title = title;
	}

	/**
	 * @return the row
	 */
	public Integer getRow() {
		return row;
	}

	/**
	 * @param row
	 *            the row to set
	 */
	public void setRow(Integer row) {
		this.row = row;
	}

	/**
	 * @return the column
	 */
	public Integer getColumn() {
		return column;
	}

	/**
	 * @param column
	 *            the column to set
	 */
	public void setColumn(Integer column) {
		this.column = column;
	}

	/**
	 * @return the order
	 */
	public Integer getOrder() {
		return order;
	}

	/**
	 * @param order
	 *            the order to set
	 */
	public void setOrder(Integer order) {
		this.order = order;
	}

}
