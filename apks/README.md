# Installable development APKs

- **Android 9 and newer (including Android 12):** [Download the connection update](NClientV3-4.2.7-connection-update-android9plus.apk)
- **Android 8 / 8.1:** [Download the connection update](NClientV3-4.2.7-connection-update-android8.apk)

On GitHub, open the APK and choose **Download raw file**, then open the file on
your phone. Build **420707 / 4.2.7-debug7** updates **NClientV3 Debug**, retains its
settings, reading progress and API key, and uses the same signing key as the
previous debug build. It installs alongside the production app.

This build adds connection recovery and fixes cookie/session handling and random
gallery retry loops. Open **Connection** from an error message or Settings to
check the public API and CDN configuration on your phone. If the site requires
verification, use **Website verification**, complete it, then check again.

Live nhentai access is blocked by this cloud environment's proxy, so recovery on
the phone is **not yet confirmed**. See [changes and verification limits](../docs/connection-update.md).

The warm dark UI retains its spacious layouts, bottom navigation,
separate cover captions, a gallery header with Read/Resume controls, floating
reader controls and redesigned settings. Under **Settings → Accent color**, choose
**Gold, Jade, Ocean, Amethyst, Rose or Copper**. Light and true-black themes remain
available. See [UI details and rendered previews](../docs/premium-ui.md).

The working reader changes from build 420705 remain: documented `/api/v2/cdn`
discovery, exact API media paths, bounded failover among advertised servers, HTTP
429 backoff and official ZIP archive downloads.
Public reading needs no API key according to the supplied docs. Gallery archive
downloads require a valid API key in Settings and must be enabled by the server.

Both Android variants pass 77 tests each (154 total); lint reports zero errors
and 72 warnings. See the connection-update notes for validation scope.
The new APK signing identities match the previous build. Verify all retained APK
checksums with `sha256sum -c SHA256SUMS` from this directory.

Earlier `reader-fix` and `premium-ui` APKs are retained for reference. They have a lower version
code, so Android will not install them over this update normally. See
[API implementation details](../docs/reader-fixes.md).
