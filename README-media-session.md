# On Player media session starter

Uploaded on a separate branch without changing main or the existing APK workflow.

Contents:
- OnPlayerMediaService.kt: Media3 MediaSessionService base for Android system media controls.
- integration-notes.txt: required manifest and Gradle snippets and current integration status.

This is not yet a complete player integration and no APK is built from this branch. Existing PlayerScreen still owns its own ExoPlayer; it must be connected via MediaController before background notifications will work end-to-end.
