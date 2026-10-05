<p align="center"><img src="docs/logo.png" width="128" alt="TeleTV"></p>

# TeleTV

**English** · [Português](README.pt-BR.md)

An unofficial Telegram client for TVs. It opens your chats, groups and channels and plays their videos on the big screen, all with the remote control. Built for Fire TV and Android TV.

The official Telegram app for Android runs on a TV, but in a phone layout designed for touch. TeleTV covers the use case that is left: sitting on the couch and watching what was posted in your groups. It is a viewer: it does not send messages.

## What it does

### Home

- **Continue watching**: a row at the top with the videos you left halfway, each with a progress bar.
- **Chats by tab**: All, Favorites, one tab per Telegram folder, and Archived. Search chats by name.
- **Library**: watch history, videos in progress and favorite videos in one place.
- **Chat menu** (hold OK): add to favorites, turn auto-download on or off.

### Inside a chat

- **Video grid** with thumbnail, duration, size, date, download state and how much you have watched.
- **Filters**: date, file size, duration, downloaded only, text search, and sorting by date, size or duration. With a filter on, the app keeps fetching older messages until the screen fills up.
- **Videos or video files**: switch to files sent as documents (MKV, MP4 and so on).
- **Video menu** (hold OK): favorite, delete the download, remove from history.

### Player

- **Streaming**: playback starts while the file is still downloading and resumes where you left off.
- **Left / right** skip 10 seconds; holding the key goes faster.
- **Menu** toggles zoom; **OK** opens the controls with subtitles, audio track and speed.
- **Back** once hides the controls; **Back** twice leaves the video.

### Storage and downloads

- **Size limit** for downloaded videos (0.5 to 8 GB) and a **time limit** for videos you have not opened. The oldest ones go first, and space is freed before each new video starts.
- **Auto-download**: pick, chat by chat, which groups and channels should have their newest videos downloaded by themselves. It runs only while the app is open and never exceeds the size limit.

### Setup and settings

- **First-run guide**: sign-in, storage, automatic downloads, PIN and remote shortcuts, in seven short steps.
- **Sign in three ways**: scan a QR code with the Telegram app, type on the remote, or scan a second QR code and type everything on your phone.
- **Your own Telegram API key** (optional): the phone page walks you through creating one at my.telegram.org.
- **PIN**: an optional 4-digit PIN asked every time the app opens.
- **Updates**: the app checks this repository's releases and installs new versions in place.
- **Languages**: English and Portuguese, following the device language.
- **Voice**: search fields accept the TV keyboard's dictation; on devices with speech recognition for apps a "Speak" button appears; the player answers the system's media voice commands.

## Install

Download the latest APK from [Releases](../../releases/latest) and install it on your TV.

- **Fire TV**: enable *Settings → My Fire TV → Developer options → Apps from Unknown Sources*, then install with the Downloader app (enter the APK address from the release page) or with `adb install teletv-vX.Y.Z.apk`.
- **Android TV / Google TV**: send the APK with a file manager or use `adb install`.
- **Obtainium**: add this repository's address to [Obtainium](https://github.com/ImranR98/Obtainium) and it will track new releases.

For in-app updates, allow TeleTV to install apps when it asks (on Fire TV: *Developer options → Install unknown apps → TeleTV*).

## Build

Requirements: JDK 17 and the Android SDK (platform 34).

1. Create your credentials at <https://my.telegram.org> → *API development tools*.
2. Put them in `local.properties`, which is not tracked by git:

   ```properties
   sdk.dir=/path/to/android-sdk
   tg.apiId=123456
   tg.apiHash=0123456789abcdef0123456789abcdef
   ```

3. Build:

   ```bash
   ./gradlew assembleDebug
   ```

The APK is written to `app/build/outputs/apk/debug/`. A build without credentials still works: on first launch it asks for an API key through the phone setup page.

The build bundles TDLib for `armeabi-v7a` only, the Fire TV Stick architecture; 64-bit devices that still run 32-bit apps work too.

Releases are built by GitHub Actions when a `v*` tag is pushed, using the secrets `TG_API_ID`, `TG_API_HASH`, `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.

## How it is built

Kotlin with plain Android views, no UI framework. [TDLib](https://github.com/tdlib/td) handles the Telegram protocol and file downloads; [Media3 ExoPlayer](https://github.com/androidx/media) plays the file while TDLib is still downloading it, through a small data source that asks TDLib for the byte range the player needs. History, favorites and settings are stored on the device.

## Contributing

Issues and pull requests are welcome. Translations live in `app/src/main/res/values-*/strings.xml`: copy `values/strings.xml` to a new `values-<language>` folder and translate it.

## Privacy

TeleTV talks only to Telegram's servers and, to look for updates, to the GitHub API. There is no telemetry and no server of its own. Your session, downloaded videos, watch history and favorites stay in the app's internal storage.

The PIN protects the interface from whoever picks up the remote; it does not encrypt the data.

The phone setup page is plain HTTP inside your local network. It is served only while the sign-in screen is open and its address carries a random code, but avoid using it on networks you do not trust.

## Support

TeleTV is free, open source and has no ads. If it is useful to you, a Bitcoin donation helps keep the project and its developer going:

```
14XJqVsMfVLpm6s4mX7mHNooihvtdfJq5J
```

In the app, the QR code is under *Settings → Support the project*.

## Disclaimer

This project is not affiliated with or endorsed by Telegram. Use is subject to the [Telegram API terms](https://core.telegram.org/api/terms).

## License

[MIT](LICENSE). Third-party components and their licenses are listed in [THIRD-PARTY-LICENSES.md](THIRD-PARTY-LICENSES.md).
