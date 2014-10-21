package com.google.devrel.training.conference.spi;

import static com.google.devrel.training.conference.service.OfyService.factory;
import static com.google.devrel.training.conference.service.OfyService.ofy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Named;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.response.ConflictException;
import com.google.api.server.spi.response.ForbiddenException;
import com.google.api.server.spi.response.NotFoundException;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.users.User;
import com.google.devrel.training.conference.Constants;
import com.google.devrel.training.conference.domain.Announcement;
import com.google.devrel.training.conference.domain.AppEngineUser;
import com.google.devrel.training.conference.domain.Conference;
import com.google.devrel.training.conference.domain.FeaturedSpeaker;
import com.google.devrel.training.conference.domain.Profile;
import com.google.devrel.training.conference.domain.Session;
import com.google.devrel.training.conference.domain.Speaker;
import com.google.devrel.training.conference.form.ConferenceForm;
import com.google.devrel.training.conference.form.ConferenceQueryForm;
import com.google.devrel.training.conference.form.ProfileForm;
import com.google.devrel.training.conference.form.ProfileForm.TeeShirtSize;
import com.google.devrel.training.conference.form.SessionForm;
import com.google.devrel.training.conference.form.SpeakerForm;
import com.google.devrel.training.conference.form.SpeakerQueryForm;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.Work;

/**
 * Defines conference APIs.
 */
@Api(
        name = "conference",
        version = "v1",
        scopes = { Constants.EMAIL_SCOPE },
        clientIds = { Constants.WEB_CLIENT_ID, Constants.ANDROID_CLIENT_ID,
                Constants.API_EXPLORER_CLIENT_ID},
        audiences = {Constants.ANDROID_AUDIENCE},
        description = "Conference Central API for creating and querying conferences," +
                " and for creating and getting user Profiles"
)
public class ConferenceApi {

    private static final Logger LOG = Logger.getLogger(ConferenceApi.class.getName());

    private static String extractDefaultDisplayNameFromEmail(String email) {
        return email == null ? null : email.substring(0, email.indexOf("@"));
    }

    private static Profile getProfileFromUser(User user, String userId) {
        // First fetch it from the datastore.
        Profile profile = ofy().load().key(
                Key.create(Profile.class, userId)).now();
        if (profile == null) {
            // Create a new Profile if not exist.
            String email = user.getEmail();
            profile = new Profile(userId,
                    extractDefaultDisplayNameFromEmail(email), email, TeeShirtSize.NOT_SPECIFIED);
        }
        return profile;
    }

    /**
     * This is an ugly workaround for null userId for Android clients.
     *
     * @param user A User object injected by the cloud endpoints.
     * @return the App Engine userId for the user.
     */
    private static String getUserId(User user) {
        String userId = user.getUserId();
        if (userId == null) {
            LOG.info("userId is null, so trying to obtain it from the datastore.");
            AppEngineUser appEngineUser = new AppEngineUser(user);
            ofy().save().entity(appEngineUser).now();
            // Begin new session for not using session cache.
            Objectify objectify = ofy().factory().begin();
            AppEngineUser savedUser = objectify.load().key(appEngineUser.getKey()).now();
            userId = savedUser.getUser().getUserId();
            LOG.info("Obtained the userId: " + userId);
        }
        return userId;
    }

    /**
     * Just a wrapper for Boolean.
     */
    public static class WrappedBoolean {

        private final Boolean result;

        public WrappedBoolean(Boolean result) {
            this.result = result;
        }

        public Boolean getResult() {
            return result;
        }
    }

    /**
     * A wrapper class that can embrace a generic result or some kind of exception.
     *
     * Use this wrapper class for the return type of objectify transaction.
     * <pre>
     * {@code
     * // The transaction that returns Conference object.
     * TxResult<Conference> result = ofy().transact(new Work<TxResult<Conference>>() {
     *     public TxResult<Conference> run() {
     *         // Code here.
     *         // To throw 404
     *         return new TxResult<>(new NotFoundException("No such conference"));
     *         // To return a conference.
     *         Conference conference = somehow.getConference();
     *         return new TxResult<>(conference);
     *     }
     * }
     * // Actually the NotFoundException will be thrown here.
     * return result.getResult();
     * </pre>
     *
     * @param <ResultType> The type of the actual return object.
     */
    private static class TxResult<ResultType> {

        private ResultType result;

        private Throwable exception;

        private TxResult(ResultType result) {
            this.result = result;
        }

