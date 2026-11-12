# Agent notes

See README.md for usage, config, and layout.

## Keybase API

- Every chat `read` must pass `"peek": true`. Without it, a backup run marks
  messages as read in the user's Keybase.
- `keybase chat api` exits 0 even when a request fails; the failure is in the
  reply's `error` field. Always call it through `kbb.keybase/chat-api`, which
  throws on errors.
- Treat Keybase as read-only. Never call methods that send, edit, delete,
  react, mark, join, or leave.
- Keybase never returns deleted messages. The backup is the only copy of a
  message that was deleted after it was backed up.

## Data

- `backup/` and `backup.log` contain real private chats and files. When
  debugging, print counts, keys, and message types. Do not print message
  bodies or file contents.
- `messages.jsonl` is append-only and is where the next run resumes. Never
  rewrite or reorder its existing lines. New lines are written only after all
  fetching is done.
- To experiment, run with `--target` pointed at a scratch directory, or use
  `--dry-run`.

## Commands

- `bb test`: unit tests.
- `bb backup --dry-run`: safe smoke test. It lists candidates and writes
  nothing.
