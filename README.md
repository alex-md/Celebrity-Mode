# Celebrity Mode

A RuneLite novelty plugin that surrounds your character with 1–30 simulated fans. Fans are independent cosmetic scene objects: other players cannot see them, and they cannot click, move your character, send chat, interact with objects, or change real player/NPC collections.

## Features

- Four travelling styles: Entourage, Trail, Loose Crowd, and Swarm. When you stop, every mode gathers close around you and wanders.
- Player-route breadcrumbs, collision-aware formation placement, short local detours, walking/running catch-up, and smooth movement through intermediate tiles.
- Persistent cache-backed human body models, randomized colors, and Bob, bronze, F2P, midgame, fashionscape, modern gear, or mixed outfits. A bounded cache scan discovers wearable gear and combines related pieces with varied accessories, rather than selecting fixed outfits.
- Constant crowd chatter with an extreme Spam setting, optional simulated usernames, rare stationary waves/cheers, level and death reactions, and a rate-limited recognition burst.
- Gradual, accelerating arrivals from nearby tiles on login, enable, or relocation. Runtime resizing and settings changes; followers retain their positions and route across ordinary region loading; true teleports, plane changes, world-view changes, hopping, and logout start a fresh arrival.
- An optional debug overlay showing breadcrumbs, targets, IDs, formation slots, state, and target distance.

## Settings

| Setting | Default | Behavior |
| --- | --- | --- |
| Crowd size | 8 | 1–30 fans; adds/removes only the necessary entities |
| Formation | Entourage | Controls travelling offsets; every mode crowds around you while stopped |
| Personal space | On | Prefers distinct tiles and avoids resting on your tile; stopped fans gather within 2–3 tiles |
| Maximum spread | 2 | 1–4 tiles of lateral spread |
| Gear theme | Mixed | Persistent randomly chosen outfits |
| Show fan names | Off | Draws names in the overlay |
| Chat frequency | Spam | Off; Low (one message every ~3–6s); Normal (crowd bursts every ~0.6–1.2s); Spam (large bursts every ~0.6s) |
| Custom quotes | Empty | One quote per line; commas are separators only when no newlines exist |
| Quote source | Mixed | Built-in, custom-only, or both; empty custom-only is silent |
| React to events | On | Rare crowd-wide stationary emotes (~60–144s apart), recognition, level, and death reactions |
| Debug overlay | Off | Enables movement diagnostics |

Chat **Off** disables all overhead dialogue, including event reactions. Names and emotes have their own settings. Generated dialogue uses 375 composable patterns across 37 categories, personality vocabularies, and your current character name. Fans observe walking/running, the open bank interface, combat targets, low health during combat, weapon changes, and recent skill XP. Skill-specific chatter includes newer skills and methods. Recent messages are remembered across the crowd and by each fan to reduce repetition. Skill/target observations expire; initial login XP snapshots never count as activity. Level reactions mention the actual skill and level. Custom quotes can use `{player}`, `{gear}`, `{target}`, `{skill}`, and `{level}` placeholders. Custom quotes are trimmed, stripped of markup, limited to 100 characters each, and capped at 100 entries. Newline-separated quotes can contain commas.

## Build and run

