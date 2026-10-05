# Hold This Thought docs

Start here when picking up this project.

- [Project context](PROJECT_CONTEXT.md): the idea, who it's for, and non-negotiables.
- [Architecture](ARCHITECTURE.md): code map, data model, sync algorithm, search, Claude integration.
- [Roadmap](ROADMAP.md): what's done and what to build next.
- [LLM handoff](LLM_HANDOFF.md): practical notes for a coding agent picking this up.

## Setup

### Android

`local.properties` (untracked) needs:

```
sdk.dir=/Users/<you>/Library/Android/sdk
SUPABASE_URL=https://<project>.supabase.co
SUPABASE_PUBLISHABLE_KEY=<publishable key>
```

Without the Supabase values the app still works, local-only.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Supabase (once per project)

1. Free-tier projects pause after a week of inactivity. If the app says
   "Can't reach your backup", restore the project from the Supabase dashboard.
2. Apply migrations in order (dashboard SQL editor, or `supabase db push`):
   `supabase/migrations/202607250001_create_thoughts.sql` (already applied on the existing
   project), then `supabase/migrations/202610040001_two_way_sync_and_learnings.sql`.
3. Set the Edge Function secret `GEMINI_API_KEY`, then deploy:
   `supabase functions deploy thought-machine`.
4. For a personal app, turn off "Confirm email" in Auth settings, or confirm the email once.

### Export to a folder for Claude (Mac)

```bash
HTT_EMAIL=you@example.com HTT_PASSWORD='…' node tools/export-thoughts.mjs ~/HoldThisThought
```

Then run `claude` inside `~/HoldThisThought`.

### Connect Claude live (MCP)

See [tools/mcp-server/README.md](../tools/mcp-server/README.md): `npm install`, `npm run login`,
then `claude mcp add --scope user hold-this-thought -- node <repo>/tools/mcp-server/server.mjs`.
