---
name: interview-insights
description: Research what questions, tasks, and activities to expect in a job interview at a given company for a given position and, optionally, interview type (live coding, system design, behavioural, ...). Searches the internet for information from the last 3 years, adds the agent's own knowledge last, and reports it in three sections by trust level. Use when interview preparation insights are requested.
argument-hint: "<company> | <position> [| <interview type>]"
allowed-tools: WebSearch WebFetch
---

# Interview insights

Request: $ARGUMENTS

## 1. Understand the request

- **Company**: identify the company. When several companies share the name, pick the one that best fits
  the position and state the choice in the report. If no such company can be found, say so at the top of
  the report and fill only the third section, with what is typical for the position in general.
- **Position**: the role and its level if given (`Senior Backend Engineer`, `Data Analyst`). Research the
  closest matching role family when the exact title has no results, and say so.
- **Interview type**: when given, focus on that type (`live coding`, `system design`, `behavioural`,
  `take-home`, `HR screen`, ...). When not given, cover the whole process: every stage, its format, and
  what happens in it.

Take today's date from your environment. The cutoff is the same day 3 years earlier: material published
before it, or with no date you can establish, does not go into sections A or B.

## 2. Research

Search the internet first; use your own knowledge only after the search, and only for section C.

1. Read [the source guide](references/sources.md) completely: it says where to search and which section
   each kind of source belongs to.
2. Search for each kind of source in the guide, combining the company with the position, the interview
   type, and terms such as `interview questions`, `interview experience`, `interview process`, `onsite`,
   `coding round`, `system design round`. Search in the company's main local language too if it is not
   English.
3. Fetch the pages that look relevant and read them. Search snippets alone are not evidence: use a source
   only after reading it, and record its URL and publication date.
4. Keep only material dated on or after the cutoff. When a page has several dated entries (a forum thread,
   a list of reviews), use only the entries after the cutoff.
5. Stop when new sources only repeat what you already have, or after about 25 pages read.

## 3. Report

Write in English. Collect concrete questions, tasks, and activities, not general advice. Merge reports of
the same question or task and say how many independent sources mention it: repetition across candidates is
the strongest signal. Each bullet ends with its sources as `(<source name>, <YYYY-MM>, <URL>)`.

Use exactly this structure, with the three sections separated by a horizontal rule:

```markdown
# Interview insights: <company> — <position> — <interview type or "full process">

<one or two lines: assumptions made (which company, which role family), research cutoff date>

---

## A. Reports from candidates and company insiders (most trusted)

### Process
- <stages, their order, format, duration, who interviews>

### Questions and tasks
- <concrete question or task> — reported by <n> sources (<source>, <YYYY-MM>, <URL>); ...

---

## B. Information published by the company

- <what the company says about its process, values, preparation advice> (<source>, <YYYY-MM>, <URL>)

---

## C. Less trusted sources and the agent's own assumptions

### Less trusted sources
- <claim> (<source>, <YYYY-MM or "undated">, <URL>)

### Agent's own assumptions
- <what is likely, based on the agent's own knowledge of the company, the position, or similar companies>
  — not verified
```

When a section has nothing, write `No information found.` in it instead of filling it with material from
another section. Sections A and B only ever contain sourced material from after the cutoff.
