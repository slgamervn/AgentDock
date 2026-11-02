# AgentDock APK release procedure

The Signed Android APK workflow publishes signed APKs to GitHub Releases. It must be dispatched from the main branch.

## Before the first public release

Configure these repository Actions secrets in **Settings → Secrets and variables → Actions**:

- AGENTDOCK_KEYSTORE_BASE64: Base64-encoded JKS contents.
- AGENTDOCK_KEYSTORE_PASSWORD: JKS password.
- AGENTDOCK_KEY_ALIAS: Private-key alias.
- AGENTDOCK_KEY_PASSWORD: Password for that private-key entry.

The workflow checks the JKS, alias and private-key password before building. Do not paste signing values into issues, pull requests, workflow inputs or chat. Do not commit the JKS or private key.

## Dispatch

1. Open Actions → Signed Android APK → Run workflow.
2. Select the main branch.
3. Enter a unique version_name such as 0.2.0.
4. Choose preview for a pre-release or stable for a normal release.
5. Enter user-facing release notes. Include the important changes and compatibility notes.
6. Run the workflow and verify every step succeeds.

The workflow uses its monotonically increasing GitHub Actions run_number as Android versionCode, and the supplied value as versionName. Do not rerun a release with the same channel and version name: the workflow refuses to reuse an existing tag.

## Published assets

- agentdock-preview-<version>.apk or agentdock-stable-<version>.apk
- Matching .sha256 checksum file

Tags use preview-v<version> or stable-v<version>. Preview releases are marked as pre-releases. The APK is verified with apksigner before the release is created. The same APK and checksum are also retained as a GitHub Actions artifact for 30 days.

After downloading both assets, verify the APK checksum with:

    sha256sum -c agentdock-<channel>-<version>.apk.sha256

The Android Package Installer still verifies that an update is signed by the same app-signing identity as the installed app. Never change applicationId or replace the signing certificate for an in-place update.

## Current boundary

Publishing a GitHub Release does not mean the in-app updater is production-ready. The signed update-manifest key, manifest publishing, download/redirect validation, installed-certificate comparison, user-confirmed installation and update-over-install tests are separate requirements tracked in the delivery roadmap. Do not advertise automatic updates until those checks are implemented and verified.
