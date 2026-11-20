# Feature Catalog

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).

The feature list and categories below are **[Approved]**. Feature identifiers,
milestone assignments and the restart notes are **[Proposed]** unless marked
otherwise. "Inspired by" names describe behavior only; no third-party code or
assets may be copied.

## Identifier rules [Proposed]

- Pattern: `^[a-z][a-z0-9_]{1,63}$` (lowercase, digits, underscores).
- Stable forever once released: identifiers key configuration and data files.
- Renaming a released identifier requires a configuration migration.

## Categories [Approved]

| Category | Proposed id |
| --- | --- |
| HUD & Information | `hud_information` |
| Inventory | `inventory` |
| Visuals | `visuals` |
| Controls | `controls` |
| Chat & Social | `chat_social` |
| Performance & Network | `performance_network` |
| Maps & Navigation | `maps_navigation` |

## Features

| Feature | Category | Proposed id | Milestone | Inspiration / notes |
| --- | --- | --- | --- | --- |
| Food information | HUD & Information | `food_hud` | M1 [Approved: first feature after Core] | AppleSkin |
| Block and entity inspector | HUD & Information | `inspector` | M2 [Proposed] | Jade |
| Inventory sorting | Inventory | `inventory_sort` | M2 [Proposed] | Must use vanilla click actions only |
| Item transfer | Inventory | `item_transfer` | M2 [Proposed] | Must use vanilla click actions only |
| Protected slots | Inventory | `protected_slots` | M2 [Proposed] | Must not lose items on failure |
| Shulker preview | Inventory | `shulker_preview` | M2 [Proposed] | Combined behavior of Shulker Box Tooltip and Simple Shulker Preview |
| Distinct potion identification | Visuals | `potion_identification` | [Open] | |
| Keybinding search | Controls | `keybind_search` | [Open] | |
| Keybinding conflict detection | Controls | `keybind_conflicts` | [Open] | |
| Player heads in chat | Chat & Social | `chat_heads` | [Open] | Uses skin data already available to the client; see Q-F3 |
| Server ping optimization | Performance & Network | `server_ping` | M3 [Approved] | Likely restart-required |
| Fast world exit | Performance & Network | `fast_world_exit` | M3 [Approved] | Must not compromise world data safety |
| Minimap | Maps & Navigation | `minimap` | M4 [Approved] | |
| World map | Maps & Navigation | `world_map` | M4 [Approved] | |
| Waypoints | Maps & Navigation | `waypoints` | M4 [Approved] | |
| Persistent exploration data | Maps & Navigation | `exploration_data` | M4 [Approved] | Stored separately from configuration |

M3 and M4 assignments follow directly from the milestone names in the
[vision](vision.md#milestones-approved).

## Open questions

- **Q-F1 [Open]** Which features belong to M1 (Essentials) besides Food HUD,
  and which to M2?
- **Q-F2 [Open]** Is persistent exploration data a user-toggleable feature or
  a shared service owned by the map features?
- **Q-F3 [Open]** May chat heads fetch skins from the network, or only use
  skins the client already has?
- **Q-F4 [Open]** Should inventory features be disabled by default on
  multiplayer servers (some servers forbid automated inventory actions)?
