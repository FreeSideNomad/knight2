For receivable service we need to configure new type of account

Additional Account System is CAN_GRADS
Account types are PAD and PAP
Account number scheme consists of data cenre (OCC, QCC, BCC) and 12 digit GSAN (Grads Service Account Number)

Only PAD account type can be enrolled to receivable service

You will need to create sample set of PAD and PAP accounts for existing Candian customers (3 of each)

Apart from rules related to PAD and PAP accounts, there is no additional configuration for this service.

To Enroll client to receivable service on Services Tab create a dropdown menu listing all services that this profile 
is not yet enrolled to.

When you select Receivable from dropdown menu new pop-up form will be displayed.

You will then be able to select PAD accounts alredy enrolled to the profile.

There must be at least one PAD account selected for receivable service enrollment to be saved.

Once client is enrolled to receivable service we will be able to create Indirect Clients linked to that client

To create Indirect Clients we will create a new left-nav link called Indirect Clients

To search for indirect clients we will have to select from list of clients that are enrolled in receivable service

Once we select the client, list of indirect clients will be displayed

There will be a button to create new Indirect Client enabled only if client is enrolled in receivable service is selected.

To create new Indirect Client popup form will contain all fields of the indirect client

(remove taxId field from IndirectClient class)

Define Person Role as ADMIN or CONTACT and allow adding of related persons on second page of the pop-up wizard.



