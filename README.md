# NetShield AdBlocker

NetShield is an Android DNS filtering VPN with a small in-app browser and locally managed domain rules.

## What this revision changes

- Reworked the dashboard into a dark, card-based privacy UI with clearer protection state and statistics.
- Validates custom domains and persists custom rules locally.
- Updates the hosts parser to accept both `0.0.0.0` and `127.0.0.1` entries, strips comments, and reports download errors.
- Tightens the DNS-only VPN packet checks and uses a protected upstream UDP socket.
- Hardens the embedded WebView: disables file/content access, blocks mixed content, cancels TLS certificate errors, restricts navigation to HTTP(S), and applies cosmetic filters only inside the browser.
- Adds local CA creation using an Android Keystore-backed RSA private key and a user-approved Android certificate-install prompt.
- Updates GitHub Actions to build debug and release APKs. Persistent release signing is supported through repository secrets.

## Important scope and limitations

This is **DNS filtering**, not a complete AdGuard replacement. The VPN currently processes IPv4 UDP DNS requests routed to its private DNS address. It does not implement general TCP/UDP forwarding, IPv6 DNS, DNS-over-TLS, DNS-over-HTTPS, QUIC inspection, or full HTTPS man-in-the-middle proxying. Some apps and browsers can bypass DNS filtering by using encrypted DNS. The embedded browser's cosmetic rules affect only that WebView and are not universal ad removal.

The local CA feature only creates an X.509 CA certificate and offers it to Android's certificate installer. It does not intercept traffic, issue per-site certificates, or make other apps trust the CA. Android requires the user to approve installation; modern apps commonly do not trust user-installed CAs. Do not install a CA unless you understand the security implications. The private key is generated in Android Keystore and is not exported by the app. Removing the app does not necessarily remove a CA that the user installed in Android settings; remove it separately from the device's trusted credentials settings.

## Build

Open this repository in Android Studio or run:

```bash
gradle --no-daemon assembleDebug assembleRelease
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. Without a signing key, the release output is unsigned.

## Stable release signing in GitHub Actions

Create a long-lived release keystore yourself and add these GitHub Actions repository secrets:

- `RELEASE_KEYSTORE_BASE64`: base64-encoded JKS/PKCS12 keystore file
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Do not generate a new release key for every build: Android will reject updates signed with a different key. Keep the keystore and passwords private and backed up securely.

## Privacy

The app does not upload browsing history or the CA private key. Blocklist synchronization downloads a public hosts file from StevenBlack's GitHub repository. The VPN sends allowed DNS queries to Cloudflare's public resolver at `1.1.1.1`.
