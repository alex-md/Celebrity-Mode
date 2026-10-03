# Celebrity Mode

<img src="docs/images/celebrity-mode-icon.png" alt="Celebrity Mode crown icon" width="96" height="96">

Give your OSRS character a fan club—and give that fan club something funny to say.

Celebrity Mode is a RuneLite plugin that adds 1–100 simulated followers around your character. Write custom overhead dialogue for a video bit, fill a scene with an overenthusiastic crowd, or let the fans react as you bank, fight, skill, and travel. It’s made for those moments where even a routine bank trip could use an audience.

The fans are cosmetic and visible only in your client, so they appear in recordings and streams that capture your RuneLite game view. Other players won’t see them. Overhead dialogue and fan PMs are simulated locally; the plugin never sends messages to other players.

## An audience for your next video

- **Give the crowd your own lines.** Add running jokes, catchphrases, or commentary for an OSRS video. Use only your custom quotes, or mix them with built-in chatter.
- **Let fans react to the scene.** Banking gets bank chatter. Combat gets comments about your target. Fans notice your equipped weapon and weapon swaps, talk about recent skilling, celebrate levels, and react when you die.
- **Make the crowd fit the bit.** Start with a few followers or go all the way to 100. Choose outfits, travelling formations, optional usernames, and chatter from occasional comments to constant shouting.
- **Flood your celebrity inbox.** Turn on simulated incoming fan PMs, from occasional messages to hundreds per minute, in the native private-chat display.
- **Keep the scene feeling alive.** Fans arrive gradually, follow your route, gather and wander when you stop, and occasionally wave or cheer. Different personalities and varied dialogue help them feel like a crowd of players hanging around you.

Use it for a fake celebrity encounter, a crowd that hypes up your Slayer trip, or a group of fans who have far too much to say about your bank organisation.

## Give your followers something to say

Enable **Celebrity Mode** in RuneLite, then click the gold **crown icon** in the sidebar.

1. In **Crowd**, choose 1–100 fans, a formation, and an arrival pace—or start with a scene preset. Use **Show crowd** to hide the fans between takes without losing your settings.
2. In **Extras**, choose outfits, fan names, personal space, and travelling spread.
3. In **Dialogue**, choose **My lines only** for your own material, or **Built-in + my lines** to include automatic commentary. Paste one quote per line and click **Save lines**. Placeholder buttons insert character, gear, target, and skill references.
4. Choose a dialogue frequency: **Low** for readable individual comments, **Normal** for a busy crowd, or **Spam** for everyone shouting over each other.

Crowd controls save as you change them. Quote edits stay as a draft until you save; **Revert** reloads the saved lines. The regular RuneLite settings remain available and stay in sync with the sidebar.

For example:

```text
{player} can we get a bank tour
all this gear just to forget a teleport
{player} brought the {gear}. we're saved
{target} has no idea this is going on youtube
i was here before the first 99
```

Quotes are chosen from your pool automatically. You can change the pool and other settings while the plugin is running, so swap in lines that suit the next scene. Custom quotes aren't assigned to particular activities or triggered on cue.

| Placeholder | Replaced with |
| --- | --- |
| `{player}` | Your character’s current name |
| `{gear}` | Your equipped weapon’s name; `fit` when unavailable |
| `{target}` | Your current combat target; `opponent` when unavailable |
| `{skill}` | The recently observed skill; `skill` when unavailable |
| `{level}` | The level associated with the current skill context; `0` when unavailable |

Use one quote per line if your dialogue contains commas. A single line can also use commas to separate quotes. The plugin accepts up to 100 quotes, strips markup, and limits each displayed line to 100 characters, including substituted names.

## Reactions that follow what you’re doing

Built-in dialogue uses your character’s current activity to pick its subject. Open the bank and fans talk about supplies, gear setups, and bank tabs. Fight a monster and they can name the target. Switch weapons and someone may notice. Gain skill XP and the conversation can shift to that skill. Level reactions name the actual skill and level.

The dialogue combines hundreds of patterns with personality-specific wording and remembers recent lines to reduce repetition. That gives you spontaneous commentary while you play, alongside any custom jokes you add.

Bank chatter currently detects an open bank interface; it does not identify the bank’s location. Gear comments refer to your equipped weapon. You can write location-specific lines into your custom quote pool for a scene at a particular bank.

## Celebrity inbox

In **Dialogue → Celebrity inbox**, enable **Fan PMs** to simulate an incoming flood of direct messages. Fictional senders hype you up, ask for GP or clan visits, act like old friends, or wonder whether it’s really you. Small templates combine greetings, bosses, items, clan names, requests, and slang to keep the inbox varied.

