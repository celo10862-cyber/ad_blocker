# NetShield AdBlocker — Premium rebuild (v1.3.0)

NetShield is an Android local DNS filtering VPN. This build expands the existing prototype with a premium glass-inspired dashboard, theme selection, protection modes, app selection, persistent statistics, domain allow/block rules, IPv4/IPv6 UDP DNS handling, and an optional user-consented local CA certificate installer.

## Implemented

- **Appearance:** System, Light and Dark modes. The cards use translucent/glass-inspired styling; this is not a live backdrop blur effect on every Android version.
- **Protection modes:** Standard blocks the bundled advertising domains; Balanced adds tracker domains; Strict adds the additional analytics domains.
- **Per-app VPN selection:** Android's `VpnService.Builder.addAllowedApplication` is used. Select apps in the app picker. An empty selection means the VPN is not restricted by package and all apps may use its DNS route. Restart protection after changing the selection.
- **DNS filtering:** UDP DNS queries to NetShield's configured local IPv4 and IPv6 DNS addresses are parsed and either answered with NXDOMAIN or forwarded to Cloudflare's `1.1.1.1` over a protected UDP socket.
- **Domain rules:** Add custom blocked domains and explicit allowlist exceptions. Allowlist entries take precedence.
- **Filter update:** Imports hosts-format domains from StevenBlack's public hosts list. Update failures are surfaced to the user.
- **Statistics:** Blocked DNS query count persists between launches and can be reset.
- **Local CA installer:** Generates a local RSA CA keypair in Android Keystore and presents Android's user certificate installation flow. The private key is non-exportable through this app.
- **Build:** GitHub Actions builds debug and release APKs. Release signing is persistent when the documented repository secrets are configured.

## Critical network limitations — read before using

This is **not AdGuard-equivalent and is not a full-device packet proxy**.

- The VPN currently processes ordinary IPv4/IPv6 UDP DNS packets addressed to its configured DNS endpoints. IPv4 fragments, IPv6 extension headers/fragments, TCP, arbitrary UDP, QUIC, DNS-over-TLS and DNS-over-HTTPS are not inspected.
- Apps using hard-coded DNS, encrypted DNS, their own DoH/DoT implementation, or private resolver stacks can bypass this DNS filter.
- The VPN does not route arbitrary TCP/UDP traffic through a tun2socks engine. It cannot filter arbitrary URLs, page content, same-domain video ads, or encrypted HTTPS requests.
- Per-app selection controls which apps use the VPN interface; it does not provide per-app packet inspection or per-app TLS interception.
- Blocked statistics count DNS queries blocked by this service, not every ad, tracker, packet or request.
- Traffic/battery savings are not estimated because the current engine does not observe all traffic.

## Local CA certificate safety

The local CA action is optional and requires explicit confirmation followed by Android's own certificate installation UI. Android may show the certificate as a user-installed CA. A CA certificate alone does **not** intercept or decrypt HTTPS. This project does not include a TLS interception proxy, does not silently install a system CA, and does not bypass certificate pinning. Modern Android apps commonly do not trust user-installed CAs by default. Remove the user CA from Android Security settings if you no longer need it.

The CA private key is created in Android Keystore; the certificate can be installed as a public trust anchor. Treat any installed CA as sensitive and remove it when no longer required.

## Build with GitHub Actions

1. Push this project to a GitHub repository's `main` or `master` branch.
2. Open **Actions → Build NetShield Android APK**.
3. Download the debug APK for device testing. The release APK is unsigned unless signing secrets are configured.

For persistent release signing, configure these repository secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Use your own long-lived keystore. Never generate a new release key on each workflow run, and never commit the keystore or passwords.

## Validation and testing

The source archive is XML-parsed and structurally checked. A complete Android Gradle build and device-level VPN test were not run in this environment. Before daily use, verify:

1. VPN permission prompt, foreground notification, start/stop and reboot behavior.
2. IPv4 and IPv6 DNS resolution with protection on and off.
3. DNS blocking, allowlist precedence and all three modes.
4. App selection after restarting protection.
5. Certificate installation and manual removal from Android settings.
6. Compatibility with apps that use private/encrypted DNS.

Do not interpret a successful APK build as proof that all network paths are filtered. This is a DNS-filtering implementation with clearly scoped support, not a universal ad blocker.
