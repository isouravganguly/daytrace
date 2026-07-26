# Hold This Thought — MVP implementation plan

## Product rule

Capture must never wait for the network or AI. Every typed or spoken thought is
written to Room first and appears saved immediately. Classification, cloud sync,
and embeddings are enrichment jobs.

## Stack

- Android: Kotlin, Jetpack Compose, Material 3, single-activity architecture.
- Local source of truth: Room.
- Background delivery: WorkManager with connectivity constraints and idempotent
  UUID upserts.
- Speech: Android `SpeechRecognizer`, requesting offline recognition when the
  installed recognition service supports it.
- Cloud: Supabase Auth, Postgres, Row Level Security, Edge Functions, pgvector.
- AI: Gemini through a Supabase Edge Function. The Gemini key must never ship in
  the APK. Use a Flash-class model for classification and an embedding model for
  semantic retrieval.

## Vertical slices

1. Capture foundation — implemented
   - Work/personal visual modes
   - Typed capture
   - Hold-to-speak capture
   - Local Room persistence
   - Local heuristic note/todo/thought classification
   - Drawer destinations and literal offline search
   - Completed todos excluded from default queries

2. Cloud sync — implemented for MVP
   - Add Supabase publishable URL/key via untracked local properties
   - Anonymous or email auth for a stable user id
   - WorkManager upload and reconciliation
   - Retry via WorkManager when failures occur

3. AI enrichment — partly implemented
   - Edge Function calls Gemini with structured JSON output
   - Update kind/title/tags without changing the original transcript
   - Generate embeddings asynchronously
   - Hybrid search: local text results immediately, semantic cloud results after
     the network responds
   - Current status: classification and embeddings are implemented in the Edge
     Function; semantic search exists server-side but is not yet wired into the
     Android search UI.

4. Todo lifecycle and polish
   - Completion, optional due date, reminder
   - Never show completed/failed sync items on capture screen
   - Quiet sync status inside settings, not the base screen
   - Personal-mode motion/illustration pass and work-mode accessibility pass

## Hosted Supabase status

- Supabase project is configured for MVP testing.
- Anonymous auth has been enabled.
- `GEMINI_API_KEY` has been added as an Edge Function custom secret.
- `public.thoughts`, RLS, indexes, vector search RPC, and Edge Function code live
  in this repository.
- If the remote project is reset or recreated, reapply the migration and redeploy
  `supabase/functions/thought-machine`.