        private TxResult(Throwable exception) {
            if (exception instanceof NotFoundException ||
                    exception instanceof ForbiddenException ||
                    exception instanceof ConflictException ||
                    exception instanceof IllegalArgumentException) {
                this.exception = exception;
            } else {
                throw new IllegalArgumentException("Exception not supported.");
            }
        }

        private ResultType getResult() throws NotFoundException, ForbiddenException, ConflictException {
            if (exception instanceof NotFoundException) {
                throw (NotFoundException) exception;
            }
            if (exception instanceof ForbiddenException) {
                throw (ForbiddenException) exception;
            }
            if (exception instanceof ConflictException) {
                throw (ConflictException) exception;
            }
            return result;
        }
    }

    /**
     * Returns a Profile object associated with the given user object. The cloud endpoints system
     * automatically inject the User object.
     *
     * @param user A User object injected by the cloud endpoints.
     * @return Profile object.
     * @throws UnauthorizedException when the User object is null.
     */
    @ApiMethod(name = "getProfile", path = "profile", httpMethod = HttpMethod.GET)
    public Profile getProfile(final User user) throws UnauthorizedException {
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        return ofy().load().key(Key.create(Profile.class, getUserId(user))).now();
    }

    /**
     * Creates or updates a Profile object associated with the given user object.
     *
     * @param user A User object injected by the cloud endpoints.
     * @param profileForm A ProfileForm object sent from the client form.
     * @return Profile object just created.
     * @throws UnauthorizedException when the User object is null.
     */
    @ApiMethod(name = "saveProfile", path = "profile", httpMethod = HttpMethod.POST)
    public Profile saveProfile(final User user, final ProfileForm profileForm)
            throws UnauthorizedException {
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        String displayName = profileForm.getDisplayName();
        TeeShirtSize teeShirtSize = profileForm.getTeeShirtSize();

        Profile profile = ofy().load().key(Key.create(Profile.class, getUserId(user))).now();
        if (profile == null) {
            // Populate displayName and teeShirtSize with the default values if null.
            if (displayName == null) {
                displayName = extractDefaultDisplayNameFromEmail(user.getEmail());
            }
            if (teeShirtSize == null) {
                teeShirtSize = TeeShirtSize.NOT_SPECIFIED;
            }
            profile = new Profile(getUserId(user), displayName, user.getEmail(), teeShirtSize);
        } else {
            profile.update(displayName, teeShirtSize);
        }
        ofy().save().entity(profile).now();
        return profile;
    }

    /**
     * Creates a new Conference object and stores it to the datastore.
     *
     * @param user A user who invokes this method, null when the user is not signed in.
     * @param conferenceForm A ConferenceForm object representing user's inputs.
     * @return A newly created Conference Object.
     * @throws UnauthorizedException when the user is not signed in.
     */
    @ApiMethod(name = "createConference", path = "conference", httpMethod = HttpMethod.POST)
    public Conference createConference(final User user, final ConferenceForm conferenceForm)
        throws UnauthorizedException {
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        // Allocate Id first, in order to make the transaction idempotent.
        Key<Profile> profileKey = Key.create(Profile.class, getUserId(user));
        final Key<Conference> conferenceKey = factory().allocateId(profileKey, Conference.class);
        final long conferenceId = conferenceKey.getId();
        final Queue queue = QueueFactory.getDefaultQueue();
        final String userId = getUserId(user);
        // Start a transaction.
        Conference conference = ofy().transact(new Work<Conference>() {
            @Override
            public Conference run() {
                // Fetch user's Profile.
                Profile profile = getProfileFromUser(user, userId);
                Conference conference = new Conference(conferenceId, userId, conferenceForm);
                // Save Conference and Profile.
                ofy().save().entities(conference, profile).now();
                queue.add(ofy().getTransaction(),
                        TaskOptions.Builder.withUrl("/tasks/send_confirmation_email")
                        .param("email", profile.getMainEmail())
                        .param("conferenceInfo", conference.toString()));
                return conference;
            }
        });
        return conference;
    }

