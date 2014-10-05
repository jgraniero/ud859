package com.google.devrel.training.conference.domain;

import java.util.Date;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

/**
 * Stores a conference's session information
 */
@Entity
public class Session {

	/**
	 * Use datastore's automatic id assignment for Sessions
	 */
	@Id
	private Long id;
	
	/**
	 * The start time for the session
	 */
	private Date startTime;
	
	/**
	 * Duration in number of minutes
	 */
	private Integer duration;
	
	/**
	 * Format of the session (workshop, letcture, etc.)
	 */
	// TODO immutable list of possible formats
	private String format;
	
	/**
	 * Location of the session
	 */
	private String location;
	
	// todo
	public Session() {
		
	}
	
	public String getFormat() {
		return format;
	}
}
