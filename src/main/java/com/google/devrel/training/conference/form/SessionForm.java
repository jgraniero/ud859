package com.google.devrel.training.conference.form;

import java.util.Date;
import java.util.Set;

public class SessionForm {

	/**
	 * Session name
	 */
	private String name;
	
	/**
	 * Conference highlights
	 */
	private Set<String> highlights;
	
	/**
	 * Session speaker
	 */
	private String speakerKey;
	
	/**
	 * The type of the session (workshop, q&a, etc.)
	 */
	private String typeOfSession;
	
	/**
	 * The session start date/time
	 */
	private Date startDate;
	
	/**
	 * The sesseion end date/time
	 */
	private Date endDate;
	
	/**
	 * The location of the session
	 */
	private String location;
	
	@SuppressWarnings("unused")
	private SessionForm() {}
	
	public SessionForm(
			String name, 
			Set<String> highlights, 
			String speakerKey, 
			String typeOfSession, 
			Date startDate,
			Date endDate) {
		this.name = name;
		this.highlights = highlights;
		this.speakerKey = speakerKey;
		this.typeOfSession = typeOfSession;
		this.startDate = startDate;
		this.endDate = endDate;
	}
	
	public String getName() {
		return name;
	}
	
	public Set<String> getHighlights() {
		return highlights;
	}
	
	public String getSpeakerKey() {
		return speakerKey;
	}
	
	public String getTypeOfSession() {
		return typeOfSession;
	}
	
	public Date getStartDate() {
		return startDate;
	}
	
	public Date getEndDate() {
		return endDate;
	}
	
	public String getLocation() {
		return location;
	}
}