    /**
     * Updates the existing Conference with the given conferenceId.
     *
     * @param user A user who invokes this method, null when the user is not signed in.
     * @param conferenceForm A ConferenceForm object representing user's inputs.
     * @param websafeConferenceKey The String representation of the Conference key.
     * @return Updated Conference object.
     * @throws UnauthorizedException when the user is not signed in.
     * @throws NotFoundException when there is no Conference with the given conferenceId.
     * @throws ForbiddenException when the user is not the owner of the Conference.
     */
    @ApiMethod(
            name = "updateConference",
            path = "conference/{websafeConferenceKey}",
            httpMethod = HttpMethod.PUT
    )
    public Conference updateConference(final User user, final ConferenceForm conferenceForm,
                                       @Named("websafeConferenceKey")
                                       final String websafeConferenceKey)
            throws UnauthorizedException, NotFoundException, ForbiddenException, ConflictException {
        // If not signed in, throw a 401 error.
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        final String userId = getUserId(user);
        // Update the conference with the conferenceForm sent from the client.
        // Need a transaction because we need to safely preserve the number of allocated seats.
        TxResult<Conference> result = ofy().transact(new Work<TxResult<Conference>>() {
            @Override
            public TxResult<Conference> run() {
                // If there is no Conference with the id, throw a 404 error.
                Key<Conference> conferenceKey = Key.create(websafeConferenceKey);
                Conference conference = ofy().load().key(conferenceKey).now();
                if (conference == null) {
                    return new TxResult<>(
                            new NotFoundException("No Conference found with the key: "
                                    + websafeConferenceKey));
                }
                // If the user is not the owner, throw a 403 error.
                Profile profile = ofy().load().key(Key.create(Profile.class, userId)).now();
                if (profile == null ||
                        !conference.getOrganizerUserId().equals(userId)) {
                    return new TxResult<>(
                            new ForbiddenException("Only the owner can update the conference."));
                }
                conference.updateWithConferenceForm(conferenceForm);
                ofy().save().entity(conference).now();
                return new TxResult<>(conference);
            }
        });
        // NotFoundException or ForbiddenException is actually thrown here.
        return result.getResult();
    }

    @ApiMethod(
            name = "getAnnouncement",
            path = "announcement",
            httpMethod = HttpMethod.GET
    )
    public Announcement getAnnouncement() {
        MemcacheService memcacheService = MemcacheServiceFactory.getMemcacheService();
        Object message = memcacheService.get(Constants.MEMCACHE_ANNOUNCEMENTS_KEY);
        if (message != null) {
            return new Announcement(message.toString());
        }
        return null;
    }

    /**
     * Returns a Conference object with the given conferenceId.
     *
     * @param websafeConferenceKey The String representation of the Conference Key.
     * @return a Conference object with the given conferenceId.
     * @throws NotFoundException when there is no Conference with the given conferenceId.
     */
    @ApiMethod(
            name = "getConference",
            path = "conference/{websafeConferenceKey}",
            httpMethod = HttpMethod.GET
    )
    public Conference getConference(
            @Named("websafeConferenceKey") final String websafeConferenceKey)
            throws NotFoundException {
        Key<Conference> conferenceKey = Key.create(websafeConferenceKey);
        Conference conference = ofy().load().key(conferenceKey).now();
        if (conference == null) {
            throw new NotFoundException("No Conference found with key: " + websafeConferenceKey);
        }
        return conference;
    }

    /**
     * Returns a collection of Conference Object that the user is going to attend.
     *
     * @param user An user who invokes this method, null when the user is not signed in.
     * @return a Collection of Conferences that the user is going to attend.
     * @throws UnauthorizedException when the User object is null.
     */
    @ApiMethod(
            name = "getConferencesToAttend",
            path = "getConferencesToAttend",
            httpMethod = HttpMethod.GET
    )
    public Collection<Conference> getConferencesToAttend(final User user)
            throws UnauthorizedException, NotFoundException {
        // If not signed in, throw a 401 error.
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        Profile profile = ofy().load().key(Key.create(Profile.class, getUserId(user))).now();
        if (profile == null) {
            throw new NotFoundException("Profile doesn't exist.");
        }
        List<String> keyStringsToAttend = profile.getConferenceKeysToAttend();
        List<Key<Conference>> keysToAttend = new ArrayList<>();
        for (String keyString : keyStringsToAttend) {
            keysToAttend.add(Key.<Conference>create(keyString));
        }
        return ofy().load().keys(keysToAttend).values();
    }

    /**
     * Queries against the datastore with the given filters and returns the result.
     *
     * Normally this kind of method is supposed to get invoked by a GET HTTP method,
     * but we do it with POST, in order to receive conferenceQueryForm Object via the POST body.
     *
     * @param conferenceQueryForm A form object representing the query.
     * @return A List of Conferences that match the query.
     */
    @ApiMethod(
            name = "queryConferences",
            path = "queryConferences",
            httpMethod = HttpMethod.POST
    )
    public List<Conference> queryConferences(ConferenceQueryForm conferenceQueryForm) {
        Iterable<Conference> conferenceIterable = conferenceQueryForm.getQuery();
        List<Conference> result = new ArrayList<>(0);
        List<Key<Profile>> organizersKeyList = new ArrayList<>(0);
        for (Conference conference : conferenceIterable) {
            organizersKeyList.add(Key.create(Profile.class, conference.getOrganizerUserId()));
            result.add(conference);
        }
        // To avoid separate datastore gets for each Conference, pre-fetch the Profiles.
        ofy().load().keys(organizersKeyList);
        return result;
    }

