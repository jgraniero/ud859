package com.google.devrel.training.conference.form;

import java.util.Set;

/**
 * Object used to model data sent to the API for speaker creation
 */
public class SpeakerForm {

	/**
	 * speaker's email address
	 */
	private String email;
	
	/**
	 * speaker's name
	 */
	private String name;
	
	/**
	 * speaker's name as it should appear in the web ui
	 */
	private String displayName;
	
	/**
	 * areas of expertise for the speaker
	 */
	private Set<String> expertise;
	
	/**
	 * "about me" for the speaker
	 */
	private String about;
	
	private SpeakerForm() {}
	
	public SpeakerForm(String email, String name, String displayName, Set<String> expertise,
			String about) {
		this.email = email;
		this.name = name;
		this.displayName = displayName == null ? name : displayName;
		this.expertise = expertise;
		this.about = about;
	}
	
	public String getEmail() {
		return email;
	}
	
	public String getName() {
		return name;
	}
	
	public String getDisplayName() {
		return displayName;
	}
	
	public Set<String> getExpertise() {
		return expertise;
	}
	
	public String getAbout() {
		return about;
	}
}
