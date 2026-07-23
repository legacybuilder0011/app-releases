# TapCopy Android MVP

TapCopy lets a user copy visible text from TikTok, Instagram, Pinterest, YouTube, browsers, and many other Android apps.

## User flow

1. Open TapCopy and enable its accessibility service.
2. A movable purple `T` button appears over other apps.
3. Pause a video when the caption you want is visible.
4. Tap the `T` button.
5. Each block of text is highlighted with a circle. Tap the blocks you want to
   tick them, then tap **Copy selected** — or tap **Copy all** for everything.
6. TapCopy closes and returns you to the previous app.

### Long captions that scroll (Stitch mode)

A single capture can only read the text that is visible on screen, so a caption
that is collapsed behind "…more" or runs longer than one screen needs Stitch
mode:

1. Tap the floating `T`, then tap **Stitch long text**.
2. Tap the caption block (or **Add whole screen**) to add the visible part.
3. TapCopy returns you to the app. Scroll down a little, tap `T` again, and add
   the next part. Overlapping lines between captures are removed automatically.
4. Repeat until you have the whole caption, then tap **Finish & copy**.

Tip: expanding the caption with "…more" first often means one normal capture is
enough.

## Privacy design

- OCR runs locally with the bundled ML Kit model.
- No internet permission is requested.
- TapCopy does not retrieve the active app's accessibility node tree.
- A screenshot is taken only after the user taps the floating button.
- The temporary screenshot is deleted when the selection screen closes.
- TapCopy does not automate clicks, type messages, read passwords, or operate autonomously.

## Requirements

- Android 11 or newer, because `AccessibilityService.takeScreenshot()` was added in API 30.
- Android Studio with JDK 17.
- Android SDK 36 installed.

## Build

1. Run `setup-wrapper.bat` on Windows or `./setup-wrapper.sh` on macOS/Linux. This downloads Gradle's official 8.13 wrapper bootstrap JAR.
2. Open this folder in Android Studio.
3. Allow Gradle sync to finish.
4. Run the `app` configuration on a physical Android device.
5. Open TapCopy, press **Enable TapCopy**, select **TapCopy screen text service**, and approve it.

The project targets Android 16 / API 36 and uses Android Gradle Plugin 8.11.1 with Gradle 8.13.

## Important limitations

- Apps or screens using Android's secure-window protection cannot be captured.
- Very small, animated, blurred, or low-contrast text may not be recognized. Pause the video first.
- The included OCR model recognizes Latin-script text. Add the relevant ML Kit model dependencies for Chinese, Devanagari, Japanese, or Korean.
- iOS does not allow an equivalent always-on floating overlay across unrelated apps. An iPhone version should use a Share Extension or screenshot import flow instead.

## Google Play publication

The Accessibility API is central to this feature, so the Play Console declaration, in-app prominent disclosure, privacy policy, store listing, and review video must accurately explain the feature. Do not add background monitoring, autonomous actions, hidden collection, or unrelated automation.
