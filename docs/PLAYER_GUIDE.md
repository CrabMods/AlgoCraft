# AlgoCraft player guide

## Your first computer

Choose **Minecraft 1.20.1 + Forge 47.3.0 + Java 17**, or **Minecraft 1.21.1 + NeoForge 21.1.216 + Java 21**. Put the matching AlgoCraft JAR in `mods/`. The server and every joining client must use matching Minecraft, loader and AlgoCraft builds. Singleplayer uses its built-in server. Both versions include the same 500-problem bank and rewards.

Craft an Algorithm Computer with **6 iron ingots, 2 redstone dust and 1 glass block**:

| Iron ingot | Iron ingot | Iron ingot |
| --- | --- | --- |
| Redstone dust | Glass | Redstone dust |
| Iron ingot | Iron ingot | Iron ingot |

Picking up redstone unlocks the recipe-book entry. Place the computer and right-click it. Use a **stone pickaxe or better** to pick it up again.

## Read, write, run, submit

Start with **Two Sum**, **Contains Duplicate**, or **Valid Anagram**. Read the examples and the required Java method signature, then replace the starter method body with your solution. Some familiarity with Java methods, loops and arrays helps.

**Run** checks the visible examples. **Submit** checks the public problem on the Minecraft server, including hidden tests. Compiler errors and failed results appear in the terminal. Your answer counts toward rewards when the server accepts it. Hidden test inputs and expected outputs are not disclosed for public submissions.

Search by title or ID. **View** switches the statement/editor layout; **History** keeps recent submissions. Switching problems keeps a separate draft for each problem, and closing the IDE keeps your drafts.

Use **Web** for the companion browser IDE on your own computer. It uses the server's published catalog and submits through your connected Minecraft player. Browser drafts are separate from in-game drafts. The browser editor's Monaco/CSS assets require internet access; the bundled in-game bank can be used offline.

## Rewards and daily practice

With rewards enabled, your **first accepted clear** gives survival items and XP. The reward depends on difficulty and your survival stage. Inventory overflow drops at your feet; chat reports what you received.

You can clear several new problems on the same day. A previously solved problem can give a smaller review reward once per **server calendar day**. Repeating it again that day does not grant another reward. From day 3, your first rewarded solve each day adds XP, emeralds and experience bottles. Weekly bundles arrive on day 7 (8+ diamonds, 16 gold ingots, 16 experience bottles), day 14 (2 netherite ingots, 8 diamonds, 4 golden apples), day 21 (3 netherite ingots, a totem, 16 experience bottles), and day 28 onward (4 netherite ingots, an enchanted golden apple, 24 experience bottles every 7 days). These bundles are granted only once on that server day; missing a day resets the streak to 1.

Unique-clear item milestones are **10, 25, 50, 100, 200 and 500**. The 500-clear milestone gives a **dragon egg**. Trophy achievements have their own thresholds, including first clear, problem-count milestones, streaks and difficulty achievements.

Trophies are delivered automatically when inventory space is available. Open your inventory (default key **E**) and look for the achievement name, such as **First Steps**. If your inventory is full, the server keeps the trophy in a persistent pending list instead of dropping it. Free slots and click the chat claim link, or use `/algocraft trophy claim`. `/algocraft trophy list` shows pending awards. These records survive normal server saves and restarts; claiming preserves the owner and original award date and does not duplicate a delivered award.

Minecraft's **Advancements** screen (default key **L**) has an **AlgoCraft** tree with the same 23 achievements. Holding the Algorithm Computer opens the tab; previously earned achievements sync when you join. Opening the tree does not issue another trophy.

For trophies lost by the old full-inventory behavior, an administrator can use `/algocraft trophy recover <player>`. It restores only saved earned achievements that have no delivery receipt and can recover each legacy award once. Offline players receive a pending record and can claim when they return. Use `/algocraft trophy status <player>` to audit saved solve counts, earned achievements, and pending trophies. Recovery uses the time of recovery because the old save did not retain the original trophy's date.

There are five trophy materials: **bronze, silver, gold, diamond and netherite**. Each earned trophy records your player name, award date and achievement. Place trophies on blocks to display them in your base, or use vanilla item frames. Breaking a placed trophy returns the same award, with its owner, date and achievement preserved. Clearing every problem in an enabled server bank awards the **netherite Completionist trophy**; the official bank has 500 problems.

An administrator can set `enableRewards=false`: accepted progress still saves, but items, XP and trophies are disabled.

## Multiplayer and question banks

The **server** installs and publishes public banks, judges submissions, stores each player's progress and awards rewards. Players joining the world automatically receive the public statements, translations and images. Progress belongs to the world and your player UUID and is shared across that world's dimensions.

New worlds enable the bundled `official` bank. Only the server console or an operator with permission level 2 can install, update, enable or disable additional public banks. Operators can click **Bank update guide** in the IDE to see the chat commands. The guide does not start a download. Use `/algocraft bank list`, `install <name> <manifest-url>`, `enable <id>`, `disable <id>`, `reload`, or `update-official`.

Personal imports are private practice. Importing a file or repository does not publish it to the server and does not grant server items. Place personal JSON files in `<gameDir>/algorithm_challenges/user/` or use **Import** in the IDE. Start with the [official-format guide](PROBLEM_FORMAT.md) and [working JSON example](examples/p9001.json).

## Development

See [developing](DEVELOPING.md) for the self-contained build and hidden test entrypoints.
