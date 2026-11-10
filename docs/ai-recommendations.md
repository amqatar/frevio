# AI recommendations

The home screen shows a **Recommended for you** row directly after Continue watching. Each profile gets its own picks with a one-line reason ("Because you finished Sintel"). Claude ranks the picks on the server. The Anthropic API key is a Worker secret like the other provider keys; the TV never sees it.

## How a row is made

1. **Candidates (server, no AI).** Only titles the profile can actually play: items from the profile's own addon catalogs, plus TMDB "similar" and "recommended" titles for what it watched (kept only if an addon lists them). The list then drops anything already watched, anything already in Continue watching, anything marked Not interested, and, for Kids profiles, anything not flagged for kids. The result is capped at about 150 titles, each sent as IMDb id, title, year, genres, runtime and a short synopsis.
2. **Signals (server).** The last 30 things watched, each tagged finished, abandoned or in progress; the profile's library; thumbs up/down; Not interested marks; and Simkl history when the profile is linked. No names, emails or device details are sent.
3. **Ranking (Claude).** Claude returns 10 picks as structured JSON: IMDb id plus a reason of 60 characters or less that names a title the profile watched. **The server drops any id that isn't in the candidate list**, so the model can't invent titles or point at something no addon can play.
4. **Fallback.** If the call fails or is refused, the row becomes "Because you watched X", built from TMDB similar titles with no AI. Users still see a row.

## When it runs

- **Nightly** for every profile active in the last 14 days, through the Message Batches API (half the price of normal calls, results within hours).
- **On demand** after a profile finishes a title, at most once every 6 hours per profile.
- Results are stored per profile for 24 hours (table `recommendations`) and served from there, so opening the home screen never waits on the model.

## API (`/v1`)

- `GET /v1/profiles/{id}/recommendations` returns `{generatedAt, source: "ai" | "fallback", items: [{imdbId, reason}]}`.
- `POST /v1/profiles/{id}/recommendations/feedback` with `{imdbId, signal: "not_interested" | "thumbs_up" | "thumbs_down"}`. Not interested removes the item at once and feeds the next run.
- Profile setting `ai.recommendations`: on (default) or off. When it's off, the row uses the no-AI fallback.

## Model and request (Worker, TypeScript SDK)

- **Model:** `claude-opus-5-5`. Effort is set to `low`, because this is a ranking task over a fixed list, not open reasoning.
- **Structured output** via `output_config.format`. The SDK's `messages.parse` validates the result against the schema.
- **Refusal fallback** turned on (`fallbacks: "default"` with the `server-side-fallback-2026-07-01` beta), plus the no-AI fallback row above.
- **Prompt caching:** the system prompt (instructions and output rules) is identical for every profile and goes first, so it's cached; the per-profile candidates and signals come after it.

Sketch, to be confirmed against the SDK when phase 2 builds it:

```ts
const Picks = z.object({
  picks: z.array(z.object({ imdbId: z.string(), reason: z.string().max(60) })).max(10),
});

const res = await client.messages.parse({
  model: "claude-opus-5-5",
  max_tokens: 4000,
  output_config: { effort: "low", format: zodOutputFormat(Picks) },
  system: RECOMMENDER_SYSTEM_PROMPT,           // stable, cached
  messages: [{ role: "user", content: JSON.stringify({ watched, library, feedback, candidates }) }],
});
const valid = res.parsed_output?.picks.filter(p => candidateIds.has(p.imdbId)) ?? [];
```

## Cost (estimate, pre-measurement)

A run sends about 12K input tokens (system prompt, roughly 150 candidates, history) and gets back about 1.5K output tokens. At Claude Opus 5.5's list price ($4 input / $20 output per million tokens) that's about $0.08 per run, or about **$0.04 through the nightly batch**. That's roughly **$1.20 per active profile per month**, more if on-demand refreshes are frequent.

**Your call:** Claude Haiku 5.5 ($0.10 / $0.50) would cost about 40 times less, roughly 3 cents per profile per month, but its picks may be weaker. The plan is to run both models on the same sample of profiles in phase 2, compare the picks, and then choose.

## Privacy

- Only titles and watch signals go to the model, under a random per-profile id. No account email or names.
- AI recommendations can be turned off per profile (Settings › Integrations). Kids profiles get a pre-filtered candidate list.
- The About screen states that recommendations are generated with Claude.

## Phase placement

- **Phase 2:** the routes, nightly batch job, storage, feedback, and the no-AI fallback. Tests use recorded model responses (no live key in CI).
- **Phase 4:** the TV row, the reason line, Not interested on long-press, and the settings toggle. The Windows prototype already shows this with mock picks.
