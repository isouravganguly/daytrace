# Hold This Thought — roadmap

Last updated: 2026-10-04

## Done in v0.2.0

- Local-first architecture: Room as source of truth, no auth gate before capture.
- Two-way sync (push/enrich/pull) with soft deletes and safe conflict handling.
- Kinds are now Thought / Learning / Todo (Note → Learning), with an offline guess and AI refinement.
- AI title + topic tags via Gemini; the user's text is never rewritten; user-picked kinds are locked.
- New UI: bottom navigation; calm capture canvas; hold-to-talk or tap-for-hands-free voice with
  live transcript and waveform; Library with day grouping, filters and search; edit sheet with
  done/copy/delete and undo.
- Semantic "related by meaning" search in Library (needs the v2 migration + function deploy).
- Export to Markdown / JSON on the phone; `tools/export-thoughts.mjs` exports to a folder for Claude.
- Light and dark themes, app icon, unit tests for classifier and exporter.

## Next, in order of value

1. **Restore and upgrade the cloud.** Restore the paused Supabase project, apply the v2
   migration, deploy `thought-machine`, secure the account with an email.
2. **MCP server for Claude.** A small local MCP server exposing `search_thoughts`,
   `recent_learnings` and `add_thought`, so Claude Desktop / Claude Code can query and add to
   the collection live instead of reading an export.
3. **Weekly digest.** A scheduled job (Edge Function cron or Claude routine) that summarises the
   week's learnings and surfaces things learned more than once.
4. **Spaced resurfacing of learnings.** A quiet "remember this?" card on the Library screen.
5. **Quick capture everywhere.** Home-screen widget, Quick Settings tile, share-to-app target.
6. **Editable tags** and a tag browser.
7. **Reliability.** Instrumented Room migration test, sync engine tests with a fake remote.

## Product risks to avoid

- Making capture slower to make AI smarter.
- Showing sync failures anywhere but Settings.
- Turning the todo list into a task manager.
- Making the user classify every thought.
- Shipping provider API keys in the APK.
