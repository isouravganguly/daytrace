# Hold This Thought — project context

Last updated: 2026-10-04

## One-line idea

Hold This Thought is a personal thought machine: open the app, speak or type what is on your mind, and trust that it is saved, classified, synced, and retrievable later.

## Why this exists

The user wants a lightweight personal app for capturing thoughts, learnings, reminders, and todos without the friction of choosing a folder, formatting a note, or managing task failure states. The app should feel like a private mental inbox that can later help the user remember, search, reflect, and improve.

The first priority is capture reliability. AI and cloud sync are helpful enrichment layers, but they must never block saving a thought.

## Original MVP requirements

- Android app.
- First screen should be simple: one text box and a clear hold/tap-to-speak CTA.
- Capture via typing and speech.
- Store spoken text as simple text.
- Support two spaces:
  - Work.
  - Personal.
- Personal and Work originally had different visual directions, but the current product decision is to keep both in the same clean, modern work-style color system for MVP.
- Support drawer/menu sections:
  - Capture.
  - Thoughts.
  - Notes.
  - Todos.
  - Search.
- While speaking, the user may say whether the item is a note or todo.
- If not explicit, the app should infer whether the capture is a thought, note, or todo.
- Search should retrieve notes/thoughts using remembered context, not just exact words.
- Use Supabase for cloud persistence.
- Handle no internet:
  - Save locally first.
  - Sync when connected.
  - Do not show failed/undone sync tasks on the base capture screen.
- AI can be used to read the user’s thoughts/learnings and help remind or improve them later.

## Product principles

1. Capture must feel instant.
   - Never wait for network or AI before saving.
   - Local Room database is the source of truth on device.

2. The base screen must stay calm.
   - No failed sync clutter.
   - No task-management anxiety on open.
   - Capture screen is for capturing, not debugging.

3. AI is enrichment, not the source of truth.
   - The original transcript/body must be preserved.
   - AI can classify, tag, embed, summarize, and suggest later actions.

4. Privacy should be simple and explainable.
   - For MVP testing, anonymous Supabase auth is acceptable.
   - For longer-term use, modular auth should support email/password, magic link, or passkeys.
   - API keys must not ship in the APK.

5. Search is the “memory” feature.
   - Literal local search is useful immediately.
   - Semantic search via embeddings is the long-term differentiator.

## Current user/product decisions

- Personal, single-user app. Not meant to be shared or published.
- The core value is **learnings**: things the user learned and doesn't want to relearn.
  Kinds are Thought / Learning / Todo (the old "Note" became Learning).
- The collection must be exportable and usable by Claude (Markdown export, a folder export
  script, and later an MCP server).
- Use Gemini through a Supabase Edge Function for AI (cost). Keys never ship in the APK.
- No sign-in before capture. One email account for cloud backup. Anonymous sessions from v0.1
  can be upgraded in place.
- Work and Personal share one calm visual system, with light and dark themes.
- After every Android rebuild, install the APK on the emulator.

## Current behaviour (v0.2.0)

See `ARCHITECTURE.md` for detail. In short: instant local capture by text or voice
(hold-to-talk or hands-free dictation), auto-classification, a Library with search and filters,
editing and deletion with undo, two-way cloud sync with AI titles/tags, semantic search, and
Markdown/JSON export.
