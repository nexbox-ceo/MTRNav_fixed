# MTRNav

An in-game smartphone overlay for Minecraft Transit Railway (MTR): journey
planning, live "next train" boards, and turn-by-turn navigation. Built for
**Fabric 1.18.2 + MTR 4.0.5**.

## What's in here

```
src/main/java/com/mtrnav/
  MTRNavMod.java        common entrypoint: registers the Smartphone item, hooks up Dynmap
  MTRNavClient.java     client entrypoint: keybinding, HUD, phone-open wiring
  item/                 the Smartphone item (Misc tab) that opens the phone on right-click
  compat/               MTRDataBridge -- the ONLY file that touches MTR's classes directly
  routing/              the transit graph + async A*/Dijkstra journey search
  journey/              the five-phase turn-by-turn state machine
  client/               keybinding + HUD widget (bottom-left journey status)
  phone/                the phone screen itself, and its apps (MTRNav, Bank, Times, Settings)
  tts/                   voice announcements using your OS's built-in TTS (see "Voice announcements" below)
  dynmap/                optional: puts MTR stations on Dynmap's web map
```

## Building

**This project ships without a working Gradle wrapper.** A real `gradlew`
needs its `gradle-wrapper.jar` fetched from Gradle's own servers, which
this build had no network access to do -- only `gradle-wrapper.properties`
(a small text file naming the Gradle version) is included. Three ways to
actually build it, roughly in order of "install nothing on your own
computer" to "full local dev setup":

**Option A -- GitHub Actions (nothing installed locally, builds in the
cloud):** this repo includes `.github/workflows/build.yml`, which builds
the mod on GitHub's own servers every time you push. All you need is a
free GitHub account:
1. Create a new repository on GitHub and upload this folder's contents
   (drag-and-drop through the web UI works, since everything here except
   the vendored jars is small -- see the note on `libs/` below).
2. Push/upload triggers the workflow automatically (or trigger it by hand
   from the Actions tab -> "Build MTRNav" -> "Run workflow").
3. Once it's green, open that run and download the `mtrnav-jar` artifact
   -- that's your built mod, ready to drop into `mods/`.

This is the option worth using if your computer can't easily run a modern
JDK/IDE (e.g. an old OS version) -- the actual compiling happens on
GitHub's servers, not yours.

**Option B -- IntelliJ IDEA (easiest local option, recommended if your
computer can run it):** install the free Community Edition, then File ->
Open this folder. IntelliJ detects `gradle-wrapper.properties`, downloads
the right Gradle version itself, and generates the wrapper for you -- from
then on its Gradle panel (or a terminal inside IntelliJ) can run the
`build` task like normal.

