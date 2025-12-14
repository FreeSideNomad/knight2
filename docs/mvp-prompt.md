# Goal

Employee facing Vaadin apps interacting with 
platform domain through REST API (BFF)

# Features

## Clients

In addition to IndirectClient we will also create Client Aggregate

with ClientId
Name
Address
    value object:  
        -address line 1
         -address line 2
         -city
         -state_province
         -zip_postal_code
         -countryCode

Employee portal will allow searching for clients by client type (srf, cdr) and name

Once the client is selected, the employee portal will allow viewing client details and creating related service profiles

Related to Client is ClientAccount

ClientAccountId is value object following an urn scheme:

- {accountSystem}:{accountType}:[{accountNumberSegments}][1-n]

accountSystems is a enum
 - CAN_DDA
 - CAN_FCA
 - CAN_LOC
 - CAN_MTG
 - US_FIN (US Finacle system)
 - US_FIS (US FIS system)
 - OFI (other financial institution)


 accountTypes is a enum
 - DDA (direct deposit account)
 - CC (credit card account)
 - LOC (line of credit account)
 - MTG (mortgage)

accountNumberSegments is a list of string consisting of digits only with leading zeros
each combintation accountSystem and accountType can have speific segements with predefined length
For example,
CAN_DDA:DDA:transit(5):accountNummber(12)

For OFI account Types are:

- CAN (Canadian accounts in form bank(3):transit(5):accountNumber(12)
- IBAN (International bank account number in form of BIC code (8-11 chars : ibanAccountNumber (34 chars)) - in this case accountNumber is IBAN code and not all numeric
- US (US accounts in form aba_routing(9):accountNumber(17)
- SWIFT (SWIFT code in form BIC(8-11 chars):BBAN(34 chars) 

In addition to ClientAccountId , clientId
ClientAccount has 3-letter ISO currency code, AccountStatus enum (ACTIVE, CLOSED)

When displaying Client

Layout looks like this:

ClientId:
Name: 
Address:


[Profiles][Accounts]
List of profiles and accounts for the client if profiles tab selected
List of accounts for the client if accounts tab selected

On Profiles tab there are buttons to create new profile [Service Profile][Online Profile]

in test we will have test data genertor that will create test data for client and client accounts using faker

this repo will need to copy nginx gateway into employee-gateway directory from
/Users/igormusic/code/entra-auth 
while Vaadin app will be in java module (separate app) employee-portal based on security model from entra-auth (display only authenticated users top right corner menu)

then we will have docker compose that will start 
- nginx gateway
- employee portal
- platform application (backed exposing BFF REST API)
- SQL Server
- Kafka







