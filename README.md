# On Player Android

A Kotlin + Jetpack Compose media browser/player starter project using MediaStore and Media3.

## Open/build
Open this folder in Android Studio (Giraffe or newer), allow Gradle sync, then Run on an Android 8+ device.

## Permissions and file access
The app requests Android media permissions at launch. MediaStore is used for audio, video, images and indexed non-media files. This uses scoped-storage APIs and does not request unrestricted file access.

## Implemented
- Dark, neon purple media-dashboard UI with Home, Library, central Player, Favorites/Playlist and Settings navigation.
- MediaStore scan for images, videos, audio and indexed document/archive/APK/other files; live category counts.
- File list and global search by name/type, recent ordering, favorite toggle, rescan.
- Media3 playback view with seek controls, play/pause, repeat and playback-speed controls.
- Image viewing, rotation, pinch zoom, and share.

## Notes
- Android document/file indexing depends on what the system MediaStore indexes and permissions granted. SAF folder browsing and write/delete/rename actions are not included in this first working baseline.
- Favorites and theme selection currently live in ViewModel state and reset after process restart; persistent storage is a follow-up integration.
- This project is prepared for Gradle sync in Android Studio; a Gradle wrapper is not included in this archive.

## Media format coverage (device-dependent)

On Player uses Android MediaStore and Media3/ExoPlayer. Common audio formats include MP3, AAC/M4A, WAV, FLAC, OGG/Vorbis, Opus, AMR and MIDI where the device's Android codecs support them. Common video containers/codecs include MP4/H.264, 3GP, WebM/VP8/VP9, MKV with supported codecs, and HEVC/H.265 on compatible devices. Codec/container combinations vary by Android version and device; no player can guarantee every malformed, DRM-protected, or unsupported codec file.

The app's core sections are Music/Audio, Video, Photos, and Files. Documents and archives are opened with Android's compatible external handlers where available; archive extraction is not yet built into this starter project.
