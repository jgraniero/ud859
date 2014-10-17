package com.google.devrel.training.conference.domain;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import com.google.devrel.training.conference.form.SessionForm;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Parent;

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
	 * Title for the session
	 */
	private String name;
	
	/**
	 * The date/time the session starts
	 */
	private Date startDate;
	
	/**
	 * The date/time the session ends
	 */
	private Date endDate;
	
	/**
	 * Format of the session (workshop, lecture, etc.)
	 */
	// TODO immutable list of possible formats
	private String typeOfSession;
	
	@Parent
	private Key<Conference> conferenceKey;
	
	/**
	 * List of speakers for the session
	 */
	private Set<String> speakers = new HashSet<>();
	
	/**
	 * Location of the session
	 */
	private String location;
	
	/**
	 * I haven't the slightest idea what this is or what data type it's supposed to be.
	 */
	private Set<String> highlights;
	
	private Session() {}
	
	public Session(final long id, final Key<Conference> conferenceKey, final SessionForm sessionForm) {
		// precondition checks?  see Conference.java
		this.id = id;
		this.conferenceKey = conferenceKey;
		updateWithSessionForm(sessionForm);
	}
	
	public void updateWithSessionForm(SessionForm sessionForm) {
		name = sessionForm.getName();
		startDate = sessionForm.getStartDate();
		endDate = sessionForm.getEndDate();
		typeOfSession = sessionForm.getTypeOfSession();
		speakers = sessionForm.getSpeakers();
		location = sessionForm.getLocation();
		highlights = sessionForm.getHighlights();
	}

	/**
	 * @return duration of the session in seconds
	 */
	public long getDuration() {
		return (endDate.getTime() - startDate.getTime()) / 1000;
	}
	
	public String getTypeOfSession() {
		return typeOfSession;
	}
}
