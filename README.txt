On Player - source replacement pack (not a complete Android project)

Replace these files in your existing project:
- app/src/main/java/com/onplayer/app/MainActivity.kt
- app/src/main/java/com/onplayer/app/data/MediaRepository.kt
- add app/src/main/res/drawable/app_icon.xml

Do NOT replace your Gradle files or .github/workflows/android.yml; this pack does not include them.

Changes in this pack:
- Recent files section on the home screen with timestamp and file-type thumbnails.
- Image thumbnails and video frame previews (frame sampled around 1 second).
- Light mode changes the actual app colors; Dark/AMOLED are also supported.
- Reworked category and file rows, settings, navigation, and icon.
- Documents/archives/APK/other files classified by extensions and opened using Android's installed handlers.

Important limitations:
- Documents/archives visibility is still limited by Android MediaStore permissions and indexing. A full SAF folder browser is a separate addition.
- Thumbnail decoding is asynchronous but has not been tested on a physical Android device in this pack.
- No Gradle/workflow file was changed or included. No APK was built here.
