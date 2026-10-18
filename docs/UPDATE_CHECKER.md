# Update notifications

AlgoCraft uses the loader update checker to show a notification in the Minecraft Mods screen when a newer release is available. The checker only reads the public JSON file; it never downloads or installs a mod automatically.

The stable update feed is:

`https://raw.githubusercontent.com/CrabMods/AlgoCraft/1.21.1/update.json`

It tracks both supported game versions:

- `1.21.1` / NeoForge
- `1.20.1` / Forge

The `recommended` entries identify the release suitable for normal players. The `latest` entries may point to a newer beta. Release files remain on the repository release page and the CurseForge/Modrinth project pages.