    /**
     * Returns a list of Conferences that the user created.
     * In order to receive the websafeConferenceKey via the JSON params, uses a POST method.
     *
     * @param user An user who invokes this method, null when the user is not signed in.
     * @return a list of Conferences that the user created.
     * @throws UnauthorizedException when the user is not signed in.
     */
    @ApiMethod(
            name = "getConferencesCreated",
            path = "getConferencesCreated",
            httpMethod = HttpMethod.POST
    )
    public List<Conference> getConferencesCreated(final User user) throws UnauthorizedException {
        // If not signed in, throw a 401 error.
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        String userId = getUserId(user);
        return ofy().load().type(Conference.class)
                .ancestor(Key.create(Profile.class, userId))
                .order("name").list();
    }

    /**
     * Registers to the specified Conference.
     *
     * @param user An user who invokes this method, null when the user is not signed in.
     * @param websafeConferenceKey The String representation of the Conference Key.
     * @return Boolean true when success, otherwise false
     * @throws UnauthorizedException when the user is not signed in.
     * @throws NotFoundException when there is no Conference with the given conferenceId.
     */
    @ApiMethod(
            name = "registerForConference",
            path = "conference/{websafeConferenceKey}/registration",
            httpMethod = HttpMethod.POST
    )
    public WrappedBoolean registerForConference(final User user,
                                         @Named("websafeConferenceKey")
                                         final String websafeConferenceKey)
        throws UnauthorizedException, NotFoundException, ForbiddenException, ConflictException {
        // If not signed in, throw a 401 error.
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        final String userId = getUserId(user);
        TxResult<Boolean> result = ofy().transact(new Work<TxResult<Boolean>>() {
            @Override
            public TxResult<Boolean> run() {
                Key<Conference> conferenceKey = Key.create(websafeConferenceKey);
                Conference conference = ofy().load().key(conferenceKey).now();
                // 404 when there is no Conference with the given conferenceId.
                if (conference == null) {
                    return new TxResult<>(new NotFoundException(
                            "No Conference found with key: " + websafeConferenceKey));
                }
                // Registration happens here.
                Profile profile = getProfileFromUser(user, userId);
                if (profile.getConferenceKeysToAttend().contains(websafeConferenceKey)) {
                    return new TxResult<>(new ConflictException("You have already registered for this conference"));
                } else if (conference.getSeatsAvailable() <= 0) {
                    return new TxResult<>(new ConflictException("There are no seats available."));
                } else {
                    profile.addToConferenceKeysToAttend(websafeConferenceKey);
                    conference.bookSeats(1);
                    ofy().save().entities(profile, conference).now();
                    return new TxResult<>(true);
                }
            }
        });
        // NotFoundException is actually thrown here.
        return new WrappedBoolean(result.getResult());
    }

    /**
     * Unregister from the specified Conference.
     *
     * @param user An user who invokes this method, null when the user is not signed in.
     * @param websafeConferenceKey The String representation of the Conference Key to unregister
     *                             from.
     * @return Boolean true when success, otherwise false.
     * @throws UnauthorizedException when the user is not signed in.
     * @throws NotFoundException when there is no Conference with the given conferenceId.
     */
    @ApiMethod(
            name = "unregisterFromConference",
            path = "conference/{websafeConferenceKey}/registration",
            httpMethod = HttpMethod.DELETE
    )
    public WrappedBoolean unregisterFromConference(final User user,
                                            @Named("websafeConferenceKey")
                                            final String websafeConferenceKey)
            throws UnauthorizedException, NotFoundException, ForbiddenException, ConflictException {
        // If not signed in, throw a 401 error.
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        final String userId = getUserId(user);
        TxResult<Boolean> result = ofy().transact(new Work<TxResult<Boolean>>() {
            @Override
            public TxResult<Boolean> run() {
                Key<Conference> conferenceKey = Key.create(websafeConferenceKey);
                Conference conference = ofy().load().key(conferenceKey).now();
                // 404 when there is no Conference with the given conferenceId.
                if (conference == null) {
                    return new TxResult<>(new NotFoundException(
                            "No Conference found with key: " + websafeConferenceKey));
                }
                // Un-registering from the Conference.
                Profile profile = getProfileFromUser(user, userId);
                if (profile.getConferenceKeysToAttend().contains(websafeConferenceKey)) {
                    profile.unregisterFromConference(websafeConferenceKey);
                    conference.giveBackSeats(1);
                    ofy().save().entities(profile, conference).now();
                    return new TxResult<>(true);
                } else {
                    return new TxResult<>(false);
                }
            }
        });
        // NotFoundException is actually thrown here.
        return new WrappedBoolean(result.getResult());
    }
    
