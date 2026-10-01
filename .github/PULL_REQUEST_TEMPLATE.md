## Description

<!-- Meaningful description here -->

## APK testing

For each change in the pull request, a workflow is run, which produces a debug artifact APK. Go to Checks -> DRS Smart Keyboard CI -> `drs-debug-apk` and download the APK. It installs under the `com.drs.smartkeyboard.debug` namespace and will not mess with your main installation.

## Checklist

- [ ] The change is tested on a real device (install + keyboard + affected screens).
- [ ] No fake/mock data, buttons, or diagnostics were introduced.
- [ ] All user-facing strings are added in both Arabic and English.
