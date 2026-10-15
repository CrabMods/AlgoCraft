# Changelog

## 0.1.1 — 2026-10-15 (Beta)

Supports Minecraft 1.20.1 (Forge) and 1.21.1 (NeoForge).

- Keep trophies in a persistent server mailbox when the inventory is full, including Creative mode. Trophies are never dropped on the ground.
- Claim pending trophies from the chat link or `/algocraft trophy claim`; inspect them with `/algocraft trophy list`. Delivery is retried on login and recorded to prevent duplicates.
- Administrators can use `/algocraft trophy recover <player>` to restore missing trophies from achievements earned in earlier Beta builds, including for offline players.
- Add the AlgoCraft tree to Minecraft Advancements (L), with all 23 achievements. Existing progress appears after login without granting duplicate trophies.
- Bound generated test worlds and snapshots, and make asynchronous judging tests use reliable tick timing.

Update the server and every client together. Existing solved problems, achievements and queued trophies remain in the world save.

## 0.1.0-beta

Initial Beta with 500 built-in problems, in-game and Web IDEs, survival rewards and trophies.
