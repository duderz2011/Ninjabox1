# NinjaBox authentication

NinjaBox is gated by the NinjaTreats ClipBox panel.

## Public app endpoints

- Login: `POST https://ninjatreats.xyz/project/clipbox/panel/api/app/login.php`
- Status: `GET https://ninjatreats.xyz/project/clipbox/panel/api/app/status.php`
- Logout: `POST https://ninjatreats.xyz/project/clipbox/panel/api/app/logout.php`

The Android app does not contain a panel administrator key.

On login, NinjaBox sends the username/password plus a stable per-install device ID. The panel returns a random device-bound token. NinjaBox stores the token and username, but does not retain the password.

A saved token is validated when the app starts. Invalid, expired or revoked sessions return to the NinjaTreats login screen.

## Panel patch

The corresponding panel patch adds:

- NinjaBox user management
- hashed passwords
- hashed device-bound session tokens
- configurable account expiry and device limits
- session revocation
- login rate limiting

The app package ID on this branch is `xyz.ninjatreats.ninjabox`.
