# AlgoCraft repository instructions

Use `./mod-build.ps1 build` for the full build and `./mod-build.ps1 gradle <tasks>`
for targeted checks. The project is self-contained; no internal tools,
private repositories or publishing credentials are needed to build it.

All automated tests must run silently in the background without visible
windows, focus changes, hardware mouse capture/warping or clipboard writes.
Use the built-in hidden, input-isolated, muted client adapters. Disable early
splash windows before launch. Never fall back to a visible test.

Native tests acquire a lock on the declared `algocraftTestRuntimeDirectory`
and inspect live processes before launch. Preserve and restore pre-existing
test configuration. Bound only the test processes. Require the desktop-isolation
and clean-exit receipts; compilation never substitutes for gameplay evidence.

Never add `Co-Authored-By` lines to commits.

Before committing or publishing, run `python scripts/check_public_tree.py`.
Internal review, audit, handoff and acceptance reports belong in ignored build
output or an external private evidence directory. Never commit test worlds,
caches, logs, backups, temporary files or report artifacts. Public documentation
covers players, contributors, schemas, licenses and release download links.

Record player-facing fixes in focused GitHub issues with affected Minecraft/loader
versions, reproduction steps, fix commits and the scope of actual validation.
Include a short demonstration video when it clarifies the behavior, and close
only when that issue's stated acceptance checks pass. Keep videos and raw test
logs in issue attachments or release assets rather than the source tree.
