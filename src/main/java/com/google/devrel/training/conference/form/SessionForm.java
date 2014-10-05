package com.google.devrel.training.conference.form;

import java.util.Date;

public class SessionForm {

	/**
	 * Session name
	 */
	private String name;
	
	/**
	 * Conference highlights
	 */
	private String highlights;
	
	/**
	 * Session speaker
	 */
	private String speaker;
	
	/**
	 * Duration of the session in minutes
	 */
	private int duration;
	
	/**
	 * The type of the session (workshop, q&a, etc.)
	 */
	private String typeOfSession;
	
	/**
	 * The session date
	 */
	private Date date;
	
	/**
	 * The start time of the session
	 */
	private Date startTime;
	
	public SessionForm(String name, String highlights, String speaker, int duration,
			String typeOfSession, Date date) {
		this.name = name;
		this.highlights = highlights;
		this.speaker = speaker;
		this.duration = duration;
		this.typeOfSession = typeOfSession;
		this.date = date;
	}
	
	public String getName() {
		return name;
	}
	
	public String getHighlights() {
		return highlights;
	}
	
	public String getSpeaker() {
		return speaker;
	}
	
	public int getDuration() {
		return duration;
	}
	
	public String getTypeOfSession() {
		return typeOfSession;
	}
	
	public Date getDate() {
		return date;
	}
	
	public Date getStartTime() {
		return startTime;
	}
}