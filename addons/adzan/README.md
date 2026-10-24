# Adzan

Plays the adzan on the TV at each of the five prayer times, over whatever is on screen: a small card in the
bottom-right corner with the prayer's name and time, and the adzan audio. Players that respect audio focus
(Netflix, SmartTube, YouTube) pause until it ends. OK or Back stops it. Nothing plays while the TV is in standby.

- Times: Aladhan, one month at a time, for the place and method in `app/src/main/res/values/config.xml`
  (default Jakarta, method 20 = Kemenag RI). Cached, so a short outage changes nothing.
- One exact alarm for the next adzan; no background service.
- Sound: `/sdcard/Adzan/adzan.mp3`, or `/sdcard/Adzan/<subuh|dzuhur|ashar|maghrib|isya>.mp3` for one prayer.
  Without a file it plays a built-in chime.

## Install

```bash
cp -r ../prayer-times/gradle ../prayer-times/gradlew ../prayer-times/build.gradle ../prayer-times/gradle.properties .
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell appops set id.alfian.tv.adzan SYSTEM_ALERT_WINDOW allow
adb shell pm grant id.alfian.tv.adzan android.permission.READ_MEDIA_AUDIO
adb shell am start -n id.alfian.tv.adzan/.MainActivity
```
