# Manual Functional Test Plan - Knight Platform

This document contains manual test cases for human testers to execute using the actual portal and login UIs.

---

## Table of Contents

1. [Test Environment Setup](#1-test-environment-setup)
2. [Client Portal - New User Registration (FTR)](#2-client-portal---new-user-registration-ftr)
3. [Client Portal - Returning User Login](#3-client-portal---returning-user-login)
4. [Client Portal - Password Reset](#4-client-portal---password-reset)
5. [Client Portal - Guardian Reset](#5-client-portal---guardian-reset)
6. [Client Portal - Passkey Management](#6-client-portal---passkey-management)
7. [Client Portal - Step-Up Authentication](#7-client-portal---step-up-authentication)
8. [Client Portal - Indirect Client Management](#8-client-portal---indirect-client-management)
9. [Employee Portal - Login](#9-employee-portal---login)
10. [Employee Portal - Client Search & Management](#10-employee-portal---client-search--management)
11. [Employee Portal - Profile Management](#11-employee-portal---profile-management)
12. [Employee Portal - User Management](#12-employee-portal---user-management)
13. [Employee Portal - Indirect Client Management](#13-employee-portal---indirect-client-management)
14. [Employee Portal - Batch Import](#14-employee-portal---batch-import)
15. [Indirect Client Portal](#15-indirect-client-portal)

---

## 1. Test Environment Setup

### Prerequisites

| Item | Details |
|------|---------|
| Employee Portal URL | `http://localhost:8081` (or configured URL) |
| Client Login URL | `http://localhost:8080/login` (or configured URL) |
| Client Portal URL | `http://localhost:8082` (or configured URL) |
| Indirect Client Portal URL | `http://localhost:8083` (or configured URL) |
| Auth0 Guardian App | Installed on mobile device |
| Authenticator App | Google Authenticator or similar (for TOTP) |
| Email Access | Access to test email accounts to receive OTPs |

### Test User Accounts

| Role | Username/Email | Purpose |
|------|----------------|---------|
| Bank Employee | `employee@bank.test` | Employee Portal testing |
| Client Admin | `admin@client.test` | Client Portal - existing user |
| New Client User | `newuser@client.test` | FTR flow testing |
| Indirect Client User | `user@indirect.test` | Indirect Client Portal testing |

---

## 2. Client Portal - New User Registration (FTR)

### TC-FTR-001: Complete First-Time Registration with TOTP

**Objective:** Verify a new user can complete the full registration flow with TOTP MFA

**Preconditions:**
- New user account created by bank employee (not yet registered)
- Access to user's email inbox

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to Client Login URL | "Sign in" page displayed with email input field |
| 2 | Enter the new user's email address | Email field accepts input |
| 3 | Click "Continue" button | "Check your email" screen appears showing 6-digit OTP entry |
| 4 | Check email inbox for OTP code | Email received with 6-digit verification code |
| 5 | Enter the 6-digit OTP code | Code accepted |
| 6 | Click "Verify" button | "Create your password" screen appears |
| 7 | Enter password: `TestPass123!` | Password field shows requirements checklist |
| 8 | Enter same password in "Confirm Password" field | Passwords match |
| 9 | Click "Create Account" button | "Set up Authenticator App" screen appears with QR code |
| 10 | Open authenticator app (Google Authenticator) on phone | App ready to scan |
| 11 | Scan QR code with authenticator app | Account added to authenticator |
| 12 | Enter 6-digit code from authenticator app | Code field accepts input |
| 13 | Click "Verify & Complete Setup" | Success screen: "You're being redirected..." |
| 14 | Wait for redirect | Redirected to Client Portal dashboard |

**Pass Criteria:** User successfully completes FTR and lands on dashboard

---

### TC-FTR-002: Complete First-Time Registration with Guardian

**Objective:** Verify a new user can complete FTR using Guardian push notifications

**Preconditions:**
- New user account created
- Auth0 Guardian app installed on mobile device
- Access to user's email

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to Client Login URL | Sign in page displayed |
| 2 | Enter new user's email, click "Continue" | OTP verification screen |
| 3 | Enter OTP from email, click "Verify" | Password creation screen |
| 4 | Create password meeting requirements, click "Create Account" | MFA setup screen |
| 5 | Select "Set up Auth0 Guardian" option | QR code displayed for Guardian app |
| 6 | Open Guardian app on phone | App ready |
| 7 | Tap "+" to add account, scan QR code | Account enrollment started |
| 8 | Approve enrollment on Guardian app | "Waiting for approval..." message on screen |
| 9 | Wait for enrollment confirmation | Success message, redirected to dashboard |

**Pass Criteria:** Guardian MFA enrolled and user logged in

---

### TC-FTR-003: FTR - Invalid OTP Code

**Objective:** Verify error handling for incorrect OTP

**Preconditions:** New user, OTP sent

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to login, enter email, click Continue | OTP screen displayed |
| 2 | Enter incorrect OTP: `000000` | - |
| 3 | Click "Verify" | Error message: "Invalid code" with attempts remaining shown |
| 4 | Enter incorrect OTP 2 more times | After 3rd attempt: "Maximum attempts exceeded" or similar |

**Pass Criteria:** System rejects invalid OTP and tracks attempts

---

### TC-FTR-004: FTR - Expired OTP

**Objective:** Verify handling of expired OTP codes

**Preconditions:** New user

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start FTR, get OTP screen | Timer shows "Code expires in 5:00" |
| 2 | Wait for timer to expire (5 minutes) | Timer reaches 0:00 |
| 3 | Enter the original OTP code | Error: "Code expired" |
| 4 | Click "Resend code" link | New OTP sent, timer resets |
| 5 | Enter new OTP from email | Code accepted, proceed to password screen |

**Pass Criteria:** Expired codes rejected, resend works

---

### TC-FTR-005: FTR - Password Validation

**Objective:** Verify password complexity requirements are enforced

**Preconditions:** User at password creation screen

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Enter short password: `Pass1!` | Requirement "At least 12 characters" not checked |
| 2 | Enter no uppercase: `password123!!` | Requirement "One uppercase letter" not checked |
| 3 | Enter no lowercase: `PASSWORD123!!` | Requirement "One lowercase letter" not checked |
| 4 | Enter no number: `PasswordOnly!!` | Requirement "One number" not checked |
| 5 | Enter no special char: `Password12345` | Requirement "One special character" not checked |
| 6 | Enter valid password: `SecurePass123!` | All requirements checked green |
| 7 | Enter mismatched confirm password | "Passwords don't match" error |
| 8 | Enter matching confirm password | Match confirmed |
| 9 | Click "Create Account" | Proceeds to MFA setup |

**Pass Criteria:** All password rules enforced visually and on submit

---

## 3. Client Portal - Returning User Login

### TC-LOGIN-001: Login with Password and TOTP

**Objective:** Verify existing user can login with password + TOTP

**Preconditions:**
- User completed FTR with TOTP enrolled
- Authenticator app with user's account

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to Client Login URL | Sign in page |
| 2 | Enter registered email | - |
| 3 | Click "Continue" | "Welcome back" screen with password field |
| 4 | Verify user info card shows initials and email | Correct user displayed |
| 5 | Enter correct password | - |
| 6 | Click "Sign In" | MFA verification screen: "Enter verification code" |
| 7 | Enter 6-digit code from authenticator app | - |
| 8 | Click "Verify" | Redirected to Client Portal dashboard |

**Pass Criteria:** User logged in successfully

---

### TC-LOGIN-002: Login with Guardian Push (CIBA)

**Objective:** Verify passwordless login via Guardian push notification

**Preconditions:**
- User has Guardian enrolled
- Guardian app on phone, notifications enabled

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to Client Login URL | Sign in page |
| 2 | Enter email with Guardian enrolled | - |
| 3 | Click "Continue" | "Approve sign in" screen with spinner: "Waiting for approval..." |
| 4 | Check Guardian app on phone | Push notification received: "Approve login request" |
| 5 | Tap "Approve" on Guardian app | - |
| 6 | Observe login screen | "Success!" message, redirected to dashboard |

**Pass Criteria:** Passwordless login via Guardian works

---

### TC-LOGIN-003: Login - Wrong Password

**Objective:** Verify error handling for incorrect password

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Enter registered email, click Continue | Password screen |
| 2 | Enter wrong password: `WrongPassword123!` | - |
| 3 | Click "Sign In" | Error message: "Invalid credentials" or "Wrong password" |
| 4 | Password field cleared, can retry | Field ready for new input |

**Pass Criteria:** Clear error message, no security info leaked

---

### TC-LOGIN-004: Login - Locked Account

**Objective:** Verify locked user cannot login

**Preconditions:** User account locked by bank employee

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Enter locked user's email | - |
| 2 | Click Continue, enter correct password | - |
| 3 | Click Sign In | Error: "Account locked" or "Contact administrator" |

**Pass Criteria:** Locked user cannot authenticate

---

### TC-LOGIN-005: Login - Switch User ("Not you?" link)

**Objective:** Verify user can switch to different account

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Enter email, click Continue | "Welcome back" with user info card |
| 2 | Click "Not you?" link | Returns to email entry screen |
| 3 | Enter different email | - |
| 4 | Click Continue | Shows correct user info for new email |

**Pass Criteria:** Can switch between accounts

---

### TC-LOGIN-006: Login - Use Password Instead (from CIBA)

**Objective:** Verify fallback from Guardian push to password

**Preconditions:** User has Guardian and password

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Enter email, click Continue | Guardian approval screen with spinner |
| 2 | Click "Use password instead" link | Password entry screen appears |
| 3 | Enter password, click Sign In | MFA challenge screen (TOTP or Guardian select) |
| 4 | Complete MFA | Logged in to dashboard |

**Pass Criteria:** Can fallback from push to password

---

## 4. Client Portal - Password Reset

### TC-PWD-001: Reset Password via Forgot Password Flow

**Objective:** Verify user can reset forgotten password

**Preconditions:** Existing user with email access

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to login, enter email, click Continue | Password screen |
| 2 | Click "Forgot password?" link | "Reset your password" screen with login ID field |
| 3 | Enter login ID/email | - |
| 4 | Click "Send Verification Code" | "Check your email" screen with OTP entry |
| 5 | Check email for OTP code | Password reset code received |
| 6 | Enter OTP code, click "Verify Code" | "Create new password" screen |
| 7 | Enter new password meeting requirements | Requirements checklist shows green |
| 8 | Enter matching confirm password | - |
| 9 | Click "Reset Password" | "Password Reset Complete" success screen |
| 10 | Click "Sign In" | Back to login, can use new password |
| 11 | Login with new password | Successful login |

**Pass Criteria:** Password changed, can login with new password

---

### TC-PWD-002: Password Reset - Wrong OTP

**Objective:** Verify OTP validation during password reset

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start password reset, get to OTP screen | OTP entry displayed |
| 2 | Enter wrong OTP: `123456` | - |
| 3 | Click "Verify Code" | Error: "Invalid code", attempts remaining shown |
| 4 | Enter wrong OTP 2 more times | After max attempts: locked out or must restart |

**Pass Criteria:** Invalid codes rejected with attempt tracking

---

### TC-PWD-003: Password Reset - Resend OTP

**Objective:** Verify OTP resend functionality

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start password reset, get to OTP screen | Timer counting down |
| 2 | Click "Resend code" link (if timer allows) | "Code sent" message, new OTP in email |
| 3 | Old OTP no longer works | Rejected if tried |
| 4 | New OTP works | Proceeds to password screen |

**Pass Criteria:** Resend creates new valid OTP

---

## 5. Client Portal - Guardian Reset

### TC-GUARD-001: Reset Guardian to New Device

**Objective:** Verify user can reset Guardian when changing phones

**Preconditions:**
- User has Guardian enrolled
- Lost access to old Guardian device
- Has email access

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to login, enter email | - |
| 2 | Click Continue | Guardian approval screen |
| 3 | Click "Lost access to Guardian?" link | "Reset Guardian" screen |
| 4 | User info card displayed | Shows correct email |
| 5 | Click "Send Verification Code" | "Check your email" screen with OTP entry |
| 6 | Check email for OTP | Reset code received |
| 7 | Enter OTP, click "Verify & Reset Guardian" | "Guardian Reset Complete" success screen |
| 8 | Click "Continue to Sign In" | Password login screen |
| 9 | Enter password, click Sign In | MFA setup screen (re-enroll Guardian) |
| 10 | Scan QR with new Guardian app | New device enrolled |
| 11 | Approve on new device | Logged in successfully |

**Pass Criteria:** Old Guardian removed, new device enrolled

---

### TC-GUARD-002: Guardian Reset - Invalid OTP

**Objective:** Verify Guardian reset validates OTP

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start Guardian reset flow | OTP entry screen |
| 2 | Enter invalid OTP | - |
| 3 | Click "Verify & Reset Guardian" | Error: "Invalid code" |
| 4 | Guardian NOT deleted | Still requires Guardian on login |

**Pass Criteria:** Invalid OTP doesn't remove Guardian

---

## 6. Client Portal - Passkey Management

### TC-PASS-001: Enroll Passkey After Login

**Objective:** Verify user can add passkey for future logins

**Preconditions:**
- User logged in to Client Portal
- Browser/device supports WebAuthn
- Biometric or PIN available

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Complete login (password + MFA) | "Faster sign in next time" offer screen |
| 2 | Review passkey benefits listed | Benefits explained |
| 3 | Click "Set Up Passkey" | "Register your passkey" screen with spinner |
| 4 | Browser prompts for biometric/PIN | Touch ID, Face ID, or PIN prompt appears |
| 5 | Complete biometric verification | - |
| 6 | Observe screen | "Passkey created!" success screen |
| 7 | Click "Continue" | Proceed to dashboard |

**Pass Criteria:** Passkey enrolled successfully

---

### TC-PASS-002: Login with Passkey

**Objective:** Verify passkey login works

**Preconditions:** User has passkey enrolled

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to login, enter email | - |
| 2 | Click Continue | "Sign in with passkey" screen |
| 3 | Observe "Waiting for your device..." | Biometric prompt appears |
| 4 | Complete biometric/PIN | - |
| 5 | Observe screen | "Success!" - redirected to dashboard |

**Pass Criteria:** Passkey login bypasses password and MFA

---

### TC-PASS-003: Passkey Login - Fall Back to Password

**Objective:** Verify fallback when passkey fails

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start passkey login | Biometric prompt |
| 2 | Cancel biometric prompt | "Try Again" button appears |
| 3 | Click "Use password instead" link | Password entry screen |
| 4 | Complete password + MFA login | Logged in |

**Pass Criteria:** Can bypass passkey to use password

---

### TC-PASS-004: Skip Passkey Enrollment

**Objective:** Verify user can skip passkey setup

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Complete login, see passkey offer | "Set Up Passkey" and "Maybe later" options |
| 2 | Click "Maybe later" | Proceed directly to dashboard |
| 3 | No passkey enrolled | Next login uses password + MFA |

**Pass Criteria:** Passkey enrollment is optional

---

## 7. Client Portal - Step-Up Authentication

### TC-STEPUP-001: Approve Transaction with Guardian

**Objective:** Verify step-up auth for sensitive operations

**Preconditions:**
- User logged in to Client Portal
- User has Guardian enrolled
- MFA token valid

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to Step-Up page in Client Portal | Step-up screen displayed |
| 2 | Verify "MFA Token: VALID" badge shown | Token status confirmed |
| 3 | Enter authorization message: `Approve Wire $10,000 CAD to BELL LABS` | Message in textarea |
| 4 | Click "Approve with Guardian" button | "Sending Guardian push notification..." then "PENDING..." |
| 5 | Check Guardian app on phone | Push notification with transaction details |
| 6 | Tap "Approve" on Guardian | - |
| 7 | Observe Step-Up screen | "SUCCESS - Transaction Approved" message |

**Pass Criteria:** Transaction approved via step-up

---

### TC-STEPUP-002: Reject Transaction with Guardian

**Objective:** Verify step-up rejection handling

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Initiate step-up transaction | Guardian push sent |
| 2 | On Guardian app, tap "Reject" or "Deny" | - |
| 3 | Observe Step-Up screen | "REJECTED - Transaction Denied" message |

**Pass Criteria:** Rejection properly communicated

---

### TC-STEPUP-003: Step-Up with Expired Token - Refresh Required

**Objective:** Verify handling when MFA token expires

**Preconditions:** MFA token has expired (wait or manipulate)

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to Step-Up page | "MFA Token: EXPIRED" badge shown |
| 2 | "Approve with Guardian" button disabled | Cannot initiate step-up |
| 3 | Password refresh section visible | Warning message about expired token |
| 4 | Enter password in refresh field | - |
| 5 | Click "Refresh Token" button | Token refreshed, "VALID" badge appears |
| 6 | "Approve with Guardian" now enabled | Can proceed with step-up |

**Pass Criteria:** Can refresh expired token to continue

---

## 8. Client Portal - Indirect Client Management

### TC-IC-CLIENT-001: View Indirect Clients List

**Objective:** Verify direct client user can see their indirect clients (payors)

**Preconditions:** Logged in as direct client user with indirect clients

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to "Indirect Clients" menu | Indirect Clients page loads |
| 2 | Verify page title: "Indirect Clients" | Correct heading |
| 3 | Verify grid displays columns: Business Name, Reference, Status, Accounts, Contacts, Created | All columns visible |
| 4 | Verify indirect clients are listed | Data rows displayed |
| 5 | Click "View" on an indirect client | Indirect client detail page opens |

**Pass Criteria:** Indirect clients visible and navigable

---

### TC-IC-CLIENT-002: Import Indirect Clients from File

**Objective:** Verify batch import of indirect clients

**Preconditions:**
- Valid JSON import file prepared
- Logged in as direct client user

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to Indirect Clients page | Page loads |
| 2 | Click "Import Indirect Clients" button | File upload dialog opens |
| 3 | Select valid JSON file | File selected |
| 4 | Click "Upload" or "Import" | Processing indicator |
| 5 | Wait for completion | Success message with count of imported records |
| 6 | Verify new indirect clients in list | Newly imported items visible |

**Pass Criteria:** Batch import completes successfully

---

## 9. Employee Portal - Login

### TC-EMP-001: Employee Login with LDAP

**Objective:** Verify employee can login with LDAP credentials

**Preconditions:** Valid LDAP account

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to Employee Portal URL | Login page with username/password fields |
| 2 | Enter LDAP username | - |
| 3 | Enter LDAP password | - |
| 4 | Click "Sign In" or press Enter | Redirected to Employee Portal home (Client Search) |
| 5 | Verify header shows user's name | Display name visible in top-right |
| 6 | Verify "LDAP" auth source indicator | Shows authentication source |

**Pass Criteria:** LDAP authentication successful

---

### TC-EMP-002: Employee Login - Invalid Credentials

**Objective:** Verify error handling for wrong LDAP credentials

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Enter username | - |
| 2 | Enter wrong password | - |
| 3 | Click Sign In | Error message: "Invalid username or password" |
| 4 | Remain on login page | Can retry |

**Pass Criteria:** Clear error, no security info leaked

---

### TC-EMP-003: Employee Logout

**Objective:** Verify logout functionality

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | After login, click user dropdown in header | Menu opens |
| 2 | Click "Logout" | - |
| 3 | Observe | Redirected to login page |
| 4 | Try to access protected page directly | Redirected back to login |

**Pass Criteria:** Session terminated on logout

---

## 10. Employee Portal - Client Search & Management

### TC-CLI-001: Search Client by Number

**Objective:** Verify client search by SRF/CDR number

**Preconditions:** Employee logged in, client data exists

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | On home page, see "Search Clients" section | Search form visible |
| 2 | Select "SRF" from system dropdown | System selected |
| 3 | Enter client number: `123456` | Number entered |
| 4 | Click "Search" | Search executes |
| 5 | Results grid shows matching client(s) | Grid with Client ID, Name, Type columns |
| 6 | Click on a result row | Navigates to Client Detail page |

**Pass Criteria:** Client found by number

---

### TC-CLI-002: Search Client by Name

**Objective:** Verify partial name search

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Select "Search by Client Name" option | Name search field visible |
| 2 | Enter partial name: `Acme` | - |
| 3 | Click Search | Results show all clients with "Acme" in name |
| 4 | Verify pagination if many results | Page controls work |

**Pass Criteria:** Partial name search works

---

### TC-CLI-003: View Client Details

**Objective:** Verify client detail page information

**Preconditions:** Client found via search

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Click client row in search results | Client Detail page opens |
| 2 | Verify Client ID displayed | Correct ID shown |
| 3 | Verify Client Name displayed | Correct name |
| 4 | Verify Address displayed (multi-line) | Full address visible |
| 5 | Click "Profiles" tab | List of client's profiles shown |
| 6 | Click "Accounts" tab | Grid of accounts with ID, Currency, Status |

**Pass Criteria:** All client information accessible

---

### TC-CLI-004: Create Servicing Profile from Client

**Objective:** Verify creating a servicing profile for client

**Preconditions:** On client detail page

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Click "Profiles" tab | Profiles list shown |
| 2 | Click "New Servicing Profile" button | Create Profile page opens |
| 3 | Profile Name field pre-filled with client name | Can modify if needed |
| 4 | Select Account Enrollment: "Automatic" | All accounts will be enrolled |
| 5 | Click "Create Profile" | Success notification |
| 6 | Profile appears in client's profile list | New profile visible |

**Pass Criteria:** Servicing profile created

---

### TC-CLI-005: Create Online Profile with Manual Account Selection

**Objective:** Verify creating online profile with specific accounts

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | From client detail, click "New Online Profile" | Create Profile page |
| 2 | Select Account Enrollment: "Manual" | Account grid appears |
| 3 | Check boxes for specific accounts to enroll | Accounts selected |
| 4 | Click "Create Profile" | Profile created with selected accounts |
| 5 | View new profile, check Accounts tab | Only selected accounts enrolled |

**Pass Criteria:** Manual account selection works

---

## 11. Employee Portal - Profile Management

### TC-PROF-001: Search Profiles by Client

**Objective:** Verify profile search functionality

**Preconditions:** Employee logged in

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Click "Profiles" > "Search Profiles" in sidebar | Profile Search page |
| 2 | Enter client ID or name in search field | - |
| 3 | Select filters: Client Role (Primary), Profile Types (Online) | Filters applied |
| 4 | Click Search | Results grid shows matching profiles |
| 5 | Verify columns: ID, Name, Type, Status, Primary Client, etc. | All columns visible |
| 6 | Click "View" on a profile | Profile Detail page opens |

**Pass Criteria:** Profile search with filters works

---

### TC-PROF-002: View Profile Details

**Objective:** Verify profile detail page tabs

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to a profile | Profile Detail page |
| 2 | Verify header info: ID, Name, Type, Status, Created By/At | Info displayed |
| 3 | Click "Clients" tab | Grid of enrolled clients (Primary/Secondary) |
| 4 | Click "Services" tab | Grid of enrolled services |
| 5 | Click "Accounts" tab | Grid of enrolled accounts |
| 6 | Click "Users" tab | Grid of profile users with status, roles |
| 7 | Click "Indirect Clients" tab (if visible) | List of indirect clients for profile |

**Pass Criteria:** All tabs display correct data

---

### TC-PROF-003: Add Secondary Client to Profile

**Objective:** Verify adding secondary client

**Preconditions:** Profile exists, another client exists to add

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | On Profile Detail, click "Clients" tab | Client list shown |
| 2 | Click "Add Secondary Client" button | Dialog opens |
| 3 | Search for client by name or ID | Client found |
| 4 | Select account enrollment type: Automatic or Manual | - |
| 5 | If Manual, select specific accounts | Checkboxes available |
| 6 | Click "Add" | Success notification |
| 7 | Verify client appears in list as "Secondary" | Client added |

**Pass Criteria:** Secondary client added with accounts

---

### TC-PROF-004: Remove Secondary Client

**Objective:** Verify removing secondary client

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | On Clients tab, find secondary client | Has "Remove" button |
| 2 | Click "Remove" | Confirmation dialog |
| 3 | Confirm removal | Success notification |
| 4 | Client removed from list | No longer visible |

**Pass Criteria:** Secondary client removed (primary cannot be removed)

---

### TC-PROF-005: Enroll Service to Profile

**Objective:** Verify service enrollment

**Preconditions:** Profile type is INDIRECT or ONLINE

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | On Profile Detail, click "Services" tab | Service list shown |
| 2 | Click "Enroll Service" button | Dialog opens |
| 3 | Select service type from dropdown (PAYMENTS, REPORTING, etc.) | Service selected |
| 4 | Optionally add JSON config | Config field available |
| 5 | Optionally link accounts | Account checkboxes |
| 6 | Click "Enroll" | Success notification |
| 7 | Service appears in grid with Status: ACTIVE | Enrollment confirmed |

**Pass Criteria:** Service enrolled successfully

---

## 12. Employee Portal - User Management

### TC-USER-001: Add User to Profile

**Objective:** Verify creating a new user for a profile

**Preconditions:** Profile exists

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to Profile Detail > Users tab | User list shown |
| 2 | Click "Add User" button | Add User dialog opens |
| 3 | Enter Email: `newuser@test.com` | - |
| 4 | Enter First Name: `John` | - |
| 5 | Enter Last Name: `Doe` | - |
| 6 | Check role boxes: Viewer, Creator | Roles selected |
| 7 | Click "Add" | Success: "User created and invitation sent" |
| 8 | User appears in grid with Status showing pending | New user visible |
| 9 | Verify email sent to new user | Invitation email received |

**Pass Criteria:** User created, provisioned, invitation sent

---

### TC-USER-002: View User Details

**Objective:** Verify user detail page

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | On Users tab, click "View" on a user | User Detail page opens |
| 2 | Verify header: Name, Email, Status badge, User Type | All info shown |
| 3 | Click "Details" tab | Email, names, password/MFA status displayed |
| 4 | Click "Permissions" tab | Role management interface shown |
| 5 | Click "Activity" tab | Activity placeholder (future feature) |

**Pass Criteria:** User details accessible

---

### TC-USER-003: Update User Name

**Objective:** Verify updating user's name

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | On User Detail > Details tab | First Name, Last Name fields visible |
| 2 | Change Last Name to: `Smith` | Field updated |
| 3 | Click "Update Name" button | Success notification |
| 4 | Refresh page | Name persists as "Smith" |

**Pass Criteria:** Name update saved

---

### TC-USER-004: Lock User

**Objective:** Verify locking a user account

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | On Users tab, find active user | "Lock" action available |
| 2 | Click "Lock" | Lock dialog opens |
| 3 | Enter reason: `Suspected fraud` | Reason required |
| 4 | Click "Lock User" | Success notification |
| 5 | User status shows locked indicator | Status updated |
| 6 | User cannot login (verify separately) | Login fails |

**Pass Criteria:** User locked, cannot authenticate

---

### TC-USER-005: Unlock User

**Objective:** Verify unlocking a user

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Find locked user | "Unlock" action available |
| 2 | Click "Unlock" | Confirmation or immediate unlock |
| 3 | User status returns to active | Status updated |
| 4 | User can login again | Login succeeds |

**Pass Criteria:** User unlocked successfully

---

### TC-USER-006: Resend User Invitation

**Objective:** Verify resending invitation to pending user

**Preconditions:** User created but not completed FTR

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | On Users tab, find pending user | "Resend Invite" action |
| 2 | Click "Resend Invite" | Confirmation |
| 3 | Confirm action | Success: "Invitation resent" |
| 4 | User receives new email | New password reset link |

**Pass Criteria:** New invitation sent

---

### TC-USER-007: Manage User Roles - Standard

**Objective:** Verify standard role assignment

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | On User Detail > Permissions tab | Role interface shown |
| 2 | Verify standard roles displayed: Viewer, Creator, Approver, Security Admin | Role descriptions visible |
| 3 | Check "Approver" role (add) | Checkbox selected |
| 4 | Click "Save" | Success notification |
| 5 | Uncheck "Viewer" role (remove) | Checkbox cleared |
| 6 | Click "Save" | Roles updated |
| 7 | Verify changes persisted | Refresh shows updated roles |

**Pass Criteria:** Roles modified successfully

---

### TC-USER-008: Manage User Roles - Advanced Permissions

**Objective:** Verify granular permission assignment

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | On Permissions tab, check "Show Advanced Permissions" | Advanced section expands |
| 2 | See permission categories: PAYMENTS, REPORTING, SECURITY | Collapsible sections |
| 3 | Expand PAYMENTS section | Service-level and action-level options |
| 4 | Select specific permission mode | Individual permissions available |
| 5 | Check specific actions/accounts | Granular selection |
| 6 | Click "Save" | Permissions saved |

**Pass Criteria:** Advanced permissions configurable

---

## 13. Employee Portal - Indirect Client Management

### TC-IND-001: Create Indirect Client

**Objective:** Verify creating indirect client from profile

**Preconditions:** Profile with RECEIVABLES service enrolled

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | On Profile Detail, click "Indirect Clients" tab | Tab visible (if service enrolled) |
| 2 | Click "Add Indirect Client" button | Dialog opens |
| 3 | Enter Business Name: `Acme Payors Inc` | Required field |
| 4 | Enter Admin Contact Name: `Jane Smith` | Required field |
| 5 | Optionally enter Admin Email: `jane@acme.com` | Optional |
| 6 | Optionally enter Admin Phone: `555-1234` | Optional |
| 7 | Click "Create" | Success notification |
| 8 | Indirect client appears in grid | Status: ACTIVE |

**Pass Criteria:** Indirect client created

---

### TC-IND-002: View Indirect Client Details

**Objective:** Verify indirect client detail page

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Click "View" on indirect client | Detail page opens |
| 2 | Verify header: ID, Business Name, Status, Parent Client, Created At | Info displayed |
| 3 | Click "Profiles" tab | Indirect profiles listed |
| 4 | Click "Accounts (OFI)" tab | OFI accounts grid |
| 5 | Click "Contacts" tab | Related persons grid |

**Pass Criteria:** All indirect client data accessible

---

### TC-IND-003: Add OFI Account to Indirect Client

**Objective:** Verify adding bank account

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | On Accounts tab, click "Add OFI Account" | Dialog opens |
| 2 | Enter Bank Code: `001` (3 digits) | Validated |
| 3 | Enter Transit: `12345` (5 digits) | Validated |
| 4 | Enter Account Number: `1234567` (7-12 digits) | Validated |
| 5 | Optionally enter Account Holder Name | Optional field |
| 6 | Click "Add" | Success notification |
| 7 | Account appears in grid with formatted ID | New account visible |

**Pass Criteria:** OFI account added with validation

---

### TC-IND-004: Add/Edit Contact Person

**Objective:** Verify contact management

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | On Contacts tab, click "Add Contact" | Dialog opens |
| 2 | Enter Name: `Bob Johnson` | Required |
| 3 | Select Role: ADMIN or CONTACT | Dropdown |
| 4 | Enter Email: `bob@acme.com` | Optional |
| 5 | Enter Phone: `555-9876` | Optional |
| 6 | Click "Add" | Contact added to grid |
| 7 | Click "Edit" on the contact | Edit dialog |
| 8 | Change phone number | - |
| 9 | Click "Save" | Updated |

**Pass Criteria:** Contacts can be added and edited

---

### TC-IND-005: Create Indirect Profile

**Objective:** Verify creating profile for indirect client

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | On Indirect Client Detail > Profiles tab | "New Indirect Profile" button |
| 2 | Click "New Indirect Profile" | Create page opens |
| 3 | Profile Name pre-filled with business name | Can modify |
| 4 | Select Account Enrollment: Automatic or Manual | If manual, OFI account checkboxes appear |
| 5 | Click "Create Indirect Profile" | Success notification |
| 6 | Profile appears in list | New profile visible |

**Pass Criteria:** Indirect profile created

---

## 14. Employee Portal - Batch Import

### TC-BATCH-001: Import Indirect Clients via File

**Objective:** Verify batch payor enrolment

**Preconditions:** Valid JSON import file, profile with RECEIVABLES service

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | On Profile Indirect Clients tab | "Import from File" button visible |
| 2 | Click "Import from File" | File upload dialog |
| 3 | Select valid JSON file (< 5MB) | File attached |
| 4 | Click "Validate" (if separate step) | Validation runs, shows count |
| 5 | Click "Execute" or "Import" | Batch processing starts |
| 6 | Wait for completion | Progress indicator or polling |
| 7 | Success message with counts | X created, Y failed |
| 8 | View newly created indirect clients | Listed in grid |

**Pass Criteria:** Batch import processes file

---

### TC-BATCH-002: Batch Import - Validation Errors

**Objective:** Verify handling of invalid import data

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Select file with invalid/missing data | File selected |
| 2 | Upload and validate | Validation errors displayed |
| 3 | Error details show which items failed | Item-level errors |
| 4 | Can fix file and retry | Re-upload option |

**Pass Criteria:** Validation errors clearly reported

---

### TC-BATCH-003: Batch Import - Duplicate Detection

**Objective:** Verify duplicate external reference handling

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Import file with item having existing external reference | - |
| 2 | Execute import | - |
| 3 | Duplicate item marked as FAILED | Reason: "duplicate" or "already exists" |
| 4 | Non-duplicate items still processed | Partial success |

**Pass Criteria:** Duplicates rejected, others succeed

---

## 15. Indirect Client Portal

### TC-INDP-001: Indirect Client User Login

**Objective:** Verify indirect client user can login

**Preconditions:** Indirect client user created and completed FTR

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to Indirect Client Portal URL | Login page (same as Client Login) |
| 2 | Enter indirect user email | - |
| 3 | Complete login (password + MFA) | - |
| 4 | Redirected to Indirect Client Portal dashboard | Welcome message with business name |

**Pass Criteria:** Indirect user authenticated and redirected to correct portal

---

### TC-INDP-002: View Dashboard

**Objective:** Verify indirect client dashboard info

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | After login, on dashboard | Welcome message visible |
| 2 | Verify Client Information card | Business Name, Status, OFI Accounts count shown |
| 3 | Status formatted correctly | "Active", "Inactive", or "Pending" |

**Pass Criteria:** Dashboard shows correct business info

---

### TC-INDP-003: Manage OFI Accounts

**Objective:** Verify indirect client user can manage their accounts

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Click "Accounts" in sidebar | OFI Accounts page |
| 2 | Grid shows Bank Code, Transit, Account Number, Holder Name, Status | All columns visible |
| 3 | Add new account (if permitted) | Account creation works |
| 4 | View account details | Full info displayed |

**Pass Criteria:** Indirect user can view/manage their accounts

---

### TC-INDP-004: Manage Users (as Indirect Client Admin)

**Objective:** Verify indirect client admin can manage their users

**Preconditions:** User has admin role for indirect client

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Click "Users" in sidebar | Users page |
| 2 | View list of indirect client users | Grid displayed |
| 3 | Add new user (if permitted) | User creation dialog |
| 4 | Manage user roles | Role assignment |

**Pass Criteria:** Indirect client admin can manage users

---

## Test Execution Summary

### Test Categories

| Category | Test Count | Priority |
|----------|------------|----------|
| FTR (New User Registration) | 5 | Critical |
| Returning User Login | 6 | Critical |
| Password Reset | 3 | High |
| Guardian Reset | 2 | High |
| Passkey | 4 | Medium |
| Step-Up Auth | 3 | High |
| Client Portal - Indirect Clients | 2 | Medium |
| Employee Login | 3 | Critical |
| Employee - Client Search | 5 | High |
| Employee - Profile Management | 5 | High |
| Employee - User Management | 8 | Critical |
| Employee - Indirect Clients | 5 | High |
| Employee - Batch Import | 3 | Medium |
| Indirect Client Portal | 4 | Medium |
| **TOTAL** | **58** | |

### Execution Checklist

| Phase | Status | Sign-Off | Date |
|-------|--------|----------|------|
| Test Environment Setup | [ ] | | |
| FTR Tests | [ ] | | |
| Login Tests | [ ] | | |
| Password/Guardian Reset | [ ] | | |
| Passkey Tests | [ ] | | |
| Step-Up Tests | [ ] | | |
| Employee Portal Tests | [ ] | | |
| Indirect Client Portal Tests | [ ] | | |
| Regression | [ ] | | |

### Defect Log

| ID | Test ID | Severity | Summary | Status |
|----|---------|----------|---------|--------|
| | | | | |

---

## Appendix: Test Data Checklist

Before testing, ensure the following test data exists:

- [ ] Bank employee LDAP account
- [ ] Direct client with accounts
- [ ] Profile with services enrolled (PAYMENTS, RECEIVABLES)
- [ ] New user account (not yet registered) for FTR testing
- [ ] Existing user with TOTP enrolled
- [ ] Existing user with Guardian enrolled
- [ ] Existing user with passkey enrolled
- [ ] Locked user account
- [ ] Indirect client with OFI accounts
- [ ] Indirect client user account
- [ ] Sample batch import JSON file
- [ ] Auth0 Guardian app on test device
- [ ] Authenticator app (Google Authenticator) on test device