    /**
     * Get all sessions in a conference
     * 
     * @param websafeConferenceKey The key associated with the conference
     * 
     * @return A collection of Sessions in a Conference.  The collection will be empty if there are
     * 		   no sessions in the conference
     * 
     * @throws NotFoundException If no conference exists with the given websafeConferenceKey
     */
    @ApiMethod(
            name = "getConferenceSessions",
            path = "getConferenceSessions",
            httpMethod = HttpMethod.GET
    )
    public Collection<Session> getConferenceSessions(
    		@Named("websafeConferenceKey") final String websafeConferenceKey) 
    	throws NotFoundException {

    	Key<Conference> conferenceKey = Key.create(websafeConferenceKey);
    	return ofy().load().type(Session.class)
    			           .ancestor(conferenceKey)
    			           .list();
    }
    
    /**
     * Get all sessions in a given conference, filtered by type
     * 
     * @param websafeConferenceKey The key associated with the conference
     * @param typeOfSession        The type to filter sessions by
     * 
     * @return A list of sessions, filtered by typeOfSession, and ordered by session start time
     * 
     * @throws NotFoundException If no conference exists with the given websafeConferenceKey
     */
    @ApiMethod(
    		name = "getConferenceSessionsByType",
    		path = "getConferenceSessionsByType",
    		httpMethod = HttpMethod.GET
    )
    public List<Session> getConferenceSessionsByType(
    		@Named("websafeConferenceKey") final String websafeConferenceKey,
    		@Named("typeOfSession") final String typeOfSession)
    		throws NotFoundException {
    	
    	Key<Conference> conferenceKey = Key.create(websafeConferenceKey);
    	return ofy().load().type(Session.class)
       	                   .ancestor(conferenceKey)
       	                   .filter("typeOfSession =", typeOfSession)
       	                   .order("startDate")
       	                   .list();
    }
    
    /**
     * Get all sessions, filtered by speaker, across all conferences
     * 
     * @param speaker The name of the speaker
     * 
     * @return A list of sessions by the given speaker, across all conferences
     */
    @ApiMethod(
    		name = "getSessionsBySpeakerKey",
    		path = "getSessionsBySpeakerKey",
    		httpMethod = HttpMethod.GET
    )
    public List<Session> getSessionsBySpeakerKey(@Named("websafeSpeakerKey") String websafeSpeakerKey) {
    	return ofy().load().type(Session.class).filter("speakerKey =", websafeSpeakerKey).list();
    }
    
    /**
     * Returns sessions across all conferences for a given speaker
     * 
     * @param speaker The name of the speaker
     * @return List of sessions for the given speaker
     * @throws NotFoundException If the speaker could not be found in the database
     */
    @ApiMethod(
    		name = "getSessionsBySpeaker",
    		path = "getSessionsBySpeaker",
    		httpMethod = HttpMethod.GET
    )
    public List<Session> getSessionsBySpeaker(@Named("speaker") String speaker) 
    	throws NotFoundException {
    	
    	// first, get the speaker
    	List<Speaker> res = ofy().load().type(Speaker.class).filter("name =", speaker).list();
    	
    	if (res == null || res.isEmpty()) {
    		throw new NotFoundException("Could not find speaker");
    	}
    	
    	return ofy().load().type(Session.class)
    			            .filter("speakerKey =", res.get(0).getWebsafeKey())
    			            .list();
    }
    
    /**
     * Create a speaker and save to datastore
     * 
     * @param user The user who is creating the speaker, null if not authenticated
     * @param speakerForm Contains all of the properties for the new speaker
     * @return The speaker, if created successfully 
     * 
     * @throws UnauthorizedException If user is not authenticated
     * @throws Null
     */
    @ApiMethod(
    		name = "createSpeaker",
    		path = "speaker",
    		httpMethod = HttpMethod.PUT
    )
    public Speaker createSpeaker(User user, SpeakerForm speakerForm) 
    		throws UnauthorizedException {
    	if (user == null) {
    		throw new UnauthorizedException("Authorization required");
    	}
    	
    	Speaker speaker = new Speaker(speakerForm);
    	ofy().save().entity(speaker).now();

    	return speaker;
    }
    
