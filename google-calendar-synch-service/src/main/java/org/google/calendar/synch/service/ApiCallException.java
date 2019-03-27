package org.google.calendar.synch.service;

public class ApiCallException extends RuntimeException {

	public ApiCallException(Exception e) {
		super(e);
	}

}
