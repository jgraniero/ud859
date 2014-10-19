package com.google.devrel.training.conference.form;

import java.util.Set;

public class SpeakerForm {

	private String email;
	
	private String name;
	
	private String displayName;
	
	private Set<String> expertise;
	
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
