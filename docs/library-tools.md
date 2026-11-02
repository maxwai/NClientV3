# Library tools — build 420710 / 4.2.7-debug10

## History

Use the search icon in History to search saved titles, artist names and gallery
IDs. Multiple words must all match. The filter icon offers **All stories**,
**In progress**, and **Reached last page**. Unknown page totals in legacy entries
are not treated as finished. Search and filter combine, survive activity recreation,
and never request online metadata. Older records without artist metadata can still
be found by title or ID. Clearing a search does not remove history.

## Downloaded reading

Tap **Read downloaded stories** above the bottom navigation to enter the local
library. Queued downloads and their partial folders are hidden from this view.
Tap **Browse online** to return to Explore. The chosen library is remembered for
the next launch, after the existing PIN/biometric unlock when enabled. Entering
downloaded reading clears the previous navigation stack, so Back does not reopen
an old online browsing screen. Storage permission is requested when required.

This is a downloaded-library view, not a global network-disabling switch. Reading
the saved files requires no connection; existing background transfers are not
cancelled. Cached online pages are not presented as downloaded galleries. Returning
online restores access to the regular download queue through Downloads.

Existing one-tap resume and gallery-details actions remain available. No additional
page bookmarks or notes were added.

## Provider verification

On 2026-11-02, requests for hanime.tv robots/sitemap and possible API documentation
locations were rejected with `Tunnel connection failed: 403 Forbidden` by the cloud
proxy, before any response from the website. This does not establish whether those
paths exist, whether a photos section exists, or whether a supported API is offered.
No third-party image provider was integrated or treated as verified.

## Validation

Tests cover title/artist/ID matching, progress filters, search/filter restoration,
downloaded-library navigation and remembered launch choice, returning online,
and exclusion of queued downloads while preserving local stories. The complete
reader/history/cache/theme suite runs for both APK variants. UI layouts are rendered
with fictional fixtures; no physical-device test is claimed.

The full regression run passed 98 tests per variant (196 total). After the final
download-list refresh fix, all 98 Android 9+ tests and the six focused Android 8
library tests passed again. Both APKs build; lint reports zero errors and 82
warnings. Signatures match the previous APKs for installation as an update.
