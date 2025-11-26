# NClientV3

[![Github](https://img.shields.io/github/v/release/maxwai/NClientV3.svg?logo=github)](https://github.com/maxwai/NClientV3/releases/latest)

An unofficial NHentai Android Client. This is a fork of the original Project by [@Dar9586](https://github.com/Dar9586) found [here](https://github.com/Dar9586/NClientV2)

This app works for devices from API 26 (Android 8) and above.

For Devices Running Android 8 (SDK 26 and 27) there is a separate APK with `pre28` in the name. Support for this Version of the app will be reduced.

Releases: <https://github.com/maxwai/NClientV3/releases>

## Migrate from original NClientV2 to NClientV3

Unfortunately, due to Android security features, it is not possible to create an APK that will update the original App since the same signature will not be possible. For that reason a migration is necessary.

1. If not already done, move your download folder to outside the protected folders in `Settings -> Folder location`
   1. There are a lot of tutorials online on how to access `/storage/emulated/0/Android/data`. For example here from [medium.com](https://medium.com/@naira-nicol/how-to-access-android-data-folder-c0227e0a42ef)
   2. Be sure to not move it to `/storage/emulated/0/Android/data` but to a folder that your normal file app can see it. (Notice: `/storage/emulated/0/nclient` would work for example for a new path)
2. Rename the subfolder in the path you set from `NClientV2` to `NClientV3`
3. Export your data in `Settings -> Export data`
   1. IMPORTANT: wait for the pop up message saying `Export finished`
4. Open NClientV3
5. Set the download folder in `Settings -> Folder location` to the same path as in NClientV2
6. Import your data in `Settings -> Import data`, choose the zip file you created in Step 3
   1. IMPORTANT: wait for the pop up message saying `Import finished`

## Translate App

You can help translate the Project by going to the Crowdin Project [here](https://crowdin.com/project/nclientv3/invite?h=33e3f83681ebaea1bf037ed157d2ea272410538).

If your desired language is missing, write an issue, the language will be added.

## API Features

- Browse main page
- Search by query or tags
- Include or exclude tags
- Blur or hide excluded tags
- Download manga
- Favorite galleries
- Enable PIN to access the app

## Custom feature

- Share galleries
- Open in browser
- Bookmark

## App Screen

|                                                                Main page                                                                 |                                                                Lateral menu                                                                 |
|:----------------------------------------------------------------------------------------------------------------------------------------:|:-------------------------------------------------------------------------------------------------------------------------------------------:|
| ![Main page](https://raw.githubusercontent.com/maxwai/NClientV3/master/fastlane/metadata/android/en-US/images/phoneScreenshots/img1.png) | ![Lateral menu](https://raw.githubusercontent.com/maxwai/NClientV3/master/fastlane/metadata/android/en-US/images/phoneScreenshots/img2.png) |
|                                                                  Search                                                                  |                                                                Random manga                                                                 |
|  ![Search](https://raw.githubusercontent.com/maxwai/NClientV3/master/fastlane/metadata/android/en-US/images/phoneScreenshots/img3.png)   | ![Random manga](https://raw.githubusercontent.com/maxwai/NClientV3/master/fastlane/metadata/android/en-US/images/phoneScreenshots/img4.jpg) |

## Contributors

- [shirokun20](https://github.com/shirokun20) for the initial Bug fixes
- [w0x8m](https://github.com/w0x8m) for the new language picker & Chinese Simplified translation
- [ananas7](https://github.com/ananas7) for Arabic translation
- [raymi7066](https://github.com/raymi7066) for Chinese Simplified translation

## Contributors of original Project

- [Still34](https://github.com/Still34) for code cleanup & Traditional Chinese translation
- [TacoTheDank](https://github.com/TacoTheDank) for XML and gradle cleanup
- [hmaltr](https://github.com/hmaltr) for Turkish translation and issue moderation
- [ZerOri](https://github.com/ZerOri) and [linsui](https://github.com/linsui) for Chinese translation
- [herrsunchess](https://github.com/herrsunchess) for German translation
- [eme22](https://github.com/herrsunchess) for Spanish translation
- [velosipedistufa](https://github.com/velosipedistufa) for Russian translation
- [bottomtextboy](https://github.com/bottomtextboy) for Arabic translation
- [MaticBabnik](https://github.com/MaticBabnik) for bug fixes
- [DontPayAttention](https://github.com/DontPayAttention) for French translation
- [kuragehimekurara1](https://github.com/kuragehimekurara1) for Japanese translation
- [chayleaf](https://github.com/chayleaf) for Cloudflare bypass
- [Atmosphelen](https://github.com/Atmosphelen) for Ukrainian translation

## Libraries

- OKHttp ([License](https://github.com/square/okhttp/blob/master/LICENSE.txt))
- PersistentCookieJar ([License](https://github.com/franmontiel/PersistentCookieJar/blob/master/LICENSE.txt))
- JSoup ([License](https://github.com/jhy/jsoup/blob/master/LICENSE))
- Glide ([License](https://github.com/bumptech/glide/blob/master/LICENSE))
- AmbilWarna ([License](https://github.com/yukuku/ambilwarna/blob/master/LICENSE))
- AndroidFastScroll ([License](https://github.com/zhanghai/AndroidFastScroll/blob/master/LICENSE))

## License

```text
   Copyright 2024 maxwai

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```
