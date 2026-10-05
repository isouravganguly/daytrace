# Hold This Thought — MCP server

Gives Claude (Claude Code or Claude Desktop) live access to your thought collection:

| Tool | Does |
| --- | --- |
| `search_thoughts` | Search by meaning (embeddings) and by exact words, merged. Optional `kind`. |
| `recent_thoughts` | What you captured in the last N days; `kind: LEARNING` for "what did I learn". |
| `list_todos` | Open todos (or all with `include_done`). |
| `add_thought` | Save something you ask Claude to remember. It syncs to your phone. |
| `complete_todo` | Mark a todo done or reopen it. |

It runs on your Mac, talks only to your own Supabase project, and sees only your rows (Row Level Security).

## Setup

Needs Node 20+, a restored Supabase project with the v2 migration and the `thought-machine` function
deployed, and an **email** account. If the app shows "Private session", tap "Secure my account" first.

```bash
cd tools/mcp-server
npm install
npm run login
```

`login` asks for your email and password once, then stores only the session tokens in
`~/.config/hold-this-thought/session.json` (mode 600). It never stores the password. Run it again
if you change your password or the session expires after a long time unused.

Supabase URL and key come from the repo's `local.properties`, or from the `SUPABASE_URL` and
`SUPABASE_PUBLISHABLE_KEY` environment variables.

### Claude Code

```bash
claude mcp add --scope user hold-this-thought -- node /path/to/HoldThisThought/tools/mcp-server/server.mjs
```

`--scope user` makes it available in every project, so Claude can check your learnings while you work.

### Claude Desktop

Add this to `~/Library/Application Support/Claude/claude_desktop_config.json`, then restart Claude Desktop.
Desktop apps don't see your shell's PATH, so use the absolute path from `which node`:

```json
{
  "mcpServers": {
    "hold-this-thought": {
      "command": "/path/to/node",
      "args": ["/path/to/HoldThisThought/tools/mcp-server/server.mjs"]
    }
  }
}
```

## Try it

- "What have I learned about Kotlin coroutines?"
- "Summarise my learnings from this week and point out anything I learned twice."
- "Remember this as a learning: Gradle's config cache breaks with tasks that read project at execution time."
- "What's on my todo list for personal?" / "Mark the drawer one done."

## Troubleshooting

- **"Not logged in"**: run `npm run login`.
- **"Supabase auth failed (400)"** on use: the stored session was revoked. Run `npm run login` again.
- **Search finds only exact words**: the `thought-machine` function isn't deployed or Gemini failed.
  Literal matching still works.
- Logs go to stderr. In Claude Desktop they're in `~/Library/Logs/Claude/mcp-server-hold-this-thought.log`.
