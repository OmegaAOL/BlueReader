# BlueReader

BlueReader is an unofficial, open source Android client for Bluesky. It is forked from [RedReader v1.22](https://github.com/QuantumBadger/RedReader/tree/v1.22), the last version to support Android 4.x. In addition to supporting BLuesky instead of Reddit, it has various improvements over the base application.

## Improvements over RedReader

* Bluesky login support 
* Greatly improved theming system
** Separates accent color and themes, allowing accents to be applied to light, dim, dark, and black themes
** Themes the Settings UI and some dialogs according to the default selected theme
** Adds status and nav bar theming to Android 4.4 KitKat (previously only worked on Android 5+)
** Allows themes to now be applied without restart
* Lower the minimum required Android version from 4.1 (Jelly Bean) to 4.0 (Ice Cream Sandwich)
* Increase the target Android version from Android 12 to Android 14
* Improve shipped icons for Material You adaptive design
* Add TLS 1.2 support to the application on Android 4.x
* Change dialogs from using forced Material Design to using the system style (Holo on Android 4.x, Material on Android 5+, Samsung's on TouchWiz/OneUI, etc...)
* And much more
  
# Features

* Free and open source, with no ads or tracking
* Lightweight and fast
* Swipe posts left and right to perform customizable actions, such as
    upvote/downvote, or save/hide
* Advanced cache management - automatically stores past versions of posts and
    comments
* Support for multiple accounts
* Two-column tablet mode (can be used on your phone, if it’s big enough)
* Image and comment precaching (optional: always, never, or Wi-Fi only)
* Built-in image viewer, and GIF/video player
* Multiple themes, including night mode, and ultra black for AMOLED displays
* Support for Android 4.0+
* Translations for multiple languages
* Accessibility features and optimization for screen reader use


# Downloading

Currently if you want to get BlueReader you will have to download from 
releases, GitHub Actions, or build from source.


# Pull Requests

Please see the contribution guidelines in
 [CONTRIBUTING.md](CONTRIBUTING.md). 


# Building

BlueReader is built using Gradle. On Linux, simply run:

    ./gradlew installDebug


# License

BlueReader, like RedReader, is licensed under the GPLv3. A copy of 
the license is included in [LICENSE.txt](LICENSE.txt).


Thanks
------

A full list of contributors is included in the 
[changelog](src/main/assets/changelog.txt).
