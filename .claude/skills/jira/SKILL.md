# Jira Skill — graphs-algos-lib

One script does everything: `.claude/tools/jira/jira.sh` (bash + curl + jq,
Jira Cloud REST v3). Auth comes from the `JIRA_BASE_URL`, `JIRA_EMAIL`,
`JIRA_API_TOKEN` environment variables (already set in `~/.bashrc`);
default project is `GAL` (override with `JIRA_PROJECT` or `--project`).

## Commands

```bash
J=.claude/tools/jira/jira.sh

# Create — Tasks link to their Epic and Sub-tasks to their parent Task
# through the same --parent flag (Jira Cloud parent field handles both)
$J create --type Epic --summary "..." --description "..." [--labels a,b]
$J create --type Task --summary "..." --parent GAL-1
$J create --type Subtask --summary "..." --parent GAL-3

# Read
$J view GAL-3
$J search --jql "project = GAL AND status != Done ORDER BY created DESC" [--max 50]

# Update
$J transition GAL-3 "In Progress"
$J transition GAL-3 Done --comment "Merged PR #1"
$J comment GAL-3 "Pushed to remote"
```

## Conventions

- This is a team-managed (next-gen) project; the sub-task issue type is named
  `Subtask` (no hyphen). Epic and Task are as usual.
- Multi-line descriptions: pass `\n`-separated text; each line becomes an ADF
  paragraph.
- Workflow statuses here: `To Do` → `In Progress` → `Done` (the script lists
  the valid transitions if you pass an unknown one).
- Move the issue to `In Progress` when starting work, `Done` with a comment
  referencing the PR/commit when finishing.
- Issue keys go into branch names and commit messages — see the Delivery
  section in CLAUDE.md.

## Troubleshooting

- `jq: command not found` → static binary lives in `~/.local/bin/jq`; ensure
  `~/.local/bin` is on PATH.
- 401/403 → token expired; ask the user for a fresh Jira API token.
- "no transition named [...]" → the script prints the available transitions;
  pick from those.
