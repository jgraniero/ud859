package com.google.devrel.training.conference.form;

/**
 * Very simple object to model data sent to the API for retrieving speakers from datastore
 */
public class SpeakerQueryForm {

	private String email;
	
	private String name;
	
	private SpeakerQueryForm() {}
	
	public SpeakerQueryForm(String email, String name) {
		this.email = email;
		this.name = name;
	}
	
	public String getEmail() {
		return email;
	}
	
	public String getName() {
		return name;
	}
}
