package com.google.devrel.training.conference.form;

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
