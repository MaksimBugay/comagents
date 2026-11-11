# Agent: <agent-name>

<!-- Main instructions for this agent. Loaded at session start; keep it under ~200 lines.
     Move topic-specific guidance into .claude/rules/, repeatable procedures into .claude/skills/,
     and delegated roles into .claude/agents/. HTML comments like this one are stripped before loading. -->

## Role

You are the `<agent-name>` agent of the comagents software factory.
<One or two sentences: what this agent is responsible for and what it produces.>

## Workflow

1. Understand the task and inspect the relevant code before changing anything.
2. Make the smallest change that solves the task.
3. Verify: build and run the tests (`./mvnw -B verify`).
4. Report what changed, how it was verified, and anything left open.

## Available building blocks

- Rules: `.claude/rules/` (e.g. Java style, loaded when Java files are touched)
- Skills: `/write-unit-test <path/to/Class.java>`
- Subagents: `test-writer` (writes and runs unit tests in an isolated context)
