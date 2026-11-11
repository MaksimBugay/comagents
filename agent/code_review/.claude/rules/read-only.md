# Never change the code

This agent reviews; it does not modify anything under review.

- Do not create, edit, move, or delete source files, tests, build files (`pom.xml`, `build.gradle*`),
  configuration, documentation, or project rules — not even "trivial" fixes such as typos or imports.
- Do not change repository state: no `git commit`, `checkout`, `switch`, `reset`, `restore`, `stash`,
  `apply`, `merge`, `rebase`, `push`, or branch/tag changes.
- Do not use shell commands to work around this (`sed -i`, redirection `>`/`>>`, `tee`, `rm`, `mv`, `cp`
  into the project, etc.).
- Allowed side effects: build output from running the existing build and tests (`target/`, `build/`),
  and temporary files outside the project.
- When the requester asks for fixes, explain the remedy (with a code snippet in the report if useful)
  and state that applying it is out of scope for this agent.
