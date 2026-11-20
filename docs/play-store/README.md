# Google Play Store

Everything needed to publish Micro Review for AnkiDroid on the Play Store: the listing texts, the privacy policy, the answers to the Play Console forms, and how to generate the screenshots and GIFs.

| File | Content |
|---|---|
| [listing.md](listing.md) | App name, short and full description, release notes, category |
| [privacy-policy.md](privacy-policy.md) | Privacy policy, published on GitHub Pages by `.github/workflows/pages.yml` when it changes on `develop`: `https://<owner>.github.io/<repository>/privacy-policy.html` (the date of its last change is added) |
| this file | Assets, Play Console forms, checklist |

## Generating the assets

The Focus build has no store asset generator: the lock screen, notification and widget features it captured were removed in the Aero redesign, along with `scripts/store-assets.ps1` and `StoreAssetsSetup`. The theme screenshots come from `DesignPreviewTest` (`.github/workflows/design-preview.yml`).

## Listing assets

| Asset | Play Store requirement | Source |
|---|---|---|
| App icon | 512 × 512 PNG, 32-bit, ≤ 1 MB | `icon-512.png` |
| Feature graphic | 1024 × 500 PNG or JPEG, required | [play_store_banner.png](play_store_banner.png), made by hand in GIMP ([play_store_banner.xcf](play_store_banner.xcf)): the icon, the app name and a tagline on the icon's gradient. Export it without an alpha channel (the Play Console refuses transparency) |
| Phone screenshots | 2 to 8, PNG or JPEG, 320 to 3840 px, long side ≤ 2 × short side | `screenshots/` |
| Video | Optional, YouTube URL | From `raw/*.mp4` |

## Play Console forms

### Data safety

- Does the app collect or share user data? **No.** The app has no internet permission; cards and reviews stay in AnkiDroid, settings in the app's private storage. "Report an issue" only opens a pre-filled GitHub form in the browser, sent by the user under GitHub's policy: this is not collection by the app.
- Data encrypted in transit / deletion request: not applicable (no data collected).

### App content

- Privacy policy: `https://<owner>.github.io/<repository>/privacy-policy.html` (GitHub Pages, from [privacy-policy.md](privacy-policy.md)).
- Ads: **no**. App access: **all functionality available without special access** (no account), but AnkiDroid is needed: say so in the instructions for reviewers.
- Content rating: questionnaire, category "Reference, news or educational"; no violence, no user-generated content shared, no purchases → rated for everyone.
- Target audience: 13 and over (not designed for children, to avoid the Families policy requirements).
- News app: no. Health app: no. Government app: no. Financial features: none.

### Permissions to justify if a reviewer asks

| Permission | Why |
|---|---|
| `com.ichi2.anki.permission.READ_WRITE_DATABASE` | Read the decks and cards, record the answers (AnkiDroid's API) |
| `POST_NOTIFICATIONS` | The card notification, the scheduled review reminders |
| `RECEIVE_BOOT_COMPLETED` | Post the card again and set the reminders again after a restart |
| `SYSTEM_ALERT_WINDOW` | Optional "Open after unlock": start the review screen from the lock screen wallpaper (a background activity start) |
| `SET_WALLPAPER` | Optional card picture on the lock screen, where the live wallpaper can't be set on the lock screen alone (lock screen only, never the home screen) |
| Live wallpaper service | The card on the lock screen |

## Checklist before the first publication

These are not handled by the script or the repository yet:

- [x] **Target API level**: the app targets API 36. The Play Store raises the required level each year (API 35 since August 2025): check the current requirement in the Play Console before each release.
- [x] **Release build**: the Play Store needs an Android App Bundle signed with an upload key, with Play App Signing (Google keeps the app signing key). Play releases use the same versions as GitHub: the final stage of `release.yml` attaches the signed AAB to the GitHub release, uploaded to the Play Console by hand (release candidates stay on GitHub only, no Google credentials in GitHub). See [Signing the release build](#signing-the-release-build).

## Signing the release build

The upload key is a keystore (`.jks`) kept outside the repository, never committed, and backed up with its password. Generate it once with `keytool -genkeypair -v -keystore <path>.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000` (the JDK's `keytool`), or in Android Studio › Build › Generate Signed App Bundle.

The release build reads it from Gradle properties; without them, it is unsigned:

| Property | Content |
|---|---|
| `microreviewUploadStoreFile` | Path of the `.jks` file |
| `microreviewUploadStorePassword` | Its password |
| `microreviewUploadKeyAlias` | Key alias, `upload` by default |
| `microreviewUploadKeyPassword` | Key password, the store password by default |

- **Locally**: put them in `~/.gradle/gradle.properties` (outside the repository, seen by every worktree), then `./gradlew bundleRelease`: `app/build/outputs/bundle/release/app-release.aab`.
- **In CI** (`release.yml`, final stage): repository secrets `UPLOAD_KEYSTORE_BASE64` (the `.jks` file in base64: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("<path>.jks"))` in PowerShell) and `UPLOAD_KEYSTORE_PASSWORD`. Without them the release goes on, with a warning and no bundle.

The release build is shrunk and optimized by **R8** (`isMinifyEnabled`, `isShrinkResources`; rules in `app/proguard-rules.pro`). The device tests run on the debug build, so they don't cover it: before a final release, install the release build on an emulator and go through the main screen, Settings, a notification answer and the hourly worker. The AAB carries R8's mapping file, which the Play Console uses to deobfuscate crash reports; `release.yml` also attaches it to the GitHub release (`…-mapping.txt`).
- [x] **Application ID**: `com.orlikalabs.microreview`, on the developer's own domain, as the AnkiDroid team asked (an ID under `com.ankidroid` would suggest an app maintained by them; they agreed to the app name). The Kotlin package is the same. It can't be changed once published. The listing must not suggest an official AnkiDroid app: no AnkiDroid logo, and the disclaimer at the end of the description.
- [x] **Developer account** created. A personal account created after November 2023 must run a closed test with at least 12 testers for 14 days before applying for production (check the current rule in the Play Console).
- [x] Feature graphic made, screenshots generated.
- [x] Privacy policy published (GitHub Pages).
- [ ] Data safety and App content forms filled.
