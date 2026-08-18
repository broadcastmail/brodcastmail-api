# Onboarding Architecture

## Overview
Two-step onboarding flow that runs once per account creation. 
Supabase Oauth is responsible for authentication and proves
ownership of the Supabase project. BroadcastMail API then
creates a read-only role (via the Management API) that introspects
schema, connects Resend as an email provider, and creates an account.
On flow completion onboarding session is destroyed, recap is
shown, and the dashboard session begins.

---
## Session lifecycle
State lives in `OnboardingSessionStore` — in-memory`ConcurrentHashMap`, keyed by a random UUID token. 
TTL is 30 minutes from creation. 

Carried client-side via `
onboarding_session` httpOnly cookie (`SameSite=Lax`, `Max-Age=1800`). The cookie is set after OAuth completes and cleared on completion or expiry.

Partial sessions (multiple-project flow) live in `OnboardingSessionStore` as a separate partial entry, promoted to a full session after project selection.

On completion — `OnboardingSessionStore` entry deleted, `bm_session` httpOnly cookie set with the raw API key, redirect to `/`.


## Step machine
CONNECT_SUPABASE → CONFIRM_SCHEMA → CONNECT_RESEND → CONFIRM_ACCOUNT
Resolved by `GET /api/v1/onboarding/status` on every frontend page load:

| Step | Condition |
|---|---|
| `CONNECT_SUPABASE` | No cookie, or invalid/expired session |
| `CONFIRM_SCHEMA` | Session exists, schema not yet confirmed |
| `CONNECT_RESEND` | Schema confirmed, no Resend details |
| `CONFIRM_ACCOUNT` | Schema confirmed + Resend details present |

Frontend routes to the correct page based on the returned step. Backend enforces step preconditions via `requireSchemaConfirmed()` and `requireResendDetails()` on `OnboardingSession`.
---
## Call chain
### OAuth entry

`GET /api/v1/oauth/supabase/authorize`
* OAuthSupabaseService.buildAuthorizationUrl()
* redirect to Supabase OAuth

`GET /api/v1/oauth/supabase/callback
* OAuthSupabaseService.handleCallback()
* OAuthStateStore.validate(state)
*SupabaseManagementClient.exchangeCodeForTokens(code)
*SupabaseManagementClient.getOwnerEmail(accessToken)
→ AccountRepository.findByEmail()
[returning user]
→ AccountService.rotateApiKey()
→ CookieService.createSessionCookie()
→ OAuthCallbackResult.ReturningUser → redirect /
[new user, single project]
→ setupProjectAndCreateSession()
→ OnboardingSessionStore.create()
→ CookieService.createOnboardingCookie()
→ OAuthCallbackResult.NewUserSingleProject → redirect /onboarding
[new user, multiple projects]
→ OnboardingSessionStore.createPartial()
→ OAuthCallbackResult.NewUserMultipleProjects → redirect /onboarding/select-project

POST /api/v1/oauth/supabase/select-project
→ OAuthSupabaseService.selectProject()
→ OnboardingSessionStore.getPartial()
→ setupProjectAndCreateSession()
→ OnboardingSessionStore.invalidatePartial()
→ OAuthCallbackResult.NewUserSingleProject → redirect /onboarding


### Schema step

POST /api/v1/onboarding/schema/detect
→ OnboardingController.detectSchema()
→ OnboardingSessionStore.get()
→ SchemaIntrospectionService.detect()
→ external JDBC → information_schema queries
→ OnboardingSessionStore.update() ← stores detectedColumns

POST /api/v1/onboarding/schema/confirm
→ OnboardingController.confirmSchema()
→ OnboardingSessionStore.get()
→ SupabaseManagementClient.executeSql() ← CREATE ROLE + GRANT statements
→ OnboardingSessionStore.update() ← schemaDetails.confirmed = true

POST /api/v1/onboarding/schema/test ← fallback path only
→ OnboardingController.testConnection()
→ OnboardingSessionStore.get().requireSchemaConfirmed()
→ DriverManager.getConnection() ← reader role JDBC
→ SELECT COUNT(*) FROM auth.user_emails

### Resend step

POST /api/v1/onboarding/resend/validate
→ OnboardingController.validateResend()
→ ResendClient.sendTestEmail()
→ OnboardingSessionStore.update() ← stores encryptedResendApiKey + fromAddress


### Completion

POST /api/v1/onboarding/complete
→ OnboardingController.complete()
→ OnboardingService.completeOnboarding()
→ AccountCreationService.createFromOnboarding()
→ Account saved
→ OAuthToken saved
→ ResendClient.registerWebhook()
→ EmailProvider saved
→ returns AccountCreationResult(rawApiKey, accountId)
→ ConnectionService.createConnection(accountId, session)
→ CookieService.createSessionCookie(rawApiKey)
→ CookieService.clearOnboardingCookie()
→ redirect /

## External dependencies

 Dependency | Used for | Called from |
|---|---|---|
| Supabase Management API | Token exchange, email lookup, project list, SQL execution | `SupabaseManagementClient` |
| External JDBC (reader role) | Schema introspection, connection test | `SchemaIntrospectionService`, `OnboardingController.testConnection()` |
| Resend API | Test email during onboarding, webhook registration | `ResendClient` |

## Error states

| Step | Failure | Handling |
|---|---|---|
| OAuth callback | Invalid state param | `OAuthStateValidationException` → 400 |
| OAuth callback | No Supabase projects | `NoSupabaseProjectsException` → 400 |
| Schema confirm | SQL execution fails | Exception bubbles → frontend shows fallback screen |
| Schema test | JDBC connection fails | `ConnectionTestFailedException` → 422 with inline message |
| Resend validate | Invalid API key | Resend returns error → 422 with inline message |
| Any step | Expired/invalid session cookie | `InvalidOnboardingSessionException` → status returns `CONNECT_SUPABASE` |
| Any step | Missing session cookie | status returns `CONNECT_SUPABASE` |


## Security notes
**Session fixation** — onboarding session token is a random UUID, single-use per step. Partial sessions are invalida ted after project selection.

**Credential encryption** — Supabase access token, refresh token, and reader role password are AES-256-GCM encrypted before storing in `OnboardingSession`. 
Resend API key is encrypted before storing in `
EmailProvider`.

**Cookie config** — `onboarding_session`: `httpOnly`, 
`secure` in production, `SameSite=Lax`, 30 minute expiry. Cleared immediately on completion or error.

**Reader role password** — generated via `SecurityUtil.generatePassword()` (192-bit random, base64url). Never stored in plaintext — only the encrypted form lives in the session. TODO: CWE-316 — decrypt to `char[]` instead of `String`.