    @ApiMethod(
    		name = "querySpeakers",
    		path = "querySpeakers",
    		httpMethod = HttpMethod.POST
    )
    public List<Speaker> querySpeakers(SpeakerQueryForm speakerQueryForm) {
    	// if email is specified, that's the best case since it's the id for a speaker
    	System.out.println("email is " + speakerQueryForm.getEmail());
    	if (speakerQueryForm.getEmail() != null) {
    		System.out.println("email is not null!");
    		Key<Speaker> speakerKey = Key.create(Speaker.class, speakerQueryForm.getEmail());
    		Speaker res = ofy().load().key(speakerKey).now();
    		
    		if (res == null) {
    			return new ArrayList<Speaker>();
    		}
    		
    		return Arrays.asList(res);
    	}
    	
    	System.out.println("name is " + speakerQueryForm.getName());
    	if (speakerQueryForm.getName() != null) {
    		System.out.println("name is not null!");
    		// try matching on the name
    		return ofy().load().type(Speaker.class).filter("name =", speakerQueryForm.getName()).list();
    	}
    	
    	System.out.println("fell through everything!!");
    	// no filters specified so just return all speakers
    	return ofy().load().type(Speaker.class).list();
    }
    
    /**
     * Creates a session within a conference
     * 
     * @param user 					The user who is creating the session, null if the user is not 
     * 								authenticated
     * @param sessionForm 			Form containing details about the session
     * @param websafeConferenceKey  The key associated with the conference which will contain the
     *                              new session
     *                             
     * @return The created Session object
     * 
     * @throws UnauthorizedException    If the user is not authenticated
     * @throws NotFoundException        If a conference with the given key does not exist
     * @throws IllegalArgumentException If start date is later than end date
     * @throws ConflictException		If start date is prior to conference start date or end date
     * 									is after conference end date
     */
    @ApiMethod(
    		name = "createSession",
    		path = "session",
    		httpMethod = HttpMethod.PUT
    )
    public Session createSession(
    			User user, 
    			final SessionForm sessionForm, 
    			@Named("websafeConferenceKey") final String websafeConferenceKey) 
    		throws UnauthorizedException, NotFoundException, IllegalArgumentException,
    		       ForbiddenException, ConflictException {
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }   	
        
        final Key<Conference> conferenceKey = Key.create(websafeConferenceKey);
        Key<Session> sessionKey = factory().allocateId(conferenceKey, Session.class);
        final long sessionId = sessionKey.getId();

        TxResult<Session> session = ofy().transact(new Work<TxResult<Session>>(){
        	@Override
        	public TxResult<Session> run() {
        		// try to get the conference.  if there's an error, wrap the exception in TxResult
        		Conference conference = null;
        		try {
        			conference = getConference(websafeConferenceKey);
        		} catch (NotFoundException e) {
        			return new TxResult<>(e);
        		}

        		// try to create the session.  because Session constructor can throw an
        		// IllegalArgumentException (due to Preconditions checks), we should catch that
        		// exception here and wrap it in TxResult, like all the other exceptions
        		Session session = null;
        		try {
        			session = new Session(sessionId, conferenceKey, sessionForm);
        		} catch (IllegalArgumentException e) {
        			return new TxResult<>(e);
        		}
        		
        		// return a ConflictException if the start/end times of the session conflict with
        		// the start/end times of the conference - meaning that either the session start
        		// is before conference start or session end is after conference end
        		if (session.getStartDate().compareTo(conference.getStartDate()) < 0 ||
        		    session.getEndDate().compareTo(conference.getEndDate()) > 0) {
        			return new TxResult<>(
        				new ConflictException("Session times cannot extend past conference times"));
        		}
        		
        		// check to see that the speaker actually exists
        		Speaker speaker = 
        			ofy().load().key(Key.<Speaker>create(sessionForm.getSpeakerKey())).now();
        		
        		if (speaker == null) {
        			return new TxResult<>(new NotFoundException("Speaker not found"));
        		}

        		ofy().save().entities(session, conference).now();
        		
        		// only need to check this if speaker is set
        		if (sessionForm.getSpeakerKey() != null) {
        			checkFeaturedSpeaker(conference, speaker);
        		}

        		return new TxResult<>(session);
        	}
        });
        
