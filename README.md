<p align="center"><img src="docs/logo.png" width="128" alt="TeleTV"></p>

# TeleTV

**English** · [Português](README.pt-BR.md)

An unofficial Telegram client for TVs. It opens your chats, groups and channels and plays their videos on the big screen, all with the remote control. Built for Fire TV and Android TV.

The official Telegram app for Android runs on a TV, but in a phone layout designed for touch. TeleTV covers the use case that is left: sitting on the couch and watching what was posted in your groups.

## Features

- **Chats**: groups, channels and private chats, with tabs for your folders and the archive, and search by name.
- **Videos**: a grid with thumbnail, duration, size and date. Switch between videos and video files sent as documents (MKV, MP4 and so on).
- **Filters**: by date, file size, duration, downloaded only, text search, and sorting by date, size or duration.
- **Streaming**: playback starts while the file is still downloading and resumes where you left off.
- **Remote-friendly player**: left and right skip 10 s (hold to go faster), Menu toggles zoom, and subtitles, audio track and speed are in the controls. Back once hides the controls; Back twice leaves the video.
- **Storage under control**: a size limit for downloads and a time limit for videos you have not opened. The oldest ones go first.
- **Auto-download**: pick, chat by chat, which ones should have their most recent videos downloaded automatically (while the app is open and within the storage limit).
- **Set up from your phone**: the sign-in screen shows a QR code that opens a page served by the TV itself on your home network. Type your number, code and password there with the phone keyboard, or follow the guided steps to use your own Telegram API key.
- **Setup guide**: on first launch a short guide walks through sign-in, storage, automatic downloads, the PIN and the remote shortcuts. It can be reopened from Settings.
- **PIN**: an optional 4-digit PIN to open the app.
- **Voice**: search fields accept the TV keyboard's dictation; on devices with speech recognition for apps a "Speak" button appears. In the player, the system's voice commands (pause, resume, skip) work through the media session.
- **Updates**: the app looks for new versions in this repository's releases and installs them in place.
- **Languages**: English and Portuguese, following the device language.

## Install

Download the latest APK from [Releases](../../releases/latest) and install it on your TV.

- **Fire TV**: enable *Settings → My Fire TV → Developer options → Apps from Unknown Sources*, then install with the Downloader app (enter the APK address from the release page) or with `adb install teletv-vX.Y.Z.apk`.
- **Android TV / Google TV**: send the APK with a file manager or use `adb install`.
- **Obtainium**: add this repository's address to [Obtainium](https://github.com/ImranR98/Obtainium) and it will track new releases.

On first launch, sign in by pointing your phone at the QR code (Telegram → Settings → Devices → Link Desktop Device) or by typing your number and the code. To type on your phone instead of the remote, scan the second QR code on that screen; it works while the phone and the TV are on the same network.

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

The APK is written to `app/build/outputs/apk/debug/`. The build bundles TDLib for `armeabi-v7a` only, the Fire TV Stick architecture; 64-bit devices that still run 32-bit apps work too.

Releases are built by GitHub Actions when a `v*` tag is pushed, using the secrets `TG_API_ID`, `TG_API_HASH`, `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.

## Contributing

Issues and pull requests are welcome. Translations live in `app/src/main/res/values-*/strings.xml`: copy `values/strings.xml` to a new `values-<language>` folder and translate it.

## Privacy

TeleTV talks only to Telegram's servers and, to look for updates, to the GitHub API. There is no telemetry and no server of its own. Your session and downloaded videos stay in the app's internal storage.

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
