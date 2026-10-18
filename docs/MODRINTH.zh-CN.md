![AlgoCraft：在 Minecraft 里学算法](https://files.seeusercontent.com/2026/10/02/Ba0i/hero.jpg)

# AlgoCraft

**在 Minecraft 里学算法。** 合成一台算法电脑，在你的基地旁写真正的 Java，挑战 500 道内置题目。通过的答案会变成生存物资、每日连胜奖励，以及可以挂在墙上的奖杯。

`Forge 1.20.1` · `NeoForge 1.21.1` · `500 道题` · `English & 简体中文`

**下载：** [CurseForge](https://www.curseforge.com/minecraft/mc-mods/algocraft) · [Modrinth](https://modrinth.com/mod/algocraft) · [GitHub](https://github.com/CrabMods/AlgoCraft)

**当前版本：0.1.1 Beta**（`0.1.1`）。这是用于收集玩家对学习流程和生存奖励平衡反馈的早期测试版本。

<iframe src="https://www.youtube.com/embed/DsKH72bj73o?rel=0" width="100%" height="420" frameborder="0" allowfullscreen></iframe>

[在 YouTube 上观看](https://www.youtube.com/watch?v=DsKH72bj73o)

## 五分钟上手

1. **合成算法电脑**：6 个铁锭、2 个红石粉、1 个玻璃。捡起红石后配方书会解锁这个配方。
2. **放下并右键。** IDE 打开后，左边是题目列表，中间是题面，右边是你的代码。
3. **先挑一道简单题**，比如 *Two Sum*、*Contains Duplicate* 或 *Valid Anagram*。每道题都带有起始代码和需要完成的方法。
4. 先 **Run** 检查可见的示例，有把握后再 **Submit**。
5. **查看物品栏中的奖励**：奖励和奖杯会自动发放，无需点击领取。物品栏满时，奖杯会保存在服务器；腾出空位后点击聊天领取链接，或使用 `/algocraft trophy claim`。明天再来，保持连续解题。

| | | |
| --- | --- | --- |
| 铁锭 | 铁锭 | 铁锭 |
| 红石粉 | 玻璃 | 红石粉 |
| 铁锭 | 铁锭 | 铁锭 |

![算法电脑与合成配方](https://files.seeusercontent.com/2026/10/02/mfN2/feature-computer.jpg)

会一点 Java 会更顺手：能看懂方法、循环和数组就够了。从 Easy 题开始，慢慢往上走。

## 两种写代码的方式

**游戏内 IDE。** 一切都在电脑前完成：搜索题目、阅读题面和配图、带语法高亮地编辑代码，在终端里看到编译错误和运行结果。草稿按题目分别保存，可以随时切换再回来。

![AlgoCraft 游戏内 IDE 的真实截图](https://files.seeusercontent.com/2026/10/02/5cKy/ingame-ide.jpg)

**Web IDE。** 点 **Web** 在浏览器里打开更大的编辑器：完整的 Monaco 编辑器（VS Code 使用的编辑器）和提交历史。它运行在你自己的电脑上，并通过你在线的玩家提交，结果与游戏内提交完全一样。

![AlgoCraft Web IDE 的真实画面：Two Sum 解答](https://files.seeusercontent.com/2026/10/02/2mcU/web-ide.jpg)

**Run** 编译你的代码并检查可见示例。**Submit** 由服务器判题，并包含隐藏测试；只有通过的 Submit 才计入进度和奖励。

![Accepted：7/7 测试用例通过](https://files.seeusercontent.com/2026/10/02/2eVm/feature-accepted.jpg)

## 500 道题，开箱即用

官方题库随模组附带，离线可用：**172 道 Easy、205 道 Medium、123 道 Hard**，覆盖数组、字符串、哈希表、双指针、栈、链表、树、图、回溯、贪心和动态规划。题面按第一次读题的人来写，网格、树、图和链表等文字难以说清的地方配有示意图。题目和 IDE 均提供**英文与简体中文**。

![500 道官方题：172 Easy、205 Medium、123 Hard](https://files.seeusercontent.com/2026/10/02/2mDc/feature-bank.jpg)

![带示意图的题面](https://files.seeusercontent.com/2026/10/02/pIr9/feature-diagrams.jpg)

## 融入生存世界的奖励

开启奖励时，每道题**第一次通过**会给物品和经验。题目越难奖励越多；奖励也会根据你的装备阶段（初期、钻石装备、下界合金装备）调整。比如初期首次通过一道 Hard 题会得到 5 颗钻石、3 个金苹果和 4 个铁块；拥有下界合金装备时则是 1 个下界合金锭、2 个附魔金苹果和 1 颗下界之星。

![首通奖励：按难度和装备阶段调整](https://files.seeusercontent.com/2026/10/02/X4gs/feature-rewards.jpg)

| 坚持练习 | 奖励 |
| --- | --- |
| 再次解出已经通过的题 | 较少的复习奖励，每道题每个服务器日一次 |
| 连续 3 天及以上解题 | 当天第一次获得奖励的解题有额外经验、绿宝石和经验瓶；连胜也会提高基础经验，最多 +50% |
| 第 7 天 | 至少 8 颗钻石、16 个金锭和 16 个经验瓶 |
| 第 14 天 | **2 个下界合金锭**、8 颗钻石和 4 个金苹果 |
| 第 21 天 | **3 个下界合金锭**、1 个不死图腾和 16 个经验瓶 |
| 第 28 天及之后每 7 天 | **4 个下界合金锭**、1 个附魔金苹果和 24 个经验瓶 |
| 累计不同题目 10 · 25 · 50 · 100 · 200 | 10 颗钻石 · 5 个绿宝石块 · 4 个下界合金碎片 · 2 个下界合金锭 · 1 颗下界之星 |
| 累计不同题目 500 | **一枚龙蛋** |

天数按服务器时钟计算；中断一天，连胜会从 1 重新开始。每次获得奖励的解题还有 10% 概率掉落额外的幸运奖励。

![每日连胜：第 3 天起有连胜奖励，每 7 天一次周奖励](https://files.seeusercontent.com/2026/10/02/fm5R/feature-daily.jpg)

![里程碑奖励，500 题时获得龙蛋](https://files.seeusercontent.com/2026/10/02/Paq5/feature-milestones.jpg)

## 值得专门建一面奖杯墙

成就会变成真正的 3D 奖杯，分为五档：**青铜、白银、黄金、钻石、下界合金**。每座奖杯都刻着你的名字、日期和成就，可以摆放在地面、架子或桌面上，也可以放入物品展示框。打破摆放的奖杯会完整返还，保留名字、日期和成就。共有 **23 个成就**：解题里程碑（1 到 500 题）、连胜（3 到 100 天）、难度目标，以及 *Night Owl*、*Speed Demon*、*Perfectionist* 等特殊成就，清空整个题库还能拿到下界合金 **Completionist**。

打开 Minecraft 的**进度**界面（默认 **L**）即可查看 **AlgoCraft** 成就树。待领取奖杯随世界保存；正常重启后可继续领取。

![五档奖杯](https://files.seeusercontent.com/2026/10/02/b6Ce/feature-trophies.jpg)

![23 个成就奖杯](https://files.seeusercontent.com/2026/10/02/fEf6/feature-achievements.jpg)

## 为服务器设计

**题库归服务器所有。** 同一服务器上的所有人看到同一套公共题目，公共提交由服务器判题，进度和奖励保存在世界中。玩家加入时会自动下载已发布的题面和图片。

![服务器拥有题库并判定每一次公共提交](https://files.seeusercontent.com/2026/10/02/Ekt8/feature-server.jpg)

只有服务器控制台或权限等级 2 的管理员可以管理公共题库。游戏内 IDE 的“题库更新指引”会显示下面的命令；普通玩家自动接收服务器题库，不显示更新入口。

| 命令 | 作用 |
| --- | --- |
| `/algocraft bank list` | 查看已加载的题库、题数和启用状态 |
| `/algocraft bank install <name> <url>` | 下载、校验并发布远程题库 |
| `/algocraft bank enable <id>` / `disable <id>` | 为所有人启用或停用题库 |
| `/algocraft bank reload` | 从服务器目录重新加载题库 |
| `/algocraft bank update-official` | 更新官方题库 |

玩家仍然可以**导入自己的题目**私下练习。个人导入只保存在自己的电脑上，不会获得服务器奖励，所以没人能靠自制简单题刷物资。服主可以用 `enableRewards=false` 关闭奖励，进度仍会保存。

## 下载

- **CurseForge:** <https://www.curseforge.com/minecraft/mc-mods/algocraft>
- **Modrinth:** <https://modrinth.com/mod/algocraft>
- **GitHub:** <https://github.com/CrabMods/AlgoCraft>

## 安装

| Minecraft | 模组加载器 | Java |
| --- | --- | --- |
| **1.20.1** | **Forge 47.3.0** | **17** |
| **1.21.1** | **NeoForge 21.1.216** | **21** |

选择与你的 Minecraft 版本和加载器匹配的 JAR，放进 `mods/` 文件夹。多人游戏时，服务器和每位玩家的 **Minecraft、加载器和 AlgoCraft 构建必须匹配**。两个版本提供相同的 500 道官方题、图片、进度机制与奖励。

## 常见问题

**需要会 Java 吗？** 会基础 Java 会更顺手。每道题都给出需要完成的类和方法，示例会说明应该返回什么。

**能离线玩吗？** 可以。官方题库、游戏内 IDE 和判题都不需要联网，只有 Web IDE 需要从网络加载编辑器。

**玩家能导入简单题来刷奖励吗？** 不能。奖励和奖杯只来自服务器安装的公共题库。

**能用在课堂或社团服务器吗？** 可以。用 `/algocraft bank install` 安装你们自己的题库，服务器上的每个人都会拿到同一套题。

---

预告片音乐：“Voxel Revolution”，Kevin MacLeod（incompetech.com），CC BY 4.0。音效来自 Kenney（CC0）。
