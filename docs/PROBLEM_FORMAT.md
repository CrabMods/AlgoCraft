# Import your own problems

Use the same JSON format as AlgoCraft's built-in bank. Start with the
[downloadable example](examples/p9001.json), or browse a complete
[official problem](../question_bank/official/p1.json) in our
[GitHub repository](https://github.com/CrabMods/AlgoCraft).
Both Minecraft versions use this format.

## One problem for personal practice

1. Download `p9001.json` with GitHub's **Raw / Download** button.
2. Edit the statement, starter method, examples and tests. Keep the filename
   `p<number>.json` and make `id` the same number as a string.
3. At your Algorithm Computer, choose **Import → Local file**, enter the
   full path to the JSON file, and press **Import**.
4. Select the new problem under the personal bank. These imports stay on your
   computer and do not grant server loot, progress or trophies.

Save as UTF-8 JSON. Escape newlines inside strings as `\n`.

| Field | Format |
| --- | --- |
| `id` | Nonempty string, e.g. `"9001"` |
| `title` | Short, clear problem name |
| `description` | Markdown explaining input, return value, examples and constraints |
| `difficulty` | `"EASY"`, `"MEDIUM"` or `"HARD"` |
| `initialCode` | Java class and public method for the player to complete |
| `tests` | Nonempty array of `{ "input": "...", "output": "..." }` |
| `examples` | Optional visible cases in the same format; **Run** uses these |
| `tags` | Optional array of topic strings |
| `solutions` | Optional array with `name`, `description`, `code`, `language: "java"` |

Inputs and outputs are **strings**, even when they represent numbers or arrays.
For the sample method `sum(int[] nums)`, an input is `"nums = [2, 3, 5]"`
and the expected output is `"10"`. **Submit** checks every entry in `tests`.
Use one public solution method per ordinary `Solution` class; use an existing
official design problem as the reference for constructor/operation sequences.

Use unambiguous expected answers and add empty, minimum, negative and large
cases where the statement allows them. Verify your reference solution before
sharing a problem.

## Images and translations

For a local bank, place PNGs in `algorithm_challenges/user/images/` and reference
them with `diagrams: [{"id":"example-1","file":"example.png","caption":"What this diagram shows"}]`.
Importing a single JSON file copies only that file; copy its PNGs separately.
Do not use the legacy `images` or `diagram` fields.
Optional translations live in `lang/zh_cn/p<number>.json`; follow the
[official translation](../question_bank/official/lang/zh_cn/p1.json).

## A remote bank for a server or class

The **Remote bank** input accepts a manifest URL (or its containing directory),
not an HTML GitHub repository page. The current downloader requires:

- `catalog.json` and consecutively numbered `p1.json` through `pN.json`;
- a matching `lang/zh_cn/p<number>.json` for every problem;
- every referenced PNG under `images/`;
- `manifest.json` with `version`, `totalProblems`, ordered `files` entries
  (`name`, SHA-256 `hash`, byte `size`) and `signature` (SHA-256 of the
  concatenated file hashes, in manifest order).

Keep the official catalog/problem/image/translation order. Use the
[official manifest generator](../question_bank/official/generate_manifest.py)
in your bank folder; set its `VERSION`, validate, then generate the manifest.
JSON hashes use UTF-8 with LF line endings; PNG hashes use the exact bytes.
Serve the files together over HTTPS. Review
[the official bank structure](../question_bank/official) as a complete reference.

Remote import from the player UI is still personal practice. To publish a bank
for everybody, an operator uses `/algocraft bank install <name> <manifest-url>`.
See the [server guide](PLAYER_GUIDE.md). A manifest checksum verifies integrity;
only install banks from authors you trust.
