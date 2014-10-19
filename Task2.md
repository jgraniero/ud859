##Wishlists
Wishlists are implemented much like conferences to attend.  The `Profile` entity was extended to contain a list of sessions in wishlist.  Again, this is much like conferences to attend in that the session wishlist is stored as a list of websafe string keys for the corresponding sessions.

There are 3 functions related to wishlists:
* `addSessionToWishlist(user, websafeSessionKey)` - this adds a session to a user's wishlist.  The work to do this is completed in a transaction to ensure that the session is actually valid (exists) at the time it's added to the wishlist.  There would have to be corresponding logic in `deleteSession` (not implemented) which went through all users' wishlists and removed the deleted session.
* `removeSessionFromWishlist(user, websafeSessionKey)` - the opposite of adding to a wishlist.  This will just remove the session key from the wishlist and save the profile back to datastore.
* `getSessionsInWishlist(user)` - this gets all of the session's in a user's wishlist.  The most efficient way to query the sessions is by `ofy().load().keys(sessionKeys)`, but by doing so I think we lose the ability to leverage objectify's `order` method.  The sessions can be easily sorted by time by creating a `Comparator`.  In the interest of time (and not cluttering this project with more code), I left that out.  Might come back to it if I have time.
