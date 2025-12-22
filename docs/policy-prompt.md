Policies should be split into PermissionPolicy and ApprovalPolicy

Both are owned by Profile (ProfileId)

Action is a value object (java record wrapping urn string)
- top level is service group security, reporting, payments)
- next level is specific service for example (ACH Payments, Wire Payments, Account Transfer )
- next level is resource type (report, wire-payment, wire-template, recurring-wire-template) - optional
- last level is action (create, view , approve

For example payments.ach-payments.single-payment.create
            payments.ach-payments.recurring-payment.create
            reporting.balance-and-transactions.transactions.view
            security.users.user.create
            security.users.permission.create
            security.approvals.approval-policy.create

action does not have to be full qualified it can include wildcards for eample

- * - matches any action
- payments.* - matches any action under payments service group
- *.create - matches any action ending with create
- *.approve - matches any action ending with approve


Subject is a value object that can be user (user:UUID), group (group:UUID) or role (role:roleName)

Following roles are predefined:
- security-admin -> action: security.*
- super-admin -> action: * (all actions)
- approver -> action *.approve
- creator -> action *.create
- viewer -> action *.view

Resource is a value object representing list of resources for example accountIds
CAN_DDA:DDA:00000:081154333874,CAN_DDA:DDA:00000:317040422883,CAN_DDA:DDA:00000:673296044306

It can also be a wildcard for example:

CAN_DDA:DDA:*
*:DDA:*

If resource is not specified it means all resources

