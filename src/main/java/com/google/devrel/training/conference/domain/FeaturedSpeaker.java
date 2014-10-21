package com.google.devrel.training.conference.domain;

import java.io.Serializable;
import java.util.List;

/**
 * A class used to model a featured speaker.  
 * 
 * A featured speaker has more than 1 session at a given conference.  For a featured speaker, we
 * would like to know the name of the speaker as well as the titles of the speaker's sessions.
 * 
 * This object will ultimately be stored in memcached, which is why it extends Serializable
 */
public class FeaturedSpeaker implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * speaker name
	 */
	private String name;
	
	/**
	 * name of the conference at which the speaker will be featured
	 */
	private String conferenceName;
	
	/**
	 * name of all speaker's sessions at the conference
	 */
	private List<String> sessionNames;
	
	public FeaturedSpeaker(String name, String conferenceName, List<String> sessionNames) {
		this.name = name;
		this.conferenceName = conferenceName;
		this.sessionNames = sessionNames;
	}
	
	public String getName() {
		return name;
	}
	
	public String getConferenceName() {
		return conferenceName;
	}
	
	public List<String> getSessionNames() {
		return sessionNames;
	}
}
