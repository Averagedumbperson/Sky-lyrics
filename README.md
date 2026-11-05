# Sky Lyrics (Fabric, Minecraft 26.2)

Synced lyrics float in the sky while you play, with a "now playing" pill at the top and a settings menu (press **K**).
Inspired by the Valence mod video; this is an original implementation.

**No setup:** put the jar in `mods`, play a song in the Spotify desktop app, done. No account, no login, no API keys.

Client-only: works in singleplayer and on any server/world (Essential included). Each person sees their own lyrics.

## Install
1. Get the jar (see "Getting the jar" below).
2. Put `skylyrics-0.2.0.jar` and **Fabric API for 26.2** in your `mods` folder (Fabric Loader 0.19.3+, Java 25).
3. Start Minecraft, join a world, and play a song in the **Spotify desktop app**. Lyrics appear.

## What counts as "no setup"
Lyrics are read from what your computer says is playing:
- **Windows 10/11:** Spotify desktop app (free or Premium). Also works with other players that show in the Windows
  media overlay (Apple Music, browser players).
- **macOS:** Spotify desktop app. macOS may ask once to let Minecraft/Java control Spotify; click OK.
- **Linux:** needs `playerctl` installed (`sudo apt install playerctl`).
- **Not covered:** Spotify on your phone or the Spotify web player. For those, press K and set
  **Source: Spotify account** (needs Spotify Premium and a free Spotify developer app; see the end).

## Getting the jar
You can't just download a ready jar from here, it has to be built once. Pick one:

**A. In the cloud, nothing to install (easiest)**
1. Make a free account at github.com, then **New repository** (any name, private is fine).
2. Unzip this project and upload everything into the repo (drag the files in on the "uploading an existing file"
   page). Make sure the hidden `.github` folder goes up too. If it won't, use *Add file > Create new file*, name it
   `.github/workflows/build.yml`, and paste in the contents of that file.
3. Open the repo's **Actions** tab. The "Build mod" workflow runs on its own (or click *Run workflow*).
4. When it turns green, open the run and download the **skylyrics-jar** file at the bottom. Unzip it: that is your mod.

**B. On your PC**
1. Install JDK 25 and IntelliJ IDEA. Open this folder in IntelliJ (it fetches Gradle 9.5.1 itself).
2. In the Gradle panel run `Tasks > build`. The jar is `build/libs/skylyrics-0.2.0.jar`.
   Test inside the IDE with the `runClient` task.

If Gradle complains about a version, use the ones the Fabric template generator shows for 26.2
(https://fabricmc.net/develop/template) and edit `gradle.properties`.

## Settings (K)
Sky lyrics on/off, now-playing pill on/off, Where (Floating follows your view / Fixed stays where it appeared),
Show (word by word / whole line), Colour, Size, timing (earlier/later), Source.

If lyrics are early or late, use "Lyrics earlier/later".

## Notes
- Lyrics come from LRCLIB (free community database). Not every song has synced lyrics.
- LRCLIB only has line timing, so word-by-word is an estimate spread across each line.
- If the mod starts while a song is already playing and your player doesn't report its position, timing is counted
  from when the mod first saw the song; it corrects itself on the next song.
- Windows uses a small hidden PowerShell helper that reads the media overlay once a second. It closes with the game.
- Text uses Minecraft's built-in font.
- If anything fails, send `logs/latest.log` and the Gradle/Actions output.

## Optional: Spotify account mode (advanced)
Only needed for phone/web-player listening. The account that owns the developer app needs Premium, and one app
allows up to 5 users.
1. https://developer.spotify.com/dashboard > Create app > tick **Web API**.
2. Redirect URI: `http://127.0.0.1:8888/callback` (exactly).
3. Put the app's Client ID into `.minecraft/config/skylyrics.json` as `"spotifyClientId"`.
4. Friends: add their Spotify email under *User Management* in your app; they use the same Client ID.
5. In game press **K**, set **Source: Spotify account**, click **Connect Spotify**, approve in the browser.
`config/skylyrics-spotify.json` holds your login token; don't share it. A 403 means your account isn't added to the app.