| Traffic intensity | Target average |
| --- | --- |
| Relaxed | ~20 PMs/min |
| Streamer | ~80 PMs/min |
| Global Celebrity | ~180 PMs/min |
| Peak World Record | ~360 PMs/min |

Arrivals are randomized, with natural clumps and lulls. PMs run independently of **Show crowd**, overhead chat frequency, and your custom quote pool. They start only while logged in; turning the feature off stops traffic and discards pending messages.

Messages use native incoming private chat, so the client controls the split-chat display, private-chat tab, colours, timestamps, transparency, and filtering. The plugin leaves your client settings alone. Sender names are plain: randomized badges are omitted because there is no standard configuration toggle for them. Other plugins may handle these local PMs as normal chat, including chat notifications or history.

**Play native notification sound** is optional and defaults to off. It uses the native UI sound at most once every two seconds and respects sound-effect volume and mute settings. Under load, pending traffic is capped and stale messages are dropped rather than replayed in a huge catch-up burst.

## Screenshots

Even the chicken pen gets an audience:

![Fans chatting about your gear in a chicken pen](docs/images/fans-at-chickens.png)

A larger crowd, the creator sidebar, and incoming fan PMs in split chat:

![Celebrity Mode in game with 33 fans, native fan PMs, and sidebar controls](docs/images/celebrity-mode-in-game.png)

## Your creator sidebar

The sidebar keeps crowd setup, dialogue, and appearance in three tabs, with a summary of the configured crowd and chatter at the top. Quick scene presets set fans, formation, and frequency while keeping your quotes and appearance choices.

## Settings at a glance

| Setting | Default | What it does |
| --- | --- | --- |
| Crowd size | 8 | Choose 1–100 fans; new fans use your chosen arrival pace |
| Show crowd | On | Hide or show the cast without losing settings |
| Arrival pace | Gradual | Gradual, Quick, or Instant; applies to new arrivals |
| Formation | Entourage | Travel as an Entourage, Trail, Loose Crowd, or Swarm; all gather around you when stopped |
| Personal space | On | Prefer separate tiles; large stopped crowds gather over a wider area |
| Maximum spread | 2 | Set travelling spread from 1–8 tiles |
| Gear theme | Mixed | Choose Bob, bronze, F2P, midgame, fashionscape, modern gear, or mixed outfits |
| Show fan names | Off | Show simulated usernames above fans |
| Chat frequency | Spam | Off, Low, Normal, or Spam |
| Custom quotes | Empty | Add your own overhead dialogue |
| Quote source | Mixed | Built-in dialogue, custom quotes, or both; an empty custom-only pool is silent |
| React to events | On | Enable recognition, level and death reactions, and occasional stationary emotes |
| Enable fan PMs | Off | Simulate incoming messages in native private chat |
| Traffic intensity | Relaxed | Approximately 20, 80, 180, or 360 PMs/min |
| Play native notification sound | Off | Native UI sound, throttled to once per two seconds |
| Debug overlay | Off | Show movement diagnostics for development |

**Chat frequency: Off** disables all overhead dialogue, including event reactions. **React to events** controls event reactions and emotes; activity-aware built-in chatter follows **Quote source** and **Chat frequency**. In `CUSTOM_ONLY` mode, event dialogue also comes from your custom pool. Names are controlled separately.

## Installation

Open RuneLite’s **Plugin Hub**, search for **Celebrity Mode**, and click **Install**. Updates become available after RuneLite maintainers review and merge the corresponding Hub update.

To run it locally for development, use JDK 17 and the checked-in Gradle wrapper:

```sh
./gradlew clean test jar
./gradlew run
```

The developer launcher opens RuneLite with the plugin loaded. Log in and enable **Celebrity Mode** in the plugin panel.

See [development notes](docs/development.md) for build details, implementation, test coverage, and the remaining checks in a logged-in client.

## What to expect

Larger crowds can overlap and cost more to render. Start small and increase the count to suit your scene and hardware.

Fans cannot interact with the game or control your character. The plugin makes no network requests and includes no telemetry. Outfits currently use male body kits. Valuable-drop reactions are not implemented.

The screenshots above show the plugin running in a logged-in client, including the sidebar and split-chat fan PMs. Full transition, rendering, and performance checks remain; see the [live-check checklist](docs/development.md#verification-and-remaining-live-checks).

## License

MIT; see [LICENSE](LICENSE).
