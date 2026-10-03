# Development notes

[Back to the README](../README.md)

## Build and run

Use JDK 17 and the checked-in Gradle wrapper. Java output targets release 11, matching the [official example plugin](https://github.com/runelite/example-plugin). Development resolves RuneLite `latest.release`; the verified build resolved **1.13.1**.

```sh
./gradlew clean test jar
./gradlew run
```

Use `./gradlew run -PverifyModels` for an opt-in check that builds every gear tier and applies eight animation sequences against the actual client cache at the login screen. This check is confined to the developer launcher.

The developer launcher opens RuneLite with the plugin loaded. Enable **Celebrity Mode** in its plugin panel after logging in. The ordinary build artifact is `build/libs/celebrity-mode.jar`; the developer launcher is the supported local test route. `./gradlew shadowJar` also builds a developer launcher with dependencies. Plugin Hub distribution requires submission and review using the [Plugin Hub process](https://github.com/runelite/plugin-hub); this repository does not claim Hub approval.

There are no additional runtime dependencies, network requests, telemetry, menu actions, or packet hooks in plugin code. Fan PMs use one bounded, disposable background scheduler while active; the rest of the scene simulation runs on client ticks. Build tools and the RuneLite client itself retain their normal dependency/download behavior.

## Sidebar development

The creator panel uses RuneLite’s [PluginPanel](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/ui/PluginPanel.java), `NavigationButton`, and `ClientToolbar`. Swing components are created and refreshed on the EDT. Controls write through `ConfigManager`; the existing `ConfigChanged` subscription applies scene mutations on the client thread. Configuration keys remain compatible with the regular settings panel. A quote draft survives unrelated setting changes and external edits; Save replaces the stored pool, and Revert reloads it.

The toolbar icon loads the supplied crown PNG from the bundled classpath resources and scales it for the sidebar. The root `icon.png` is a 48×48 version for Plugin Hub discovery. No external image library is required. Render all three tabs at the real sidebar width with RuneLite’s look and feel:

```sh
./gradlew previewSidebar
```

The output is `build/sidebar-preview.png`. This checks panel layout without launching the game; it does not establish toolbar integration or in-game performance.

## Fan PM engine

`pm.FanDialogueGenerator` is separate from the existing overhead `chat.FanDialogueGenerator`. It expands nested lexical slots in up to four passes, escapes regex replacement metacharacters, and weights hype/friend/request/skeptic tones 45/25/20/10. Procedural usernames use at most 12 valid name characters, avoid the local player's name, and have bounded generation retries. Recent-name and recent-line histories each retain 64 entries. PM subjects are fictional fan chatter, not verified loot or achievement observations.

`FanMessageScheduler` creates one daemon `ScheduledThreadPoolExecutor` with one outstanding delayed task. Exponential inter-arrival delays target 20/80/180/360 PMs per minute. The worker receives only a character-name snapshot and never calls the client API. Its inbox retains at most 32 messages; overflow is dropped, stale messages expire after five seconds, and dispatch is limited to two messages per client tick and twelve per rolling second. Scheduling is relative to the current worker run, avoiding timer catch-up loops. A generation token invalidates callbacks already racing with cancellation. Disable, loading, hopping, logout, and plugin shutdown clear the owned inbox, cancel the timer, and shut down the executor; login or re-enable creates a fresh session.

`FanPmEngine` runs the client bridge on `ClientTick`. It builds plain `QueuedMessage` values with `PRIVATECHAT`, fictional names, and Jagex-escaped text (so `<3` remains a literal heart). It leaves formatting and timestamp overrides unset. RuneLite's [ChatMessageManager](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/chat/ChatMessageManager.java) and the native renderer apply the active Chat Color, split-chat, transparency, timestamps, and private-chat filters. No duplicate style settings or custom chat overlay are introduced.

The bridge queues a bounded batch and calls the public manager `process()` immediately on the client thread. Thus this plugin leaves no deferred PM entries in RuneLite's shared queue after a completed dispatch; it never clears another plugin's queue. Existing displayed history is left to native chat/history behavior. `PRIVATECHATOUT` is not used, and no messages are sent to the server. Random badges are omitted because standard configuration does not expose such a toggle.

Optional audio uses the one-argument `Client.playSoundEffect(SoundEffectID.UI_BOOP)`, which follows native sound-effect volume; the volume-forcing overload is not used. Playback is gated to once per two seconds. The client has no dedicated PM sound constant in this API, so this is the native UI notification sound, not a custom audio asset. Other enabled chat-notification plugins retain their own behavior.

Tests use a manual scheduled executor and clock to cover traffic means and variance, bounded queues, stale drops, rolling dispatch caps, cancelled-callback races, fresh sessions, native PM fields/escaping, sound throttling, and config/login/shutdown gating without timing-dependent sleeps. Corpus tests check resolved slots, legal names, text bounds, tone weights, and variety.

## Implementation

`CelebrityModePlugin` owns event subscriptions and dispatches configuration/lifecycle mutations to the client thread. `FanManager` owns every fan's single active `RuneLiteObject`, appearance/model, breadcrumb history, scene identity, and chat scheduler. Removing an entity always unregisters its object. Scene resets unregister all objects and clear local interpolation positions before reseeding. Models remain reusable across resets; changing the gear theme discards them.

The public [RuneLiteObject API](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/RuneLiteObject.html) and [AnimationController API](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/AnimationController.html) handle registration and animation. Each fan owns a controller; sequence resources are cached, and unchanged states do not restart animation. Animation IDs use current generated `gameval` names; equipment is discovered from the current cache. Normal, whip, and staff poses use corresponding supported sequences; waves and cheers complete once before returning to idle.

The public API does not expose creation of arbitrary player compositions. `FanModelFactory` uses `Client.getIndexConfig()`, `loadModelData()`, and `mergeModels()` to assemble selectable male identity kits and item **worn** meshes. Its small cache-definition decoder follows the public RuneLite [KitLoader](https://github.com/runelite/runelite/blob/master/cache/src/main/java/net/runelite/cache/definitions/loaders/KitLoader.java) and [ItemLoader](https://github.com/runelite/runelite/blob/master/cache/src/main/java/net/runelite/cache/definitions/loaders/ItemLoader.java) formats, including extended model IDs. It clones mutable mesh data before recoloring, texturing, or translating it. Unsupported equipment definitions fall back to the fan's body kits; temporarily missing model resources retry the same appearance after a short cooldown. The Modern Gear theme selects wearable items introduced in the newer item-ID range (20000 onward); it is a broad variety filter, not a combat-stat rating. It never changes a real `Player` or `PlayerComposition` and uses no reflection.

Logical positions are **scene-space WorldPoints within the active WorldView**, obtained from the local player's local position. In an instance, these are deliberately not canonical template coordinates: two copies of the same template room remain distinguishable. World-view identity, ID, base coordinates, plane, loading state, and impossible displacement are checked before using retained local coordinates. Render points are rebuilt with WorldView-aware conversions; the overlay uses RuneLite Perspective projection.

Breadcrumbs contain at most 80 unique movement positions. Running segments receive an intermediate breadcrumb when collision checks validate it. Travelling followers choose independent breadcrumb delays and uneven offsets, with persistent sub-tile variation to avoid rows and identical tile centers. After two stationary game ticks, all modes use close radial gathering and independent wandering targets. Fans finish their current short stroll before choosing another target, with brief randomized pauses. Movement remains active even when chat and reactions are disabled. A crowd-wide timer allows only an occasional stationary fan to emote; moving fans never begin an emote. `WorldArea.canTravelInDirection(WorldView, ...)` handles walls and diagonal corners. Movement first uses the breadcrumb route; blocked direct movement permits a detour of at most 96 visited tiles within six tiles. There is no global per-fan pathfinder. Long separation or repeated stalls trigger an individual repair. Initial arrivals model binary word-of-mouth diffusion: a spotter appears after four ticks (2.4 seconds), then waves of 2, 4, 8, 16, 32, and the remaining fans arrive roughly 16 ticks (9.6 seconds) apart. Arrivals trickle within each wave with deterministic ±2-tick reaction/pathing jitter; 30 fans are scheduled within about 45–50 seconds and 100 within about 75 seconds. Quick compresses the delays to a quarter; Instant makes new fans eligible immediately. Ordinary region loading preserves both existing fans and pending arrival deadlines. Fans enter on valid tiles 4–6 tiles away and approach on foot; geometry may require a nearer fallback. Additional fans from a size increase use the selected arrival pace. Hiding the crowd unregisters its objects and clears scene state while retaining appearances and settings; showing it starts a fresh entrance. Stopped crowds above 30 fans use a larger gathering radius. Client ticks interpolate one- or two-step paths over 30 client cycles, preserving intermediate corners.

## Verification and remaining live checks

Unit and scene-fixture tests cover bounded/unique trails, quote parsing and modes, scheduling ranges, duplicate avoidance, cache decoding and recoloring, formation assignment, personal space, walls/diagonals, bounded detours, crowd sizes 1/8/30/60/100, runtime settings changes, model reuse, individual repair, interpolation, teleport/plane/world-view resets and preservation across base changes, hopping, missing players, and cleanup. Tests additionally verify over 2,500 distinct generated lines in a seeded corpus sample, context switching and expiry, noncombat NPC interactions, low-health conditions, recent-line memory, skill-specific level reactions, generated dialogue variety, live-name placeholders, cache-derived modern outfits, a 200-tile run through 25 loading boundaries with object identities retained, branching arrival waves, bounded jitter, a 45–50-second full-crowd deadline, pending arrivals preserved through region reloads, frequent wandering around the player in every mode, rare crowd-wide emotes, and near-constant visible chat. Sidebar tests cover config synchronization without feedback writes, deferred slider updates, presets, quote Save/Revert, and draft preservation during external edits. A 100-fan fixture exercises movement and gathering in all formations, model reuse, valid positions, and cleanup. A sustained 30-fan fixture walks/runs a rectangle beside blocked geometry and verifies bounded history, valid positions, persistent models, and no registered objects after shutdown. The fixture uses real RuneLiteObject/AnimationController implementations with in-memory API surfaces; it does not render the game cache or measure GPU cost.

Before publishing, perform these checks in a logged-in RuneLite client:

1. Enable the plugin and open the crown icon. Check all tabs, scrolling, keyboard focus, Save/Revert, settings synchronization, presets, and hide/show. Disable/re-enable and verify that the toolbar icon and panel are removed and restored once.
2. Compare 1, 8, 30, 60, and 100 fans in each formation; walk/run around walls, doorways, and corners, then stop and watch every mode gather and wander around you. Check the accelerating arrival effect after enabling or teleporting.
3. Inspect every gear theme, held-weapon animation, names, chat placement, chat overlap, and idle reactions with CPU and GPU rendering.
4. Change crowd size, appearance, formation, spread, quotes, and chat frequency without restarting. Confirm Off suppresses recognition/event dialogue.
5. Teleport, change plane, enter an instance (including repeated template rooms), cross a loading boundary, hop, logout/login, reconnect, and repeatedly enable/disable the plugin. Check for stale or leftover objects.
6. Enable Fan PMs with split private chat on/off, private chat filtering modes, opaque/transparent chatboxes, Chat Color overrides, and timestamps on/off. Confirm incoming names and literal `<3` rendering. Check all traffic settings, sound on/off and muted, notifications/history integrations, disable/re-enable, hopping, logout/login, and shutdown. Verify no stale catch-up flood after a client stall. These native render and audio behaviors still require a logged-in live check.
7. Compare frame time and allocations with 30 and 100 fans versus the disabled plugin on typical hardware.

Actual cache model construction and animation application have been verified for every gear tier at the login screen. Untextured meshes are supported without cloning a nonexistent texture array. The user-provided live screenshots demonstrate follower appearances, overhead dialogue, sidebar controls, and split-chat fan PMs in a logged-in client. Full instance/transition coverage and GPU performance have **not been verified**. Outfits currently use male body kits. Bounded detours intentionally favor graceful sharing/repositioning over navigating arbitrary maze geometry. Valuable-drop reactions are not implemented; level and death reactions are included.

