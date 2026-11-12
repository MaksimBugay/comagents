# Claude Code agent template

Starting point for a comagents agent implemented with [Claude Code](https://code.claude.com/docs).
Copy it to `agent/claude_code/<agent-name>/` and replace the example content.

```bash
cp -R agent/claude_code/template agent/claude_code/<agent-name>
```

## Layout

```
template/
├── agent.json                    # agent-proxy run settings: model, effort, allowed tools, kick-off prompt (required)
├── README.md                     # what the agent does (required)
├── CLAUDE.md                     # agent role + workflow, always loaded
├── .mcp.json                     # MCP servers available to the agent (none by default)
├── .gitignore                    # keeps personal overrides out of git
└── .claude/
    ├── settings.json             # deny rules, hooks, env; allow rules go in agent.json (see below)
    ├── rules/                    # topic rules; `paths:` frontmatter = load only for matching files
    │   └── java-style.md
    ├── skills/                   # reusable procedures, invoked as /<name> or picked by the model
    │   └── write-unit-test/
    │       ├── SKILL.md          # frontmatter + instructions ($ARGUMENTS = invocation args)
    │       └── examples.md       # supporting file, read on demand
    └── agents/                   # subagents: own context, tools, model; delegated to by description
        └── test-writer.md
```

| Piece     | Use it for                                    | Loaded                                        |
|-----------|-----------------------------------------------|-----------------------------------------------|
| CLAUDE.md | who the agent is, its workflow                | every session                                 |
| rules     | conventions for a topic or file type          | at start, or when a `paths` file is touched   |
| skills    | step-by-step procedures, with optional files  | on `/name` or when the description matches    |
| agents    | delegated roles with isolated context         | when delegated to (or `claude --agent <name>`)|

The example pieces are connected: `test-writer` preloads the `write-unit-test` skill,
and both pick up `java-style` when they edit `.java` files.

## agent.json

How agent-proxy runs the agent headless (`claude -p`):

| Field | Passed as | |
|---|---|---|
| `model` | `--model` | alias (`opus`, `sonnet`) or full model name; optional |
| `effort` | `--effort` | `low`, `medium`, `high`, `xhigh` or `max`; optional |
| `allowedTools` | `--allowedTools` | tools the agent may use without approval, in permission-rule syntax |
| `kickOffPrompt` | the prompt | the first instruction of every session; required |
| `resumePrompt` | the prompt on resume | the instruction when an archived session is resumed; optional, a generic "continue where you stopped" by default |

Allow rules go here, not in `.claude/settings.json`: a headless run never trusts the folder it starts in, so
Claude Code ignores a project's `allow` rules (and warns about it). Deny rules stay in `settings.json`, which
Claude Code always applies.

## Run

```bash
cd agent/claude_code/<agent-name>
claude                                    # interactive
claude -p "/write-unit-test src/main/java/com/comagents/agentproxy/AgentController.java"
claude --agent test-writer -p "cover AgentController"   # run a subagent as the main agent
```

## Adding more

- Rule: `.claude/rules/<topic>.md`, optional `paths:` list of globs.
- Skill: `.claude/skills/<name>/SKILL.md` with `name`, `description` (+ optional `allowed-tools`, `argument-hint`, `disable-model-invocation`).
- Subagent: `.claude/agents/<name>.md` with `name`, `description` (+ optional `tools`, `model`, `skills`).
- Personal overrides: `CLAUDE.local.md`, `.claude/settings.local.json` (git-ignored).
