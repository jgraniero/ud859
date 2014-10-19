package com.google.devrel.training.conference.domain;

import java.util.Set;

import com.google.appengine.repackaged.com.google.api.client.util.Preconditions;
import com.google.devrel.training.conference.form.SpeakerForm;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;

/**
 * Stores information about a session speaker
 *
 */
@Entity
public class Speaker {

	/**
	 * email address will serve as the speaker's id
	 */
	@Id
	private String email;
	
	/**
	 * The speaker's name, which might be different than displayName
	 */
	@Index
	private String name;
	
	/**
	 * The speaker's name as it will be displayed on the site
	 */
	private String displayName;
	
	/**
	 * The speaker's areas of expertise
	 */
	@Index
	private Set<String> expertise;
	
	/**
	 * A description of the speaker
	 */
	private String about;
	
	@SuppressWarnings("unused")
	private Speaker() {}
	
	public Speaker(SpeakerForm speakerForm) {
		Preconditions.checkNotNull(speakerForm.getEmail(), "Email is required");
		updateWithSpeakerForm(speakerForm);
	}
	
	public void updateWithSpeakerForm(SpeakerForm speakerForm) {
		email = speakerForm.getEmail();
		name = speakerForm.getName();
		displayName = speakerForm.getDisplayName();
		expertise = speakerForm.getExpertise();
		about = speakerForm.getAbout();	
	}
	
	public String getWebsafeKey() {
		return Key.create(Speaker.class, email).getString();
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
