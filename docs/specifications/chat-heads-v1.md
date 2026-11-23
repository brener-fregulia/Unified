# Chat Heads V1 (M1.2)

Status labels follow [AGENTS.md](../../AGENTS.md#decision-status-labels).

The PO approved this specification on 2026-11-22, with the clarifications
in §1.1. Implementation notes are in §13.

## 1. Approved direction [Approved 2026-11-22]

| Id | Requirement |
| --- | --- |
| **R-1** | Feature id `chat_heads`, category Chat & Social. |
| **R-2** | 100% client-side. No custom networking or required server component. |
| **R-3** | Enabled by default unless a known conflict is detected. |
| **R-4** | Turning it on and off applies live where technically safe. |
| **R-5** | Original chat contents, formatting, signatures and interactions are preserved. |
| **R-6** | No copied third-party code, textures or translations. |
| **R-7** | No independent skin service unless justified and approved. |
| **R-8** | Close visual and functional parity with the original Chat Heads mod. |
| **R-9** | Player identity comes from reliable sender metadata, never only from parsing names in message text. Not every server uses `<PlayerName>`. |
| **R-10** | TAB player heads are vanilla in 26.3 when the server is in online mode. Chat Heads does not draw or change anything in TAB. |
| **R-11** | If the original Chat Heads is installed, the M0 conflict policy applies: disabled by default, the player's explicit choice wins, and a warning is shown when both run. |

### 1.1 Approval and clarifications [Approved 2026-11-22]

- CH-1 to CH-8, the configuration in §7 and CH-AC-1 to CH-AC-17 are approved, subject to the points below.
- **C-1** is approved.
- **C-2** is approved on condition that the original chat history and signed-message integrity are preserved. If updating lines already in chat would need unsafe changes to the chat subsystem, the limitation is reported and toggles apply to new messages only.
- **Q-CH2:** only reliable sender metadata. No name parsing or heuristics in V1.
- **Q-CH3:** native player-head glyph spacing. No Mixin only for a 1 px gap.
- **Q-CH4:** heads in books, signs and other screens stay deferred.

## 2. Sources

- **S-1** The unobfuscated Minecraft Java 26.3 client JAR used by this project, inspected with `javap`.
- **S-2** Fabric API 0.166.2+26.3, including `fabric-message-api-v1` 7.1.0.
- **S-3** Chat Heads **1.3.3** for Fabric 26.3: `chat_heads-1.3.3-fabric-26.3.jar`, published on [Modrinth](https://modrinth.com/mod/chat-heads) on 2026-11-22 ([source](https://github.com/dzwdz/chat_heads), MPL-2.0). Its SHA-512 matched Modrinth's published hash. Only the published JAR was inspected: its metadata, option texts (`en_us.json`), config defaults, enums, Mixin list and access widener. Its code was not read or copied.

## 3. Reference mod (S-3)

- **Mod id** `chat_heads`. It is client-only, needs Fabric Loader ≥ 0.19.5 and Minecraft ≥ 26.3, and suggests Cloth Config for its settings screen.
- **Options and defaults** (confirmed):

| Option | Default | Meaning (from its option texts) |
| --- | --- | --- |
| Render Position | Before Name | `BEFORE_NAME` or `BEFORE_LINE`. |
| Offset non-player text | on | Indents non-player lines so text aligns (Before Line only). |
| Sender Detection | UUID and heuristic | `UUID_ONLY`, `UUID_AND_HEURISTIC` or `HEURISTIC_ONLY`. The heuristic guesses the sender from message content. |
| Smart heuristics | on | Turns the heuristic off on servers known to send UUIDs. |
| Handle system messages | on | Heads may be added to pure server messages, because plugins like FreedomChat turn player chat into system messages. |
| Name Aliases, Auto-detect aliases | list, on | Manual nickname → profile mapping; reads EssentialsX `/realname` replies. |
| Render Shadow | on | |
| Right Padding | 1 px | Space after the head (0 = touching the text). |
| 3Dness | 0.0 | Draws the hat layer slightly larger. |

- **Hooks** (confirmed by its Mixin list): chat listener, packet listener, connection, chat component rendering, the chat `GuiMessage` and its lines, player chat messages, vanilla's player glyph provider and player sprite, skin texture download, books and resource packs, plus Chat Patches compatibility.
- **Assumed, to check in game:** how it sizes the head relative to text, and what it shows for unknown profiles.

## 4. Minecraft 26.3 findings (S-1, S-2)

| Id | Finding | Evidence |
| --- | --- | --- |
| **F-1** | Vanilla can draw a player head **inside text**. `Component.object(new PlayerSprite(ResolvableProfile, hat))` creates an object component. The font draws it as a glyph, with the hat (outer) layer if `hat` is true. | `contents.ObjectContents`, `contents.objects.PlayerSprite`, `FontDescription.PlayerSprite`, `gui.font.PlayerGlyphProvider` |
| **F-2** | That glyph gets its skin from `PlayerSkinRenderCache.createLookup(ResolvableProfile)`, backed by `SkinManager`. This is vanilla's shared, cached, asynchronous skin pipeline, also used by TAB and player rendering. Until the skin loads, the lookup returns the default skin. | `PlayerGlyphProvider$1`, `SkinManager.createLookup` |
| **F-3** | `ResolvableProfile.createResolved(GameProfile)` wraps a profile that is already known, with no lookup. A profile without texture data falls back to the default skin (Steve, Alex and variants, chosen from the UUID). | `ResolvableProfile`, `DefaultPlayerSkin` |
| **F-4** | Signed player chat reaches `ChatListener.showMessageToPlayer(ChatType.Bound, PlayerChatMessage, Component decorated, GameProfile sender, boolean, Instant)`. Here, and only here, the authenticated sender profile, the chat-type binding (whose `name()` is the exact name component) and the decorated display text are all available. It then calls `ChatComponent.addPlayerMessage(decorated, signature, tag)` (two call sites). | `ChatListener` bytecode |
| **F-5** | `ChatType.Bound.decorate` builds `Component.translatable(key, parameters)`. The parameters include `bound.name()` (for example `chat.type.text` = `<%s> %s`). The name can therefore be found **by identity in the component tree**, without reading any text. | `ChatType$Bound`, `ChatTypeDecoration.decorate` |
| **F-6** | The trust tag (secure, modified or not secure) is computed **before** `addPlayerMessage`. The report log stores the original `PlayerChatMessage`. Narration uses `decoratedContent()`. Changing only the display component passed to `addPlayerMessage` therefore leaves signatures, trust indicators, reporting and narration untouched. | `ChatListener.showMessageToPlayer`, `logPlayerMessage`, `narrateChatMessage` |
| **F-7** | `GuiMessage` stores no sender. System messages (`handleSystemMessage`) and disguised chat (`handleDisguisedChatMessage`, name only) carry **no sender UUID**. Vanilla's own `guessChatUUID` extracts `<name>` from the plain text, and is used only for blocking. | `GuiMessage`, `ChatListener` |
| **F-8** | Fabric's `ClientReceiveMessageEvents` can modify system messages (`MODIFY_GAME`), but there is **no event to modify player chat**. Its `CHAT` event only observes. | S-2 |
| **F-9** | TAB heads are vanilla: `PlayerTabOverlay` draws `PlayerFaceExtractor` when `ClientPacketListener.onlineMode()` is true. | `PlayerTabOverlay` |
| **F-10** | `ChatComponent` keeps a private `allMessages` list and rebuilds its lines with `rescaleChat()`. | `ChatComponent` |

Because the head is a glyph in the text (F-1), vanilla's chat code handles
it like text: wrapping, chat scale, opacity, fading, scrolling, text shadow,
GUI scale and line height all work with no extra code.

## 5. Behavior [Approved 2026-11-22]

- **CH-1 Identity.** A head is added only to **signed or unsigned player chat that arrives with a sender profile** (F-4), using that profile. System messages, command responses, plugin broadcasts and disguised chat get **no head** in V1 (F-7, R-9). This is the reference mod's "UUID only" mode.
- **CH-2 Position, Before Name (default).** The head is inserted directly before the sender's name component, found by identity in the decorated message (F-5). No extra space is inserted, so `<Name> Hello` becomes `<[head]Name> Hello`. This matches the PO's chat reference. The angle brackets and formatting come from the chat type and are untouched.
- **CH-3 Position, Before Line.** The head is placed at the start of the message.
- **CH-4 Fallback.** If the name component isn't found in Before Name mode (a custom server chat type), the head goes before the line instead. Text is never parsed.
- **CH-5 Skin.** The glyph uses the sender's profile, including the hat layer (F-1 to F-3). A skin that hasn't loaded yet shows the default skin, then updates in place. A sender who disconnects keeps their head, because the profile is stored in the message.
- **CH-6 Integrity.** Only the display copy passed to `addPlayerMessage` changes (F-6). Message text, styles, click and hover events, trust indicators, signatures, the report log and narration are not modified. The head glyph itself has no click or hover action.
- **CH-7 No extra traffic.** No skin API or profile lookups. Skins come through vanilla's cache, as for TAB.
- **CH-8 TAB.** Not touched (R-10).

## 6. Technical approach [Approved 2026-11-22]

- **TA-1** `chat_heads` is registered through `Features`, in category `CHAT_SOCIAL`, enabled by default, with toggle mode `LIVE`, and a `ConflictDescriptor("chat_heads", …)`.
- **TA-2** Building the head and locating the name are pure functions on `Component` trees, with unit tests (vanilla format, custom chat types, nested styles, missing name).
- **TA-3** Mixin **C-1**: a MixinExtras `@WrapOperation` on both `ChatComponent.addPlayerMessage` calls inside `ChatListener.showMessageToPlayer`. The handler receives the `ChatType.Bound` and `GameProfile` arguments (`@Local(argsOnly = true)`).
  - *Why Fabric isn't enough:* there is no event to modify player chat (F-8).
  - *Compatibility:* only the display component is wrapped, and other wraps still compose. The original Chat Heads hooks nearby, so the declared conflict covers it.
  - *Runtime:* applied at class load. It does nothing while the feature is off. No restart is needed.
  - *Test:* a client game test sends signed chat in a disposable world and checks that the displayed component contains exactly one `PlayerSprite` before the name, and that the signature and trust tag equal those of an identical message with the feature off.
- **TA-4** Mixin **C-2**, for LIVE: an accessor for `ChatComponent.allMessages` (F-10). Each display change made by C-1 is remembered (original ↔ with head). On disable, `GuiMessage`s already in chat are swapped back to their originals and `rescaleChat()` rebuilds the lines; enabling does the reverse. Without C-2, a toggle would only affect new messages (Q-CH1).
- **TA-5** No new skin manager, rendering framework or chat abstraction. No access widener.

## 7. Configuration [Approved 2026-11-22]

| Key | Type | Default | Apply |
| --- | --- | --- | --- |
| (feature toggle) | — | enabled (disabled when `chat_heads` is installed) | LIVE |
| `position` | ENUM `before_name` / `before_line` | `before_name` | LIVE for new messages; existing lines are rebuilt through C-2 |

Left out on purpose, as cosmetic or out of V1 scope:
- Render Shadow: the glyph follows the chat text shadow.
- Right Padding and 3Dness: native glyph metrics are used (Q-CH3).
- Offset non-player text.
- Sender heuristics, smart heuristics and name aliases.
- Handle system messages (CH-1).

## 8. Parity matrix

| Behavior | Chat Heads 1.3.3 (status) | Chat Heads V1 [Approved 2026-11-22] |
| --- | --- | --- |
| Head in player chat, Before Name | Default (confirmed) | Same (CH-2) |
| Before Line | Option (confirmed) | Same (CH-3) |
| Base and hat layer | Hat drawn; 3Dness option (confirmed) | Base and hat via vanilla glyph; no 3Dness |
| Default Steve/Alex fallback | Assumed | Vanilla default skin (F-3) |
| Signed multiplayer chat | Confirmed | Same, from sender profile |
| Singleplayer and LAN chat | Assumed | Same: both use the same `PlayerChatMessage` path |
| System messages and command responses | Heads added by heuristic, on by default (confirmed) | **No head** (R-9); see Q-CH2 |
| Custom chat formats (plugins) | Heuristic and aliases (confirmed) | Before Name if the name component is found, else Before Line; nothing for system-message chat |
| Wrapping, multiline, scale, opacity, fade, scroll | Assumed handled | Native, as text (§4) |
| Unknown profile | Assumed | No head without a sender profile; with a profile but no textures, the default skin |
| Skin loading and caching | Own Mixins on skin download (confirmed) | Vanilla `SkinManager` cache only (F-2) |
| Right padding | 1 px (confirmed) | Native glyph spacing (Q-CH3) |
| Books, config screens | Render targets (confirmed) | Not in V1 |
| TAB | Not affected | Vanilla, untouched (R-10) |

## 9. Compatibility [Approved 2026-11-22]

- **Chat Heads (`chat_heads`):** declared conflict. Both would insert heads into the same messages (duplicates). The M0 policy applies (R-11).
- **Chat Patches:** the reference ships a compatibility Mixin for it (confirmed), so it changes chat storage. No documented conflict with this design. Listed for QA, not declared.
- **No other verified conflicts.** Mods that replace `ChatComponent` drawing could still show the head, because it is an ordinary text glyph.

## 10. Acceptance criteria [Approved 2026-11-22]

| Id | Criterion |
| --- | --- |
| **CH-AC-1** | A player chat message shows exactly one head, from the sender's profile, and never from text parsing. |
| **CH-AC-2** | The head shows the skin's base and hat layers. A custom skin appears once loaded. |
| **CH-AC-3** | A profile without textures shows vanilla's default skin for that UUID. |
| **CH-AC-4** | For signed multiplayer chat, the signature, trust indicator, report screen entry and narration are identical with the feature on and off. |
| **CH-AC-5** | System messages, command output, plugin broadcasts and disguised chat show no head. |
| **CH-AC-6** | Vanilla `<Name> msg` becomes `<[head]Name> msg` with no added space. A custom chat type without a findable name gets the head before the line. Text, colors and click and hover events are unchanged. |
| **CH-AC-7** | Long messages wrap correctly. The head stays on the first line and is never split or overlapped. |
| **CH-AC-8** | At GUI scales 1–4, chat scale 0.5–1.0, any opacity, and while fading and scrolling, the head stays aligned with the text baseline and has the text's height. |
| **CH-AC-9** | Turning the feature or `position` on or off changes messages already in chat at once, without a restart (C-2). |
| **CH-AC-10** | Settings persist across restarts. |
| **CH-AC-11** | It works on a vanilla dedicated server and on Paper, never disconnects, and sends no packets. |
| **CH-AC-12** | No message text or signature is modified (CH-AC-4 plus a unit test on the display copy). |
| **CH-AC-13** | No HTTP requests beyond vanilla's own skin downloads (log check). |
| **CH-AC-14** | All new text is in `en_us` and `pt_br`. |
| **CH-AC-15** | Visual comparison with Chat Heads 1.3.3 in a separate disposable profile: same messages, Before Name and Before Line, at GUI scales 1 and 2. Differences are listed and accepted or fixed. |
| **CH-AC-16** | TAB looks the same with the feature on and off: vanilla heads in online mode, no duplicates. |
| **CH-AC-17** | With `chat_heads` installed, the feature starts disabled with the conflict status, and an explicit enable wins. |

## 11. Manual QA checklist [Approved 2026-11-22]

Use the PO's Prism testing instance for multiplayer, and disposable game
tests for everything else. Never change server authentication or install a
server-side mod.

1. On the authenticated Paper server: send chat and check `<[head]Name> text` against the PO's reference (size, alignment, spacing).
2. With a second player, check that both heads are correct and stay after that player leaves.
3. Run server and plugin commands (`/help`, `/msg`, plugin broadcasts) and check that none of them shows a head.
4. Send a long message and check the wrapping.
5. Change GUI scale, chat scale, opacity and line spacing; let chat fade; scroll.
6. Open TAB and check that heads look the same with the feature on and off.
7. Toggle the feature and `position` with messages already in chat.
8. Open the report screen on a message and check that the original is listed.
9. Check that Food HUD still works.
10. Switch to Português (Brasil).

## 12. Open questions

- **Q-CH1 [Approved 2026-11-22]** C-2 is approved conditionally (§1.1).
- **Q-CH2 [Approved 2026-11-22]** Sender metadata only; no name parsing or heuristics in V1. A later opt-in name match would need a new specification.
- **Q-CH3 [Approved 2026-11-22]** Native glyph spacing; no padding Mixin.
- **Q-CH4 [Deferred]** Heads in books, signs and config screens, as the reference does.
