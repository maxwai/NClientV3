# Connection recovery — build 420707 / 4.2.7-debug7

This update fixes confirmed client-side connection and session-handling defects.
It does not establish the cause of the reported failure on the phone: the cloud
proxy rejects HTTPS CONNECT requests to nhentai.net before reaching the site,
including a check outside the sandbox. Live site recovery could not be verified
from this environment.

## Changes

- Connection failures now distinguish DNS, TLS, timeout, HTTP access denial,
  rate limits, HTML verification pages and unreadable API data. Details exclude
  raw request URLs, cookies and response bodies.
- **Connection**, available from a browsing error or Settings, checks the public
  gallery API and CDN configuration on the device. Only successful responses
  enable **Return to browsing**, which retries the failed browsing operation.
- **Website verification** opens the official HTTPS site in a dedicated screen.
  It rejects navigation to other origins, disables file/content access and mixed
  content, and keeps certificate validation enabled. If a challenge appears,
  complete it and tap **Check connection**. No account is needed for this step.
- Only Cloudflare verification cookies are imported from that screen. The API
  uses the same user agent as the verification browser, and CDN discovery shares
  that verified session. Full-size image requests still use a separate client
  without API credentials or site cookies.
- Saved cookies now obey domain, path, expiry and HTTPS restrictions. Previously,
  the cookie jar returned every unexpired cookie for any requested URL. Clearing
  cookies now clears both WebView and HTTP-client stores. Session/auth preferences
  are excluded from Android backup and device transfer; cookie-value logging is
  removed. API-key attachment is restricted to the official HTTPS API origin.
- Random-gallery failures stop instead of retrying indefinitely; only one gallery
  is prefetched, and simultaneous requests are suppressed.
- API responses are size-bounded and HTML is recognized even when the server gives
  it the wrong content type. No image servers, media routes or extensions are
  guessed, and rate-limit backoff remains in place.

## Verification

Both Android variants build and pass 77 tests each (154 total, none skipped).
Android lint reports zero errors and 72 warnings. Automated checks cover anonymous requests, cookie
scope, verification-cookie import, clearing persisted sessions, credential
destination restrictions, CDN retry after verification, response-size limits,
real Android 12 connection-screen success/failure behavior, and WebView settings
and navigation policy. Existing reader, archive and layout regression tests run
alongside these checks. These use controlled responses and Android simulation;
they do not demonstrate a live nhentai response or physical-device compatibility.

The generated APKs use the same application ID and signer as build 420706, so they
can update NClientV3 Debug without uninstalling it. The previous UI and accent
choices remain available.

The upstream [NClientV3 source](https://github.com/maxwai/NClientV3) still uses the
API v2 routes used here. [Issue 264](https://github.com/maxwai/NClientV3/issues/264)
reports similar image failures and a network/DNS workaround; that is supporting
context, not proof of the cause on this device. The original
[NClientV2](https://github.com/Dar9586/NClientV2) was also checked for its older
website-verification implementation.

## Anonymous use and downloads

No personal API key is bundled. Public browsing is anonymous according to the
official API documentation supplied in this conversation. The site/CDN still see
the device's network address and requested content. History and saved galleries
remain on the device; downloaded files may be visible to file-management apps
according to the selected storage location and Android permissions.

The documented full-gallery archive endpoint requires an account API key and
server-side download support. This update preserves that requirement. Existing
archive extraction rejects unsafe paths and enforces decompression size limits.
