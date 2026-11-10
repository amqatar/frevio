# Metadata enrichment and watch tracking

Addons stay the source of catalogs and streams. Four outside services add detail and tracking on top. **Every provider key and app secret lives only in the API server (Cloudflare Worker secrets).** Apps never hold them: they call our `/v1` API, which calls the providers, caches the answers and returns one merged result.

## Providers

| Provider | What it adds | Key handling | Terms to respect |
|---|---|---|---|
| **TMDB** | Backdrops, posters and logos in more sizes; cast with photos; trailers; collections ("part of…"); localized titles and synopses (Arabic, etc.); "more like this" | `TMDB_READ_TOKEN` Worker secret | Free for **non-commercial** use with attribution: TMDB logo plus "This product uses the TMDB API but is not endorsed or certified by TMDB" in About. Commercial use needs a written agreement with TMDB, possibly with fees |
| **IMDb** | The IMDb rating and vote count; IMDb IDs (`tt…`) as the shared key across addons, TMDB, MDBList and Simkl | No direct key | IMDb has no free official API. Data is licensed through AWS Data Exchange with contract pricing. So: use IMDb **IDs** freely, get the IMDb **rating** through MDBList, and never scrape imdb.com |
| **MDBList** | Ratings in one call: IMDb, Rotten Tomatoes (critics and audience), Metacritic, Letterboxd, Trakt, TMDB; curated lists usable as home rows | `MDBLIST_API_KEY` Worker secret | Free tier reported at about 1,000 requests/day (community guide, unverified; check docs.mdblist.com). Caching below keeps us far under it; supporter tiers raise it |
| **Simkl** | Watch tracking per profile: scrobbles, history, watchlist, ratings; imports history into Continue watching | `SIMKL_CLIENT_ID`, `SIMKL_CLIENT_SECRET` Worker secrets; each profile's Simkl access token stored encrypted server-side | App registration is free. Use the PIN flow for the TV. Scrobble with start/pause/stop events, never by polling |

## API additions (`/v1`)

**Enriched metadata**
- `GET /v1/meta/{type}/{imdbId}?lang=en` returns the merged record: TMDB artwork, cast, trailers and localized text; MDBList ratings; attribution flags. Apps show what's present and skip the rest.
- **Caching** in the Worker (Cache API plus KV):
  - TMDB data: 7 days
  - ratings: 24 hours
  - "not found": 6 hours

  Every device and profile shares the same cache entries, so the provider is called about once per title per day, not once per viewer.
- **Failure:** if a provider is down or a limit is hit, the API returns what it has, with `partial: true`. Addon metadata always works without enrichment.

**Simkl, per profile**
- `POST /v1/profiles/{id}/integrations/simkl/pin` makes the Worker request a PIN from Simkl with our client ID. The TV shows the code and `simkl.com/pin`.
- `GET /v1/profiles/{id}/integrations/simkl/pin` is polled by the TV until it's linked. The Worker stores the access token against the profile; the TV never sees it.
- `DELETE /v1/profiles/{id}/integrations/simkl` unlinks the profile.
- **Scrobbling:** the player already sends progress to `/v1/profiles/{id}/progress`. When the profile is linked, the Worker forwards play, pause and stop to Simkl's scrobble endpoints. If Simkl is unreachable, the event waits in a queue and is retried later.
- **Import:** on link, and then on a schedule, the Worker pulls Simkl history and watchlist and merges them into the profile's progress and library. The shared Continue watching rules apply unchanged.

## Settings the user sees

- **Settings › Integrations:**
  - Simkl: connect (PIN on screen) or disconnect, last sync time, "Sync now"
  - Enriched metadata (TMDB): on/off, language
  - Ratings shown: which of IMDb, Rotten Tomatoes, Metacritic and Letterboxd appear on title pages
- **Settings › About:** TMDB logo and the required attribution notice.

## Decision needed: commercial or not

If Frevio stays free and private, TMDB's non-commercial terms fit. If it ever earns money (a subscription, ads or a paid tier), we'd need a TMDB commercial agreement and an MDBList supporter or commercial tier before launch. Simkl's app registration terms should be re-read at that point too.

## Phase placement

- **Phase 2 (API):** the routes, Worker secrets and caching above, with tests that use recorded provider responses (no live keys in CI).
- **Phase 3 (shared core):** the merged-metadata model and Simkl link state in `shared/`.
- **Phase 4–5 (TV):** ratings strip, cast photos, trailers, the Integrations settings and the scrobbling indicator. The Windows prototype already shows these with mock data.
