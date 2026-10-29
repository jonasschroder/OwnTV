# Free SHL channel recommendations

This integration uses TV-Programidag directly on-device. No paid API, trial,
credential, hosted backend, new permission or stream endpoint is needed.
Lab policy/evidence: [Broadcast Lab PR #30](https://github.com/jonasschroder/OwnTV/pull/30).
Only a probable recommendation is claimed from venue metadata.

## Data and UI path

The existing opted-in official SHL reader now retains the schedule's `Venue`
column in `ShlGame`/`SportFixture` and its existing JSON cache. Old cache entries
without venue remain readable and cannot generate a venue recommendation. Manual
profile/fixture keys, Room schemas and saved source preferences are unchanged.

While Sport is visible/resumed, SHL is enabled and an official schedule is fresh,
`PublicHockeySource` reads six reviewed free channel schedules, at most hourly.
The existing network pool and bounded cancellable reader are reused. No auth,
cookies or provider interceptors are inherited. Redirects are disabled; data is
limited to JSON programme metadata. A complete scan is required before publication.
The persistent cache reserves its request window before HTTP, including cancellation
and process death. Access/rate failures stop the scan; Retry-After is honored.
No background service or Home-triggered public fetch is added.

`publicHockeyAssignment` joins official SHL competition, exact normalized venue,
start −60/+10 minutes and end after kickoff. Duplicate/changed fixture identities,
competing fixtures at the venue, contradictory teams and overlapping different
broadcasts reject automatic selection. Arena renames are not guessed. One fresh
linear station yields PROBABLE_EXTERNAL; explicit teams can yield CONFIRMED.
No confidence score, TV4 Play promotion or numbered-station substitution.

Sport cards show **Trolig kanal** or **Bekräftad kanal**. Attribution links to
TV-Programidag. Names still appear when the owner's IPTV library has no playable
match; Watch is enabled only through existing bounded profile-authorized channel
resolution and quality preferences. Actual playback revalidates profile, source,
fixture identity and expiry. No external stream URLs are fetched. Local exact EPG
evidence takes precedence, including a conflicting external recommendation.
Manual channel selection remains the fallback; its persisted keys are unchanged.

## Real evidence and limits

Selected actual public observations from 29 October are regression fixtures under
`app/src/test/resources/public-hockey`, never production mappings. They reproduce
12 probable recommendations among 14 official references. Brynäs–Färjestad maps
probably to Sport Live 1; Växjö–Färjestad to Sport Live 3, observed D−2. Exact-team
confirmation and independently measured error rate are not established by these
venue-only observations. Uniqueness is checked within the six reviewed channels,
not every worldwide broadcaster. Missing/renamed arenas and schedule discrepancies
intentionally reduce coverage. Future API schema/channel-lineup changes fail closed
where detected and need review.

[Free API terms](https://tvprogramidag.se/utvecklare): attribution, max 60 requests/min/IP,
hourly updates and caching, no availability guarantee. This implementation uses six
spaced requests/hour, a one-hour source TTL and bounded local persistence. It reuses
the existing SHL opt-in and its schedule freshness/budget; it does not enable the
separate TVmatchen experiment or change its policy.

## Verification and rollback

Targeted JVM coverage includes real observed fixtures, venue extraction, old cache,
cache restart/budget/cancellation, conflicts, expiry/reschedules, aliases, explicit
numbered identities, EPG precedence and authorized local playback mapping. Existing
1080p/720p native card tests now transition confirmed→probable without losing
focus, exercise short/long OK and profile changes, and capture synthetic screenshots.
Those are emulator fixtures, not real Chromecast playback or broadcast ground truth.
Local JVM/instrumentation compile and CI results are reported in the draft PR.

After an explicitly approved signed QA update: check FBK's next match before
matchday, probable label, source link, saved HD/FHD choice and Watch; then check
missing IPTV, offline/expired data, local EPG confirmation, manual fallback, Back
and profile switch. No installation was performed during development.
Rollback: revert this PR. Added cache fields are optional and public metadata is
expendable; user profiles, IPTV credentials and selections require no migration.