        return session.getResult();
    }
    
    private void checkFeaturedSpeaker(Conference conference, Speaker speaker) {
    	// get all sessions in conference by this speaker
    	Key<Conference> conferenceKey = Key.create(conference.getWebsafeKey());
    	List<Session> speakerSessionsInConf = 
    		ofy().load().type(Session.class)
    	         .ancestor(conferenceKey)
    	         .filter("speakerKey =", speaker.getWebsafeKey())
    	         .list();
    	
    	if (speakerSessionsInConf.size() < 2) {
    		return;
    	}
    	
    	// if we reach this part of the code, the speaker should be featured.  get the session names
    	List<String> sessionNames = new ArrayList<>();
    	for (Session speakerSession : speakerSessionsInConf) {
    		sessionNames.add(speakerSession.getName());
    	}
    	
    	// create the featured speaker to store in memcache
    	FeaturedSpeaker featuredSpeaker = 
    		new FeaturedSpeaker(speaker.getName(), conference.getName(), sessionNames);

        MemcacheService memcacheService = MemcacheServiceFactory.getMemcacheService();
        memcacheService.put(Constants.MEMCACHE_FEATURED_SPEAKER_KEY, featuredSpeaker);
    }
    
    /**
     * 
     */
    @ApiMethod(
    		name = "getFeaturedSpeaker",
    		path = "getFeaturedSpeaker",
    		httpMethod = HttpMethod.GET
    )
    public FeaturedSpeaker getFeaturedSpeaker() {
        MemcacheService memcacheService = MemcacheServiceFactory.getMemcacheService();
        Object message = memcacheService.get(Constants.MEMCACHE_FEATURED_SPEAKER_KEY);
        if (message != null) {
        	FeaturedSpeaker speaker = (FeaturedSpeaker) message;
        	System.out.println("the speaker's name is " + speaker.getName());
        	return speaker;
        }
        return null;
    }

    /**
     * Adds a session to a user's wishlist
     * 
     * @param user 				The user, null when not authenticated
     * @param websafeSessionKey The websafe string key of the session to add to the wishlist
     * 
     * @return true if added to wishlist successfully, exception will get thrown otherwise
     * 
     * @throws UnauthorizedException If the user is not authenticated
     * @throws ConflictException 	 If the user already has the session in his wishlist
     */
    @ApiMethod(
            name = "addSessionToWishlist",
            path = "addSessionToWishlist",
            httpMethod = HttpMethod.POST
    )
    public WrappedBoolean addSessionToWishlist(
    			final User user,
            	@Named("websafeSessionKey") final String websafeSessionKey)
            throws UnauthorizedException, ConflictException, NotFoundException, ForbiddenException {
        // If not signed in, throw a 401 error.
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        
        // all of the objectify work takes place in a transaction.  this is because we want to verify
        // that the websafe session key actually maps to an existing session.  if we don't do this
        // in a transaction, someone else can delete the session while we are adding it to the 
        // wishlist, leading to inconsistencies
        TxResult<Boolean> result = ofy().transact(new Work<TxResult<Boolean>>() {
        	@Override
        	public TxResult<Boolean> run() {
        		// get the profile
        		Profile profile = getProfileFromUser(user, getUserId(user));
        		
        		// get the session.  we don't really need it, but we should verify that it actually
        		// exists.  this is the primary reason for doing all this in a transaction
        		Key<Session> sessionKey = Key.create(websafeSessionKey);
        		Session session = ofy().load().key(sessionKey).now();
        		if (session == null) {
        			return new TxResult<>(new NotFoundException("Session does not exist"));
        		}
        		
        		if (profile.getSessionKeysInWishlist().contains(websafeSessionKey)) {
        			return new TxResult<>(
        				new ConflictException("You have already added this session to your wishlist"));
        		}
        		
        		profile.addToSessionKeysInWishlist(websafeSessionKey);
        		ofy().save().entity(profile).now();
        		
        		return new TxResult<>(true);
        	}
        });
        
        return new WrappedBoolean(result.getResult());
    }
    
    /**
     * Gets all sessions in a user's wishlist
     * 
     * @param user The user whose wishlist we want.  Null if not signed in
     * 
     * @return The wishlist of sessions
     * 
     * @throws UnauthorizedException If the user is not authenticated
     * @throws NotFoundException 	 If the user could not be found
     */
    @ApiMethod(
    		name = "getSessionsInWishlist",
    		path = "getSessionsInWishlist",
    		httpMethod = HttpMethod.GET
    )
    public Collection<Session> getSessionsInWishlist(User user) 
    		throws UnauthorizedException, NotFoundException {
        // If not signed in, throw a 401 error.
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }

        Profile profile = ofy().load().key(Key.create(Profile.class, getUserId(user))).now();
        if (profile == null) {
            throw new NotFoundException("Profile doesn't exist.");
        }

        List<String> websafeSessionKeysInWishlist = profile.getSessionKeysInWishlist();
        List<Key<Session>> sessionKeysInWishlist = new ArrayList<>();
        for (String keyString : websafeSessionKeysInWishlist) {
            sessionKeysInWishlist.add(Key.<Session>create(keyString));
        }

        return ofy().load().keys(sessionKeysInWishlist).values();
    }
    
    /**
     * Removes a session from a user's wishlist 
     * 
     * @param user 				The user, null if not authenticated
     * @param websafeSessionKey The string key of the session
     * 
     * @return true if session was removed from wishlist, false if session was not in wishlist to
     *         begin with
     *         
     * @throws UnauthorizedException If the user is not logged in
     * @throws NotFoundException     If the user's profile could not be found in the database
     */
    @ApiMethod(
    		name = "removeSessionFromWishlist",
    		path = "removeSessionFromWishlist",
    		httpMethod = HttpMethod.POST
    )
    public WrappedBoolean removeSessionFromWishlist(
    			User user,
    			@Named("websafeSessionKey") String websafeSessionKey) 
    		throws UnauthorizedException, NotFoundException {
    	if (user == null) {
    		throw new UnauthorizedException("Authorization required");
    	}
    	
    	Profile profile = ofy().load().key(Key.create(Profile.class, getUserId(user))).now();
    	if (profile == null) {
    		throw new NotFoundException("Profile doesn't exist.");
    	}
    	
    	List<String> websafeKeysInWishlist = profile.getSessionKeysInWishlist();
    	if (websafeKeysInWishlist.contains(websafeSessionKey)) {
    		profile.removeFromWishlist(websafeSessionKey);
    		ofy().save().entities(profile).now();
    		return new WrappedBoolean(true);
    	}
    	
    	return new WrappedBoolean(false);
    }
    
    /**
     * Finds all sessions which contain a speaker who is an expert in appengine
     * 
     * This could arguably be done in a transaction since we are executing two queries, but since
     * they are only reads I'm opting not to do it here and to let the user get slightly incorrect
     * results in the edge case where changes are made to the sessions/speakers in question between
     * the completion of the two queries here.
     * 
     * @return
     */
    public List<Session> additionalQuery1() {
    	
    	// first get all speakers who are experts in appengine
    	List<Speaker> appengineExperts = 
    		ofy().load().type(Speaker.class).filter("expertise =", "appengine").list();
    	
    	// return an empty list of sessions if there are no speakers who are experts in appengine.
    	// no point in actually running the second query
    	if (appengineExperts.isEmpty()) {
    		return new ArrayList<Session>();
    	}
    	
    	List<String> speakerKeys = new ArrayList<>();
    	for (Speaker speaker : appengineExperts) {
    		speakerKeys.add(speaker.getWebsafeKey());
    	}
    	
    	return ofy().load().type(Session.class).filter("speakerKey in", speakerKeys).list();
    }
    
    /**
     * Finds all sessions which are starting in the next week, sorted by start time
     * 
     * This could be for a view on the UI where the upcoming conferences are displayed.
     * 
     * @return
     */
    public List<Conference> additionalQuery2() {
    	Calendar cal = Calendar.getInstance();
    	Date today = cal.getTime();
    	
    	cal.add(Calendar.DAY_OF_YEAR, 6);
    	Date sixDaysFromNow = cal.getTime();
    	
    	return ofy().load().type(Conference.class)
    	                   .filter("startDate >=", today)
    	                   .filter("startDate <=", sixDaysFromNow)
    	                   .list();
    }

    /**
     * The query problem from task 3
     * 
     * The user doesn't like workshops, so we have to use an inequality there.  There are many 
     * possible types of conferences that list might always be changing.  If the list isn't well
     * maintained, it might be difficult to get a complete list of conference types which are not 
     * workshops.
     * 
     * On the other hand, time isn't changing.  We can write this once and forget about it.  If the
     * user doesn't want to attend a session that ends at 7pm or later, we know that the conference
     * must end anytime from hour 1 through hour 18.  Thus, we can swap our endTime < 19 for
     * endHour in (1, 2, ..., 18).  We are then free to use our only inequality on the session type
     * 
     * This solution required the addition of endHour to the Session class.  For completeness, I also
     * added a startHour field, but I've only indexed endHour as it was necessary for this specific
     * example
     * 
     * @return
     */
    public List<Session> queryProblem() {
    	List<Integer> endHours = new ArrayList<>();
    	// 7pm is 19, so valid conferences are those that end before 7pm, which in 24 hour format
    	// is hours 1 through 18
    	for (int i = 1; i <= 18; i++) {
    		endHours.add(i);
    	}
    	
    	return ofy().load().type(Session.class)
    	            .filter("typeOfSession !=", "workshop")
    	            .filter("endHour in", endHours)
    	            .list();
    }
}