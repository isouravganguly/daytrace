# Hold This Thought — project context

Last updated: 2026-07-26

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

- Use Gemini for AI model calls for cost reasons.
- OpenAI SDK remains optional; consumer ChatGPT/OpenAI login/subscription should not be assumed usable for app API calls.
- Use Supabase Edge Functions to call Gemini so `GEMINI_API_KEY` stays server-side.
- Supabase anonymous auth is enabled for MVP testing.
- Use `Continue privately` as the simplest privacy-preserving login path for now.
- Keep personal/work theme visually consistent for MVP.
- After every Android rebuild, install the APK into the emulator.

## Current MVP behavior

- App gates cloud-enabled builds behind auth/private mode.
- User can continue privately using anonymous Supabase auth.
- User can type a thought and save it locally immediately.
- User can hold the speech CTA to capture voice through Android `SpeechRecognizer`.
- Local classifier assigns `TODO`, `NOTE`, or `THOUGHT`.
- WorkManager syncs pending local thoughts when connected.
- Supabase Edge Function enriches synced thoughts with Gemini classification and embeddings.
- Local lists hide completed items.
- The search screen currently uses local text matching in-app; semantic search exists on the Edge Function/database side but is not fully wired into the Android UI yet.