**Option C -- Gradle installed yourself:** install Gradle 8.5+
(<https://gradle.org/install>), then from this folder run:
```
gradle wrapper --gradle-version 8.5
```
That generates the real `gradlew`/`gradlew.bat`/`gradle-wrapper.jar`. From
then on, use `./gradlew build` (Mac/Linux) or `gradlew.bat build`
(Windows) as normal.

Either way, before building:
1. `libs/Dynmap-3_7-beta-4-fabric-1_18_2.jar` should already be there (~11
   MB, fine to commit/upload anywhere). `libs/MTR-fabric-4_0_5_1_18_2.jar`
   is ~85 MB -- too big for GitHub's plain drag-and-drop web upload, so
   for **Option A specifically, leave it out**: `build.gradle` detects
   it's missing and automatically fetches MTR from Modrinth's Maven
   instead (see the comment right above that line for one thing worth
   double-checking if that resolution ever fails). For Options B/C,
   keep the local jar if you already have it -- it's used automatically
   when present.
2. Check `gradle.properties` against <https://fabricmc.net/develop> for
   1.18.2 (yarn/loader/fabric-api versions do occasionally get new builds).
3. Run the `build` task. The built jar lands in `build/libs/`.

Dynmap is a genuinely optional dependency: if you don't want the web-map
integration, delete `src/main/java/com/mtrnav/dynmap/DynmapIntegration.java`,
remove the two `DynmapIntegration` lines from `MTRNavMod.java`, drop the
Dynmap jar from `build.gradle`, and remove `"recommends"` from
`fabric.mod.json`. Nothing else depends on it.

## How this was built, and what to double-check

Almost none of this is guesswork. MTR 4.x has no published addon API, so
every class in `compat/` and `dynmap/` was verified by decompiling the
**actual jars you uploaded** with `javap` (and, for the Dynmap HTTP path,
grepping the compiled bytecode for literal string constants) rather than
relying on memory of older MTR versions or similar mods. Specifically
confirmed against your jars:

- `org.mtr.core.data.{Data, ClientData, Station, Platform, Route,
  RoutePlatformData, NameColorDataBase, TransportMode, Position,
  SavedRailBase, AreaBase}` -- the whole shape `MTRDataBridge` and
  `TransitGraph` are built on.
- `org.mtr.mod.client.MinecraftClientData` -- the client-side data
  singleton.
- `org.mtr.mod.data.{ArrivalsCache, ArrivalsCacheClient}` and
  `org.mtr.core.operation.ArrivalResponse` -- the live "next train" feed
  the Times app uses (the same one MTR's own in-world PIDS boards read
  from).
- `org.dynmap.{DynmapCommonAPIListener, DynmapCommonAPI}` and
  `org.dynmap.markers.{MarkerAPI, MarkerSet, Marker}` -- Dynmap's marker
  API.
- MTR's embedded webserver really does expose `/mtr/api/map/*` with an
  `endpoint=stations-and-routes` option and a `dimension` parameter --
  found as literal constants in `SystemMapServlet`'s compiled bytecode.

**Three things a compile/first run may still need adjusting, all isolated
to one clearly-marked spot each:**

1. **`Route#durations` alignment** (`TransitGraph.addRideEdges`). The field
   exists and is a parallel list to stops, but this build couldn't confirm
   *which* index pairing it uses without a full decompile. If ETAs look
   off, that's the first place to check -- everything else degrades to a
   flat per-hop estimate rather than crashing if the assumption is wrong.
2. **Boarding-confirmation** (`JourneyManager.confirmBoardedCorrectLine`).
   Verifying that the vehicle a player just mounted matches the expected
   line needs `VehicleExtraData#getThisRouteId()` (confirmed to exist)
   wired to whatever entity/seat class the player is actually riding
   (not confirmed in this pass). Currently this just assumes yes.
3. **Dynmap JSON field names** (`DynmapIntegration.extractStations`). The
   endpoint path and its parameters are verified real; the JSON body's
   exact key names for a station's name/position weren't. Run
   `curl "http://localhost:<port>/mtr/api/map/stations-and-routes?dimension=<id>"`
   against a running world and adjust the key names there if no markers
   show up.

## Voice announcements

The phone's **Settings** app lets you pick an announcement voice: UK Male,
UK Female, US Male, US Female, or None. This uses whatever speech engine
is already built into your OS -- no account, no API key, nothing to
install. (This mod's TTS has actually been through three approaches now:
espeak-ng needed installing and PATH setup that turned out to be more
hassle than it was worth; ElevenLabs needed an account, API key, and
internet; this OS-native version needs none of that.)

- **Windows**: PowerShell + `System.Speech.Synthesis`, present on every
  Windows install. It looks up an installed voice matching the chosen
  accent/gender at speak-time rather than hard-coding a specific voice
  name, since exactly which voices (Hazel, George, Zira, David, ...) are
  present depends on which language packs are installed.
- **macOS**: the `say` command with Apple's long-standing stock voices
  (Daniel/Kate for UK, Alex/Samantha for US), falling back to your Mac's
  default voice if one of those specific ones isn't present.
- **Linux**: honestly the weak point -- there's no single TTS engine every
  Linux desktop ships with the way Windows/macOS do. This tries
  `spd-say` (speech-dispatcher, often present if accessibility features
  are on) and falls back to `espeak-ng`/`espeak` if installed, but on a
  bare-bones install none of those may exist, in which case it just stays
  silent rather than erroring.

None of these three code paths could actually be run and listened to in
the environment this was written in, so the first announcement you hear
is the real verification step for whichever OS you're on.

Announcements fire on: starting a journey ("Let's go to X", then the first
instruction), reaching a platform once a live arrival is found ("Wait N
minutes for the Bus 21 to Yorktown"), and one stop before your alight stop
while riding ("Drop off at X") -- intermediate stops in between aren't
announced, matching how real transit PA systems behave.

## Bank app: skip the ticket machine

The Bank app shows what's in your pockets and has a **Keep** button that
moves those emeralds into a server-held wallet, viewable via the **Saved**
button -- that's the only door in. Cashing out into `mtrnav:ticket` items
happens from the Saved view, so every emerald passes through the wallet
first; there's no immediate pocket-to-ticket shortcut.

All of the actual emerald/ticket movement happens server-side over a small
custom packet (`com.mtrnav.bank`) -- the client only ever displays state
and sends requests, so nothing here can desync or be cheated by editing
client files. One honest caveat: MTR 4.0.5 has no ticket item of its own
(its fare gates are blocks -- `TICKET_BARRIER_ENTRANCE_1` etc. -- confirmed
in the jar, but not something this build reverse-engineered), so the
Ticket item here is MTRNav's own thing. It's not confirmed to do anything
if you feed it into MTR's own ticket barriers -- treat it as this mod's own
receipt/currency unless you wire that up yourself.

Wallet balances are stored per-player-UUID in
`config/mtrnav-wallets.properties` -- see `BankWallet`'s class doc for why
that's a flat file rather than a proper per-world `PersistentState`.

## What survives a relog

- **An active journey** does. Progress (which leg, which phase, how far
  through the stop sequence) is checkpointed to
  `config/mtrnav-journey.json` on every phase change and on disconnect, and
  restored a few ticks after rejoining once MTR's data has synced. If a
  station/platform/line referenced in the save no longer exists (you
  rebuilt something), it's abandoned quietly rather than crashing.
- **Your last-used Origin/Destination/Avoid** in the planner do too, in
  `config/mtrnav-planner.properties` -- reopening the MTRNav app pre-fills
  them instead of starting blank.

Both are stored by MTR's own object IDs and re-resolved through
`MTRDataBridge` on load, since the old Java object references don't
survive a relog (MTR rebuilds its client data fresh each session).

## Previous journey shortcut

The MTRNav app's Nearby screen shows a "Previous journey" card (when one is
saved) with the last Origin -> Destination at a glance -- tap it to
re-search that same trip immediately instead of going through the planner
again. It reads the same `mtrnav-planner.properties` file the planner
itself remembers picks in.

## Trip reports

On arrival, the phone shows a trip summary (route, lines used, planned vs.
actual time) with a 1-5 rating row and a **Save Trip Report** button. Once
you've rated 5 trips, an average rating (a plain mean) starts showing on
that screen too -- stored as just a running count/sum in
`config/mtrnav-feedback.properties`, not the individual ratings. Saving
writes a Minecraft-crash-report-styled `.txt` file to a **`MTRNav Trip
Reports`** folder inside your **Documents** folder (not buried in the
game's own directory) -- a fun, readable record of the trip rather than
raw data, with a random witty one-liner up top in the same spirit as an
actual crash report's header jokes.

## Gameplay notes

- Right-click the **Smartphone** item (Misc creative tab) to open the
  phone, or press **P** (rebindable in Controls). Opening/closing the
  phone plays a soft click; arriving at your destination plays a ping.
- The phone never pauses or blurs the game -- it's a translucent overlay,
  not an inventory screen.
- Starting a journey keeps running even with the phone closed: a small
  widget in the bottom-left shows where you're headed (or the next stop
  while riding), the current instruction, and a live countdown alongside
  the fixed departure/arrival clock. Wandering off path triggers an
  automatic recenter/reroute, and a delayed line replaces the destination
  line with a scrolling delay notice.
