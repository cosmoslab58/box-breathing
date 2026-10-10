# Box Breathing

A small native Android app (plain Java, no dependencies) for box breathing and its relatives.

- **The box:** a dot travels the square one side per phase (up = in, top = hold, down = out,
  bottom = hold) and leaves a trail for the round; an inner square swells and shrinks with the breath.
- **Audio timer:** a sound on every second (tick, beep, spoken count, or off), an accented first
  second, and a phase-change cue (pitched chime, spoken "Breathe in / Hold / Breathe out", or off).
  All sounds are synthesized at startup in `Sounds.java`. Optional vibration on each phase change.
- **Configurable:** each of the four phases (0 skips a hold), presets (Box 4·4·4·4, Long box 6·6·6·6,
  4-7-8, Coherent 5·5), session length in minutes (rounded up to whole rounds, or open-ended),
  get-ready countdown, count up or down, volume.

The clock is anchored to the session start, so ticks never drift. The screen stays on during a
session; leaving the app pauses it (there is no background service).

## Safety

Box Breathing is a breathing timer for general relaxation. It is not medical advice, diagnosis or
treatment. Holding your breath can cause dizziness or fainting: breathe gently, stop if you feel
unwell, and never use it while driving, operating machinery or in water. If you are pregnant or
have a heart, lung, blood pressure, seizure or other medical condition, ask a doctor first. You use
it at your own risk; it is provided as is, without warranty, and the author accepts no liability for
any harm arising from its use (see also the [license](LICENSE)).

## Install

Download `box-breathing.apk` from the [latest release](https://github.com/cosmoslab58/box-breathing/releases/latest)
and open it on your phone (Android 10 or newer). Android will ask you to allow installs from your
browser or file manager the first time.

The APK is signed with this certificate (SHA-256), which every future release will share:

    3b:7e:ed:3c:52:73:15:a5:81:99:59:2d:78:a0:ff:0f:ca:24:1f:3f:4a:5f:c9:7d:48:58:f3:3f:57:39:53:5c

The app needs no permissions beyond vibration, has no network access, and collects nothing.

## Support

Box Breathing is free, with no ads and nothing to buy. It exists because a basic, good box
breathing timer should be free for everyone. If it helps you, a
[donation via PayPal](https://www.paypal.com/donate/?business=nathancrosty%40gmail.com&item_name=Box+Breathing&currency_code=USD) is welcome but never expected.

## Build

    ./build.sh      # signed release APK -> dist/box-breathing.apk

To release: bump `versionCode` and `versionName` in `app/build.gradle`, add
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, commit, tag `vX.Y.Z` (signed),
build, and attach the APK to a GitHub release. F-Droid picks up new tags on its own.

Builds in Docker (Android SDK 35 + JDK 17). The signing key is `~/.secrets/box-breathing.jks` with
its password in `~/.secrets/box-breathing.env`, both created on first build. Keep them, or later
builds won't install over an earlier one.

## License

MIT
