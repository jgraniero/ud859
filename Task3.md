## Query Related Problem
I'm assuming that "sessions before 7pm" means that the session end time is prior to 7pm.

The problem is that while datastore allows us to use an inequality filter on at most one property, we need an inequality filter on two properties here:
* session end time < 7pm
* typeOfSession != workshop

There are a few easy solutions to this problem:
* query using one equality, then further filter those results in code using the other inequality
* specifically query for all hours prior to 7pm - something like `endTime in (0, 1, 2, 3, ..., 18)`
* specifically query for all session types which are not workshops.  this would require a finite list of session types which might a little bit more difficult to nail down than option above, where we need all hours in the day prior to 7pm.  In the case above, at least we know the hours of the day can't change, while in this case the base set of session types can, and very likely will, tchange.

An easy way to do this would be to store the "occupied" hours of the day in an array with the Session entity.  If the user doesn't want to go to a session that is occupied at 7pm, we just check the array for 7pm, thereby eliminiating the < 7pm inequality.
