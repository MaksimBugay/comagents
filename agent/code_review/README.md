# code_review agent

Read-only Java 25 / Spring Boot code reviewer, built from `agent/template/claude_code`.

```
code_review/
├── CLAUDE.md                                  # role, workflow, hard constraints
└── .claude/
    ├── settings.json                          # read-only git + build allowed; Edit/Write and mutating git denied
    ├── rules/
    │   ├── read-only.md                       # never change the code
    │   └── java-spring-boot.md                # the standard code is reviewed against
    └── skills/java-code-review/
        ├── SKILL.md                           # scope → prepare → verify → severity-ordered report
        └── references/review-checklist.md     # what to check and the coverage/conclusion rules
```

## Run

Run it from the repository to review, so git and the build see that project, and point Claude Code
at this agent's configuration. Each piece of an `--add-dir` folder loads differently:

- skills: loaded automatically
- `CLAUDE.md` and rules: only with `CLAUDE_CODE_ADDITIONAL_DIRECTORIES_CLAUDE_MD=1`
  (path-scoped rules are never loaded this way, which is why both rules here are unscoped)
- `settings.json`: not loaded; pass it with `--settings`

```bash
AGENT=/path/to/comagents/agent/code_review
cd /path/to/repo-under-review
CLAUDE_CODE_ADDITIONAL_DIRECTORIES_CLAUDE_MD=1 claude \
       --add-dir "$AGENT" --settings "$AGENT/.claude/settings.json" \
       -p "/java-code-review"                       # current branch vs main
#      -p "/java-code-review src/main/java/com/acme/OrderService.java"
#      -p "/java-code-review main..feature/payments"
```

Or start `claude` inside this folder and give it the repository path as the target.

No MCP servers are configured yet.