Use JDK 17 and the checked-in Gradle wrapper. Java output targets release 11, matching the [official example plugin](https://github.com/runelite/example-plugin). Development resolves RuneLite `latest.release`; the verified build resolved **1.13.1**.

```sh
./gradlew clean test jar
./gradlew run
```

Use `./gradlew run -PverifyModels` for an opt-in check that builds every gear tier and applies eight animation sequences against the actual client cache at the login screen. This check is confined to the developer launcher.

The developer launcher opens RuneLite with the plugin loaded. Enable **Celebrity Mode** in its plugin panel after logging in. The ordinary build artifact is `build/libs/celebrity-mode.jar`; the developer launcher is the supported local test route. `./gradlew shadowJar` also builds a developer launcher with dependencies. Plugin Hub distribution requires submission and review using the [Plugin Hub process](https://github.com/runelite/plugin-hub); this repository does not claim Hub approval.

There are no additional runtime dependencies, network requests, telemetry, background workers, menu actions, or packet hooks in plugin code. Build tools and the RuneLite client itself retain their normal dependency/download behavior.

## Implementation

`CelebrityModePlugin` owns event subscriptions and dispatches configuration/lifecycle mutations to the client thread. `FanManager` owns every fan's single active `RuneLiteObject`, appearance/model, breadcrumb history, scene identity, and chat scheduler. Removing an entity always unregisters its object. Scene resets unregister all objects and clear local interpolation positions before reseeding. Models remain reusable across resets; changing the gear theme discards them.

The public [RuneLiteObject API](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/RuneLiteObject.html) and [AnimationController API](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/AnimationController.html) handle registration and animation. Each fan owns a controller; sequence resources are cached, and unchanged states do not restart animation. Animation IDs use current generated `gameval` names; equipment is discovered from the current cache. Normal, whip, and staff poses use corresponding supported sequences; waves and cheers complete once before returning to idle.

The public API does not expose creation of arbitrary player compositions. `FanModelFactory` uses `Client.getIndexConfig()`, `loadModelData()`, and `mergeModels()` to assemble selectable male identity kits and item **worn** meshes. Its small cache-definition decoder follows the public RuneLite [KitLoader](https://github.com/runelite/runelite/blob/master/cache/src/main/java/net/runelite/cache/definitions/loaders/KitLoader.java) and [ItemLoader](https://github.com/runelite/runelite/blob/master/cache/src/main/java/net/runelite/cache/definitions/loaders/ItemLoader.java) formats, including extended model IDs. It clones mutable mesh data before recoloring, texturing, or translating it. Unsupported equipment definitions fall back to the fan's body kits; temporarily missing model resources retry the same appearance after a short cooldown. The Modern Gear theme selects wearable items introduced in the newer item-ID range (20000 onward); it is a broad variety filter, not a combat-stat rating. It never changes a real `Player` or `PlayerComposition` and uses no reflection.

Logical positions are **scene-space WorldPoints within the active WorldView**, obtained from the local player's local position. In an instance, these are deliberately not canonical template coordinates: two copies of the same template room remain distinguishable. World-view identity, ID, base coordinates, plane, loading state, and impossible displacement are checked before using retained local coordinates. Render points are rebuilt with WorldView-aware conversions; the overlay uses RuneLite Perspective projection.

Breadcrumbs contain at most 80 unique movement positions. Running segments receive an intermediate breadcrumb when collision checks validate it. Travelling followers choose independent breadcrumb delays and uneven offsets, with persistent sub-tile variation to avoid rows and identical tile centers. After two stationary game ticks, all modes use close radial gathering and independent wandering targets. Fans finish their current short stroll before choosing another target, with brief randomized pauses. Movement remains active even when chat and reactions are disabled. A crowd-wide timer allows only an occasional stationary fan to emote; moving fans never begin an emote. `WorldArea.canTravelInDirection(WorldView, ...)` handles walls and diagonal corners. Movement first uses the breadcrumb route; blocked direct movement permits a detour of at most 96 visited tiles within six tiles. There is no global per-fan pathfinder. Long separation or repeated stalls trigger an individual repair. Initial arrivals model binary word-of-mouth diffusion: a spotter appears after four ticks (2.4 seconds), then waves of 2, 4, 8, and 15 fans arrive roughly 16 ticks (9.6 seconds) apart. Arrivals trickle within each wave with deterministic ±2-tick reaction/pathing jitter; all 30 fans are scheduled within about 45–50 seconds. Ordinary region loading preserves both existing fans and pending arrival deadlines. Fans enter on valid tiles 4–6 tiles away and approach on foot; geometry may require a nearer fallback. Additional fans from a size increase also arrive gradually. Client ticks interpolate one- or two-step paths over 30 client cycles, preserving intermediate corners.

## Verification and remaining live checks

Unit and scene-fixture tests cover bounded/unique trails, quote parsing and modes, scheduling ranges, duplicate avoidance, cache decoding and recoloring, formation assignment, personal space, walls/diagonals, bounded detours, crowd sizes 1/8/30, runtime settings changes, model reuse, individual repair, interpolation, teleport/plane/world-view resets and preservation across base changes, hopping, missing players, and cleanup. Tests additionally verify over 2,500 distinct generated lines in a seeded corpus sample, context switching and expiry, noncombat NPC interactions, low-health conditions, recent-line memory, skill-specific level reactions, generated dialogue variety, live-name placeholders, cache-derived modern outfits, a 200-tile run through 25 loading boundaries with object identities retained, branching arrival waves, bounded jitter, a 45–50-second full-crowd deadline, pending arrivals preserved through region reloads, frequent wandering around the player in every mode, rare crowd-wide emotes, and near-constant visible chat. A sustained 30-fan fixture walks/runs a rectangle beside blocked geometry and verifies bounded history, valid positions, persistent models, and no registered objects after shutdown. The fixture uses real RuneLiteObject/AnimationController implementations with in-memory API surfaces; it does not render the game cache or measure GPU cost.

Before publishing, perform these checks in a logged-in RuneLite client:

1. Compare 1, 8, and 30 fans in each formation; walk/run around walls, doorways, and corners, then stop and watch every mode gather and wander around you. Check the accelerating arrival effect after enabling or teleporting.
2. Inspect every gear theme, held-weapon animation, names, chat placement, chat overlap, and idle reactions with CPU and GPU rendering.
3. Change crowd size, appearance, formation, spread, quotes, and chat frequency without restarting. Confirm Off suppresses recognition/event dialogue.
4. Teleport, change plane, enter an instance (including repeated template rooms), cross a loading boundary, hop, logout/login, reconnect, and repeatedly enable/disable the plugin. Check for stale or leftover objects.
5. Compare frame time and allocations with 30 fans versus the disabled plugin on typical hardware.

Actual cache model construction and animation application have been verified for every gear tier at the login screen. Untextured meshes are supported without cloning a nonexistent texture array. Visual appearance, actual instance rendering, movement quality, and GPU performance have **not been verified in a logged-in client**. Outfits currently use male body kits. Bounded detours intentionally favor graceful sharing/repositioning over navigating arbitrary maze geometry. Valuable-drop reactions are not implemented; level and death reactions are included.

## License

MIT; see [LICENSE](LICENSE).
