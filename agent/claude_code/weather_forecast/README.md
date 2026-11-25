# weather_forecast agent

Finds tomorrow's weather forecast for a city or a country on the internet, built from `agent/claude_code/template`.
When the given location is not a real city or country, it answers that the region does not exist and gives
tomorrow's forecast for Tallinn instead.

```
weather_forecast/
├── agent.json                                 # agent-proxy run settings: model, effort, allowed tools, kick-off prompt, response schema
├── CLAUDE.md                                  # role, workflow, hard constraints
└── .claude/
    ├── settings.json                          # deny rules: shell, Edit and Write (allowed tools are in agent.json)
    └── skills/weather-forecast/
        └── SKILL.md                           # check the location → search and fetch → forecast
```

## Request

The start request needs a `STRING` item named `location` (`requiredItems` in `agent.json`):

```json
{"items": [{"type": "STRING", "name": "location", "value": "Tartu"}]}
```

No repository is needed. The agent only searches and fetches web pages; the shell, Edit and Write are disabled.

## Run by hand

```bash
cd agent/claude_code/weather_forecast
claude -p "/weather-forecast Tartu"
```

## Exposed as MCP tool

`exposeAsMcp` in `agent.json` describes the agent as the MCP tool `weather_forecast`: `tool` says when it is
worth calling, `requestSchema` is the JSON Schema of the start request's agent context for it: exactly one
`STRING` item `location`.

## Response

`agent.json` sets a `responseSchema`: the final answer is an agent context with exactly one `STRING` item named
`forecast`, holding tomorrow's forecast (or the region-not-exists notice followed by the Tallinn forecast).
