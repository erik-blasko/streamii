# Streamii

Free, open-source Android player for publicly available TV channels and radio stations.
No ads, no tracking, no accounts.

## Features

- Thousands of free-to-air TV channels and radio stations from public M3U playlists
- Filters by country and category, favorites, channel detail
- Stream availability check (online / offline)
- HLS, DASH and MPEG-TS playback (Media3 ExoPlayer)
- Background playback for radio, picture-in-picture for video
- Lock screen and notification controls
- Live / −30 s rewind, quality picker, sleep timer
- Automatic switch to another source when a stream fails
- Add your own M3U playlists
- Slovak and English UI, light and dark theme

## Download

Get the latest APK from [Releases](../../releases/latest) and install it on your phone
(Android 8.0 or newer). You may need to allow installing apps from unknown sources.

To get updates automatically, add `https://github.com/erik-blasko/streamii` to [Obtainium](https://github.com/ImranR98/Obtainium).

## Content disclaimer

Streamii does not host, provide or distribute any video or audio content.
It only plays streams listed in publicly available third-party playlists
(e.g. [iptv-org/iptv](https://github.com/iptv-org/iptv), [Free-TV/IPTV](https://github.com/Free-TV/IPTV)).
The availability and legality of individual streams are the responsibility of their providers.
If you believe a stream should not be listed, please contact the maintainers of the playlist that contains it.

Channel names, logos and service names (e.g. Pluto TV, Samsung TV Plus) are trademarks of their
respective owners. Streamii is not affiliated with or endorsed by any of them.

## Privacy

Streamii collects no personal data. There are no analytics, no ads and no accounts.
The app only connects to the playlist and stream servers you choose to load.
Settings and favorites are stored only on your device.

## Building

Requirements: Android Studio (or JDK 17+) and the Android SDK.

```
./gradlew :app:assembleDebug
```

Release builds are signed with a key that is not part of this repository.
To build your own signed release, create `keystore.properties` in the project root:

```
storeFile=/path/to/your-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

and run `./gradlew :app:assembleRelease`. Without this file the release build is unsigned.

Tech stack: Kotlin, Jetpack Compose, Material 3, Media3 ExoPlayer, OkHttp, Coil, DataStore.

## License

Streamii is licensed under the [GNU General Public License v3.0](LICENSE).
