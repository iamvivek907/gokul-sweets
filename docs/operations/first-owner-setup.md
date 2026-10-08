# First owner setup and account recovery

This change is additive. Existing accounts, roles, MFA enrollment, Add staff and staff password reset flows are retained. Setup creates one `OWNER_ADMIN`; that owner can add further owners through the existing Staff management screen.

## Before deploying

Staff users are shared across deployment environments within a database. The one-time latch follows that staff directory, not the DEV/PROD flag. If the production database already contains an owner (including an inactive owner), setup stays closed. Migration V121 closes it automatically and does not change that account or generate a key for it. Use a separate production database if you need a separate production staff directory. Do not delete existing users to run setup.

Secure staff sessions must be enabled (`GOKUL_FEATURES_SECURE_STAFF_SESSIONS=true`), the deployment environment must be `DEV` or `PROD`, and the existing `STAFF_MFA_ENCRYPTION_KEY`, HTTPS API URL and exact frontend CORS origin must already be configured. Keep the existing MFA encryption key stable for databases with enrolled accounts. Production frontend builds must point to the production API.

## Enable first-owner setup

On a trusted operator machine, generate a random setup key and its SHA-256 hash:

```python
import hashlib, secrets
key = secrets.token_urlsafe(32)
print('Private setup key:', key)
print('STAFF_OWNER_SETUP_KEY_HASH:', hashlib.sha256(key.encode()).hexdigest())
```

Store the private key securely; do not put it in source control, URLs, logs or any `NEXT_PUBLIC_*` variable. On the backend only, set:

```text
STAFF_OWNER_SETUP_ENABLED=true
STAFF_OWNER_SETUP_KEY_HASH=<the 64-character lowercase hash>
```

Deploy the backend migrations and frontend changes through the normal release process. Complete the rollout on all backend instances before using setup or recovery; old backend versions do not enforce credential-bound enrollment challenges. Open `/admin/setup` on the matching storefront. Enter the private setup key, owner name, username and a new password of at least 12 characters (at most 72 UTF-8 bytes). Copy the displayed account recovery key to secure offline storage. It is shown only in the successful response; the database stores its hash only. Confirm that it is saved, then sign in through `/admin/login` and complete the existing authenticator enrollment. Save the MFA recovery codes separately.

Setup closes transactionally after the first owner. Concurrent requests cannot create a second owner. Deactivating/deleting that owner does not reopen setup. After completion, set `STAFF_OWNER_SETUP_ENABLED=false` and remove `STAFF_OWNER_SETUP_KEY_HASH` as deployment housekeeping. Recovery remains available independently of the setup flag.

If the setup response is lost, try login with the chosen credentials before retrying setup. The account may already exist. After MFA enrollment, generate a replacement recovery key from Account security if you did not receive the first key.

## Existing owners and username changes

Owners can open **System → Account security** to generate or replace their own account recovery key, or change their own username. Both require the current password and a fresh authenticator code or unused MFA recovery code. Changing the username signs out all of that owner’s devices; the password, MFA enrollment, role and branch access stay intact. Account security does not modify other staff accounts. Existing Staff management password resets continue to work.

Each owner has a separate recovery key. Rotating it immediately invalidates the previous key. Save the replacement before leaving the page. If a rotation response is lost, sign in and explicitly generate another key using a fresh MFA code; mutations are never retried automatically.

## Forgotten owner username or password

Open `/admin/recover`. Enter the saved account recovery key and choose the username and password. A successful recovery consumes that key, issues a replacement, signs out all existing sessions and removes pending enrollment challenges. Established MFA enrollment and MFA recovery codes, role and branch permissions are preserved. Migration V122 binds enrollment challenges to the staff credential version, so a concurrent old-password sign-in cannot create a usable challenge after recovery. Existing pending challenges are preserved when the migration runs; mixed old/new backend versions require retrying login if an old instance issues a challenge without a credential snapshot. Save the replacement and sign in with MFA normally. Inactive or demoted owners cannot use this feature to reactivate themselves or regain privileges.

An account recovery key does not bypass established MFA. Keep account recovery keys and MFA recovery codes separately. If both the account key and login credentials are lost, another authorized owner can use the existing staff password reset flow. Loss of all credentials and all MFA recovery methods requires controlled operator support; there is no public bypass or reopening of first-owner setup.

The new routes use existing trusted-Origin enforcement. Signed-in changes additionally require the existing staff session and CSRF token. Credential actions are throttled by server-observed client address (and staff ID for signed-in changes); setup/recovery responses are not cached. Behind a proxy, configure trusted forwarding through the normal hosting configuration rather than trusting arbitrary client-supplied forwarding headers. Only stale limiter buckets created by this feature are pruned in bounded batches; existing login limits are retained.
