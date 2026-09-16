# OpenGuitarTuner

A free guitar tuner for Android phones. Play a string and the app shows which string it hears and whether to tune
up or down. There are no ads, no account and nothing to pay for.

## What it does

- Shows your guitar's headstock with all six strings. Play any string and the app picks out which one it is and
  tells you how far off it is.
- Tap a string to tune just that one. Press and hold a string to hear the note it should be.
- Comes with 20+ tunings ready to use: Standard, Drop D, DADGAD, Open G, half step down and more.
- Lets you save as many of your own tunings as you like.
- Has a chromatic mode that tunes to any note.
- Listens through the phone's microphone, or through a USB audio interface if you want to plug your guitar in.
- Offers a dark or light theme, sharps or flats, and a reference pitch other than 440 Hz if you need one.
- Works offline. The app has no internet access, so the sound it hears never leaves your phone.

## Will it work on my phone?

- **Android 16 or newer is required.** Older Android versions can't install it.
- It runs on phones and tablets.
- It's made for **six-string guitars**. Other instruments can use chromatic mode, which just shows the nearest note.
- Any phone microphone will do. For USB, use an audio interface that works with Android without installing
  drivers, plugged into the phone's USB-C port.

## Install

The app isn't in an app store. You install it from a file on this page:

1. On your phone, open the [latest release](https://github.com/ntainy/open-guitar-tuner/releases/latest).
2. Under **Assets**, tap the `OpenGuitarTuner-….apk` file to download it.
3. Open the downloaded file. If Android asks, allow your browser to install apps, then tap **Install**.
4. Open OpenGuitarTuner and allow microphone access.

To update, download the newer APK and install it the same way. Your tunings and settings are kept.

## Using a USB interface

Plug the interface into your phone. The app switches to it automatically. To choose an input yourself, tap the input
icon at the top of the Tune screen.

If the tuner only reacts when you play hard, open the same menu, play a string softly, and raise **Sensitivity**
until the level bar goes past the mark. The app remembers the setting separately for each input.

## What it's been tested with

This is a beta, version 0.2. So far it has been tried with:

| Device | Input | Result |
|---|---|---|
| Samsung Galaxy S23 Ultra (Android 16) | Built-in microphone, acoustic guitar | Works well and is in regular use |
| NUX Mighty Plug Pro (MP-3) | USB | Works well once Sensitivity is raised, because the MP-3's dry mode sends a quiet signal |

Other phones and interfaces should work too, but nobody has tried them yet. If something doesn't work for you,
please [open an issue](https://github.com/ntainy/open-guitar-tuner/issues) and say which phone and input you used.
This is a pet project I work on in my spare time, so I read every issue but can't promise a quick reply or fix.

## Licence

OpenGuitarTuner is free and open source under the [GNU GPL v3](LICENSE). It includes the Bricolage Grotesque font
under the SIL Open Font License ([`art/OFL-BricolageGrotesque.txt`](art/OFL-BricolageGrotesque.txt)). Notes for
developers are in [`docs/`](docs).
