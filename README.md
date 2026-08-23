# SwagJobs

A comprehensive jobs plugin for Paper 1.21.1 servers featuring XP progression, a prestige system, per-job streaks, a data-driven prestige shop, anti-cheat detection, and smelter/brewer block caps.

---

## Requirements

| Dependency | Version | Type |
|---|---|---|
| Java | 21+ | Required |
| Paper | 1.21.1 | Required |
| SwagAPI | 1.0.0 | Required (hard) |
| SwagFishing | any | Optional (soft) |
| SwagTags | any | Optional (soft) |

---

## Features

### Job System
Players are enrolled in all jobs simultaneously. XP and money are earned by performing job-specific actions. Ten jobs are available:

| Job | Action | Icon |
|---|---|---|
| Miner | Break ore and stone blocks | Diamond Pickaxe |
| Lumberjack | Chop logs | Diamond Axe |
| Farmer | Harvest fully-grown crops | Diamond Hoe |
| Fisher | Catch fish (vanilla or SwagFishing rarities) | Fishing Rod |
| Hunter | Kill mobs | Diamond Sword |
| Builder | Place configured blocks | Bricks |
| Enchanter | Use an enchanting table (tiers by level cost) | Enchanting Table |
| Smelter | Smelt items in furnaces/blast furnaces/smokers | Furnace |
| Brewer | Brew potions in brewing stands | Brewing Stand |
| Crafter | Craft configured items at a crafting table | Crafting Table |

### XP and Level Progression
- Each job levels from 1 to 100 (configurable via `xp.max-level`).
- XP required per level scales with `base-xp` per job and a prestige scaling factor.
- Rank-based XP multipliers apply based on LuckPerms permissions (`SwagJobs.rank.*`).

### Streak System
- Each job tracks an independent streak per player, keyed as `UUID:JOB_NAME`.
- Streaks reset after 5 seconds of inactivity on that specific job.
- Streak multipliers apply to money only and use a logarithmic power curve:
  - Streak 0: 1.0x | Streak 100: ~2.0x | Streak 500: ~2.8x | Streak 2000: 4.0x (hard cap)

### Prestige System
- At level 100, players can prestige their active job (up to 10 times).
- Each prestige awards 100 job points and increases XP gain and money multipliers.
- Unclaimed level rewards are auto-claimed at prestige time.
- SwagTags integration grants prestige tags at milestones 1 (Novice), 5 (Expert), and 10 (Master).

### Prestige Shop
- Spendable job points currency earned from levelling and prestiging.
- Shop items configured in `prestige_shop.yml` (data-driven, hot-reloadable).
- Supports ITEM delivery (hand item with full NBT) and COMMAND delivery.
- Optional per-player purchase limits and rarity tiers (Common/Uncommon/Rare/Epic/Legendary).
- Admin shop editor GUI opened via `/jobs shopedit`.

### Smelter/Brewer Block Cap
- Players may place unlimited furnaces, blast furnaces, smokers, and brewing stands.
- Only the first N registered blocks (ordered by placement) grant XP, where N is determined by rank:

| Rank | Cap |
|---|---|
| Member | 10 |
| Axolotl | 15 |
| Lizard | 20 |
| Flea | 25 |

### Anti-Exploit Detection
- Place-break cycle tracking for configurable exploit blocks (e.g., melon/pumpkin): place a
  tracked block and break it yourself past the configured cycle limit and XP is suppressed.
- Optional admin alerts (`anticheat.alert-admins`) for players who exceed the cycle cap.
- Macro/CPS-based detection has moved to SwagAC.

---

## Commands

### `/jobs` (aliases: `/job`, `/j`)

| Sub-command | Description | Permission |
|---|---|---|
| *(none)* | Open the job selection GUI | `SwagJobs.use` |
| `select <job>` | Set the active job displayed in the boss bar | `SwagJobs.use` |
| `progress [job]` | Open the job progress/rewards GUI | `SwagJobs.use` |
| `prestige` | Prestige the active job (requires level 100) | `SwagJobs.use` |
| `shop` | Open the prestige shop | `SwagJobs.use` |
| `shopedit` | Open the prestige shop editor | `SwagJobs.admin.shopedit` |
| `reload` | Reload config and prestige shop data | `SwagJobs.admin.reload` |
| `investigate <player>` | Teleport and vanish to a player | `SwagJobs.admin` |
| `debug` | Toggle per-player XP/money debug output | `SwagJobs.admin` |
| `help` | Show the help menu | `SwagJobs.use` |

### `/SwagJobsdev`

Developer/testing command. Requires `SwagJobs.dev` permission or OP.

