# keybase-backup

Back up Keybase chats (with attachments), KBFS files, and Keybase-hosted git
repos into a single directory. Runs are incremental: each run picks up where
the last one left off.

The tool keeps one live copy and doesn't take snapshots. If you need history,
back up the target directory with your usual nightly backup.

## Requirements

- [mise](https://mise.jdx.dev/), which installs babashka from `mise.toml`
- `keybase`, logged in and running. KBFS must be mounted for the files section.
- `rsync` for files, `git` for repos

```sh
mise install
```

## Usage

```sh
bb backup                  # run every section with a non-empty :include
bb backup chat git         # run only these sections
bb backup --dry-run        # list candidates: + included, - excluded
bb backup --target DIR     # override the configured target
bb backup --config FILE    # use a different config file
bb test                    # run unit tests
```

The command exits non-zero if any item fails. Other items still run.

To run from cron:

```sh
cd ~/src/keybase-backup && mise exec -- bb backup
```

## Configuration

`~/.config/keybase-backup/config.edn`:

```clojure
{:target "/home/me/src/keybase-backup/backup"
 :chat  {:include ["**"] :exclude ["team/bigteam/**"] :attachments true}
 :files {:include ["private/me" "team/acme"] :exclude []}
 :git   {:include ["**"] :exclude []}}
```

Each section has `:include` and `:exclude` lists. Their globs are matched
against each item's path relative to its section directory, so the patterns
mirror the backup layout:

| Section | Example paths                                  |
|---------|------------------------------------------------|
| chat    | `dm/alice,me`, `team/acme/general`             |
| files   | `private/me`, `public/me`, `team/acme`         |
| git     | `personal/dotfiles`, `team/acme/infra`         |

An item is backed up if it matches some `:include` glob and no `:exclude` glob.
Globs follow `java.nio` syntax: `*` stays within one path segment and `**`
crosses segments. Run `bb backup --dry-run` to check your patterns.

Defaults:
- chat: `["**"]` (everything)
- git: `["**"]` (everything)
- files: `[]` (nothing). KBFS can be large, so you opt in to each folder.

### Finding what isn't backed up

Anyone on Keybase can start a chat with you or create a private folder that
includes you (e.g. `private/you,stranger`), without your approval. The folder
then appears on the KBFS mount. Broad globs like `private/*` or `**` would back
it up automatically. That's why the files section is opt-in and is best kept
as an explicit list.

To see what's out there, run a dry run:

```sh
bb backup --dry-run          # all sections
bb backup files --dry-run    # just files
```

Lines starting with `-` are candidates that are not backed up:

```
+ files private/me
- files private/me,newfriend
- files team/othercorp
```

To back one up, copy its path (the part after the section name) into that
section's `:include` in the config. Run the dry run again to confirm it now
starts with `+`. The next `bb backup` will fetch it in full. Add a path to
`:exclude` instead to keep a broad include pattern from matching it.

## Layout

```
<target>/
  chat/
    dm/<members>/                 DMs and group chats
    team/<team>/<channel>/
      conv.json                   conversation id and channel
      messages.jsonl              raw API messages, ascending id
      transcript.md               readable rendering
      attachments/<msgid>-<filename>
  files/<private|public|team>/<folder>/...
  git/<personal|team/<team>>/<repo>.git     bare mirrors
```

## How it works

**Chat.**
- **Source of truth:** `messages.jsonl`. Each run reads the highest message id
  in it, pages backward through the conversation until it reaches that id, and
  appends the new messages. Nothing is written until fetching finishes.
- **Read state:** reads use `peek`, so backing up never marks messages as read.
- **Edits, deletes and reactions:** these arrive as separate messages and are
  kept in the log. `transcript.md` is regenerated from the log with them applied.
- **Deleted messages:** Keybase never returns them. Only messages that were
  backed up before being deleted are kept. They show as `_[deleted]_` in the
  transcript, and their original text stays in `messages.jsonl`. Back up often
  to keep more of them.
- **Renames:** a conversation is identified by the id in `conv.json`. If a
  channel is renamed, its directory moves to the new name.
- **Failed attachments:** if an attachment download fails, a
  `<file>.failed` marker holding the error is written, so the download isn't
  retried every run. Delete the marker to retry. An interrupted run can leave
  these markers too.

**Files.** Each selected KBFS folder is copied from the mount with `rsync -a`.
Files deleted in KBFS are not deleted from the backup.

**Git.** Each repo gets a `git clone --mirror` the first time and
`git remote update --prune` after that.
