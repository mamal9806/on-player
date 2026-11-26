# QEMU Glass (Android UI MVP)

A Liquid Glass Compose app that builds starter QEMU launch command lines for x86_64 or aarch64 guest configurations.

## Important limitation
This app **does not embed or run QEMU**. It is a command builder and VM profile front-end. Execute generated commands in a supported QEMU installation (desktop Linux/Windows environment, or a compatible Android terminal setup). QEMU hardware acceleration options vary by host; KVM is Linux-host specific.

## Build APK
Open this folder in Android Studio (Jellyfish+), allow Gradle sync, then Build > Build APK. The included workflow can also build APKs after this project is pushed to a GitHub repository.
