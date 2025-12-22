on Indirect client when viewing details of receivable service allow for payor enrolment from
file.

User will select JSON file that will contain array of Payors
where each Payor will have details required to create Indirect Client
and array of peole with attributes required to create related person.

On the server side, once JSON file is uploaded, it will be parsed and
payors will be created and related people will be created using following logic:

- Create Indirect Client for each Payor
- Create Related Person for each Person in Payor
- Create Indirect Profile for each Indirect Client
- Enrol PAYOR service to each indirect profile
- For each ADMIN person create User with role security_admin and add new user to Indirect Profile
- initiate Auth0 onboarding for each ADMIN person


