Online and indirect profiles have users.

We would like to display they on the profile page as a tab.

Adding new user should be possible for Indirect Profiles.

In addition to storing new user data we will for indirect users need to provision Auth0 identities.

To understand how to do it analyse /Users/igormusic/code/okta-app - you can source environment variables from .env 

See : /Users/igormusic/code/okta-app/scripts/provisioning/create-user.sh for provisioning new user.

This application will be enhanced to publish events to Kafka when new user completes password and MFA setup so 
that status of provisioning can be updated in users table.

design of /Users/igormusic/code/knight2/domain/auth0-identity is kind of guess and should actually reflect requirements above.


