package org.fogbowcloud.manager.occi.request;

public enum RequestState {

	/**
	 * Open: The request is not fulfilled.
	 * 
	 * Failed: The request failed because bad parameters were specified.
	 * 
	 * Fulfilled: The request is currently active (fulfilled) and has an
	 * associated Instance.
	 * 
	 * Canceled: The request is canceled because the request went past its
	 * expiration date.
	 * 
	 * Closed: The request either completed (a Instance was launched and
	 * subsequently was interrupted or terminated), or was not fulfilled within
	 * the period specified.
	 */
	OPEN("open"), FAILED("failed"), FULFILLED("fulfilled"), CANCELED("canceled"), CLOSED("closed");

	private String value;

	private RequestState(String value) {
		this.value = value;
	}

	public String getValue() {
		return this.value;
	}
}
