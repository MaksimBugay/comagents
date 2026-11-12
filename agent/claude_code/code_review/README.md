# code_review agent

Read-only Java 25 / Spring Boot code reviewer, built from `agent/claude_code/template`.

```
code_review/
├── CLAUDE.md                                  # role, workflow, hard constraints
└── .claude/
    ├── settings.json                          # static review: shell (Bash), Edit and Write denied
    ├── rules/
    │   ├── read-only.md                       # never change the code
    │   └── java-spring-boot.md                # the standard code is reviewed against
    └── skills/java-code-review/
        ├── SKILL.md                           # scope → prepare → severity-ordered report
        └── references/review-checklist.md     # what to check and the coverage/conclusion rules
```

## Run

The agent does not need git: the project under review can be a plain snapshot of the sources.

agent-proxy copies `CLAUDE.md` and `.claude/` into the root of the project to review, so Claude Code
loads the role, settings, rules, and skills automatically, and puts this README in
`/tmp/<session id>/code_review/`. Then run Claude Code from the project root:

```bash
cd /tmp/<session id>/<repo name>
claude -p "/java-code-review"                                       # whole project
#      -p "/java-code-review src/main/java/com/acme/OrderService.java"  # files or folders
#      -p "/java-code-review /path/to/changes.patch"                  # a patch file
```

To run it by hand, copy `CLAUDE.md` and `.claude/` into the project root the same way.

No MCP servers are configured yet.
