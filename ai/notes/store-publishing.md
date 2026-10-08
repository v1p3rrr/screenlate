# Store publishing and developer verification

Findings of 2026-10-09 (policy pages as of that date). Nothing decided.

## Google Play

- Personal accounts made after 2023-11-13 run a closed test with 12 testers for 14 days before production.
- New apps and updates must target API 36 from 2026-08-31 (we target 37). AAB upload.
- What a Play build would have to change:
  - the self-updater and `REQUEST_INSTALL_PACKAGES` / `UPDATE_PACKAGES_WITHOUT_USER_ACTION` (self-updates are banned);
  - `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (high risk; open the battery settings instead);
  - an in-app accessibility disclosure with consent (request 48) and the accessibility declaration with a video;
  - the `specialUse` keep-alive service needs its own declaration;
  - a privacy policy URL and the Data safety form (screenshots to cloud recognition, text to translators).
- Main risk: the Device and Network Abuse policy bans apps that use a service or API against its terms. The Lens request
  uses Chrome's key on Google's own endpoint; Bing, Edge and gtx are against their terms too. Review rarely inspects
  traffic, but removal can come with any update or complaint, and a terminated account bars new ones.

## Android developer verification (outside Play too)

- Enforced since 2026-09-30 in four countries; global on certified devices in 2027.
- Paths: full distribution (identity check, package name and signing key registered in the Android Developer Console, no
  content review like Play's); limited distribution (no identity check, up to 20 devices by invitation); unregistered
  apps through ADB or the "advanced flow" (one-time developer-options setting with a 24 h wait, then installs and
  updates with a warning).
- F-Droid signs with its own key and opposes the program; its main repo would also refuse ML Kit (non-free).
  IzzyOnDroid ships the developer's own APKs, so it depends on the developer being verified.
