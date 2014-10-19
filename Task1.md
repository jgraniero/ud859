##Session
A Session object contain the following properties:
* `Long id`: There is no Session field which is suitable as an ID, so I've chosen to automatically allocate an ID for sessions
* `String name`: This is the name of a conference.  It can be any string and is not required
* `Date startDate` and `Date endDate`: This is how we track the start and end time of the conference.  Using date here allows us to store a lot of information in a single object.  This makes it very easy to do things like validating that a session is actually contained within a conference and ensuring that a startDate is chronologically earlier than an endDate.  This makes the logic a lot simpler especially in cases where a Session might span multiple days.  These two fields are required.
* `String typeoOfSession`: This is the session type or format (faq, workshop, etc.).  Not required
* `Key<Conference> conferenceKey`: This property makes Conference an ancestor of Session.  The ancestor relationship makes it very easy to find all sessions in a conference, which would probably be one of the most frequent Session queries.
* `Set<String> speakers`: These are all the speakers for a session.  Using a collection here becasue there could be more than one speaker.  Furthermore, I'm using a set to make sure a speaker is not listed twice (though there is an edge-case issue here where two speakers can have the same name.  In that case, only one name will be listed in the speakers set)
* `Set<String> highlights`: I'm not sure what this is.  I figure the highlights can be listed as a set of strings, for example ["we learned how to write appengine api methods", "we learned how to properly configure datastore indexes"]

