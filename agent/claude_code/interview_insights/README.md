# interview_insights agent

Researches what questions, tasks, and activities to expect in a job interview at a company for a position,
built from `agent/claude_code/template`. It searches the internet for material from the last 3 years first
and adds its own knowledge last, and reports in three sections by trust level:

- **A.** reports from candidates and company insiders (most trusted)
- **B.** information published by the company
- **C.** less trusted sources and the agent's own assumptions

```
interview_insights/
├── agent.json                                 # agent-proxy run settings: model, effort, allowed tools, kick-off prompt, response schema
├── CLAUDE.md                                  # role, workflow, hard constraints
└── .claude/
    ├── settings.json                          # deny rules: shell, Edit and Write (allowed tools are in agent.json)
    └── skills/interview-insights/
        ├── SKILL.md                           # understand the request → research → three-section report
        └── references/sources.md              # where to search and which section each kind of source belongs to
```

## Request

The start request needs `STRING` items named `company` and `position` (`requiredItems` in `agent.json`).
`interviewType` is optional; without it the agent covers the whole interview process:

```json
{"items": [
  {"type": "STRING", "name": "company", "value": "Wise"},
  {"type": "STRING", "name": "position", "value": "Senior Java Engineer"},
  {"type": "STRING", "name": "interviewType", "value": "system design"}
]}
```

No repository is needed. The agent only searches and fetches web pages; the shell, Edit and Write are disabled.

## Run by hand

```bash
cd agent/claude_code/interview_insights
claude -p "/interview-insights Wise | Senior Java Engineer | system design"
```

## Response

`agent.json` sets a `responseSchema`: the final answer is an agent context with exactly one `STRING` item named
`insights`, holding the report as free text in the three sections above.