| Sub-command | Description |
|---|---|
| `addpoints <player> <amount>` | Add job points |
| `setpoints <player> <amount>` | Set job points |
| `addxp <player> <job> <amount>` | Add XP to a job |
| `setxp <player> <job> <amount>` | Set XP for a job |
| `addlevel <player> <job> <amount>` | Add levels |
| `setlevel <player> <job> <level>` | Set level directly |
| `giveprestige <player> <job>` | Grant one prestige |
| `setprestige <player> <job> <n>` | Set prestige level |
| `checkcurve <player> <job> [level] [prestige]` | Print XP/money curve breakdown |
| `clearlevels <player\|all> [--reset-prestige]` | Reset levels |
| `investigate <player>` | Teleport and vanish to player |
| `acctest <player>` | Trigger a manual anti-cheat test flag |
| `smeltercap set\|get\|reset ...` | Manage smelter caps at runtime |

### `/granttag <player> <job> <tier>`

Manually grant a SwagTags prestige tag. Requires `SwagJobs.admin.granttag`. Valid tiers: `novice`, `expert`, `master`.

---

## Permissions

| Permission | Default | Description |
|---|---|---|
| `SwagJobs.use` | true | Use the jobs plugin |
| `SwagJobs.admin` | op | Admin commands (investigate, debug) |
| `SwagJobs.admin.reload` | op | Reload config |
| `SwagJobs.admin.reset` | op | Reset player data |
| `SwagJobs.admin.shopedit` | op | Open shop editor GUI |
| `SwagJobs.admin.granttag` | op | Grant prestige tags |
| `SwagJobs.dev` | op | Developer commands |
| `SwagJobs.rank.member` | true | Member rank (1.00x multiplier, 10 smelter cap) |
| `SwagJobs.rank.axolotl` | false | Axolotl rank (1.05x multiplier, 15 smelter cap) |
| `SwagJobs.rank.lizard` | false | Lizard rank (1.10x multiplier, 20 smelter cap) |
| `SwagJobs.rank.flea` | false | Flea rank (1.15x multiplier, 25 smelter cap) |

---

## Configuration

### `config.yml` (key sections)

```yaml
xp:
  max-level: 100
  base-xp-per-level: 250.0

money:
  base-money-per-level: 6.25
  money-per-xp: 0.02
  enabled: true

prestige:
  enabled: true
  max-prestige: 10
  xp-gain-per-prestige: 0.02      # +2% XP gain per prestige
  money-per-prestige: 0.08        # +8% money per prestige
  xp-requirement-per-prestige: 0.05  # +5% XP required per prestige

rank-multipliers:
  member: 1.0
  axolotl: 1.05
  lizard: 1.1
  flea: 1.15

rank-caps:            # Smelter/brewer block credit caps
  member: 10
  axolotl: 15
  lizard: 20
  flea: 25

anticheat:
  place-break-max-cycles: 3
  place-break-timeout-seconds: 120
  track-exploit-blocks: [PUMPKIN, MELON]
  alert-admins: false
  admin-permission: "SwagJobs.admin.alerts"
  flag-cooldown-ms: 60000

boss-bar:
  enabled: true
  color: GREEN
  style: SOLID
  hide-delay: 2.5     # seconds before bar fades
  title: '&a{job} &7| &fLevel {level} ...'

jobs:
  miner:
    enabled: true
    display-name: '&7&lMiner'
    icon: DIAMOND_PICKAXE
    base-xp: 250.0
    default-xp: 1.03
    default-money: 0.0052
    actions:
      stone:
        xp: 1.03
        money: 0.005
      # ... (full list in config.yml)
```

Each job section follows the same structure. Fisher additionally has `vanilla-fishing.fish.*` and `swagfishing.custom-fish.*` sub-sections.

### `prestige_shop.yml`

Data file for all prestige shop items. Managed at runtime via `/jobs shopedit`. Each item supports:

```yaml
items:
  my_item:
    slot: 22
    material: PAPER
    name: '&6My Item'
    rarity: RARE          # optional
    lore:
      - '&7A cool item'
    cost: 10              # job points
    type: COMMAND         # ITEM or COMMAND
    commands:
      - 'give %player% diamond 1'
    max-purchases: -1     # -1 = unlimited
    enabled: true
```

---

## Database

SwagJobs uses SQLite stored at `plugins/SwagJobs/SwagJobs.db`.

| Table | Purpose |
|---|---|
| `player_jobs` | Per-player per-job level, XP, prestige, and job points |
| `player_rewards` | Level-up reward records (claimed/unclaimed, per prestige) |
| `player_active_job` | Which job is currently active for each player |
| `player_smelter_blocks` | Registered smelter/brewer block locations per player |
| `prestige_shop_purchases` | Purchase log for the prestige shop |

Purchase history is also appended to `plugins/SwagJobs/logs/shop_purchases.log`.

---

## Building

Requires Maven 3.8+ and Java 21.

```bash
mvn clean package
```

The shaded JAR (with SQLite JDBC bundled) is produced at `target/SwagJobs-1.0.0.jar`.

Place the JAR in your server's `plugins/` directory alongside Vault and LuckPerms.
