# ReelGuard — Play Console submission kit

Copy‑paste drafts for the Google Play Console. Adapt anything in **[brackets]**.
Package: `com.reelguard.app` · Contact: `standesv@gmail.com`

---

## 1. Store listing

### 1a. English

**App name** (30 char max)
```
ReelGuard: Block Reels & Shorts
```

**Short description** (80 char max)
```
Block Reels, Shorts & Spotlight. Set time limits or schedules. 100% on-device.
```

**Full description** (4000 char max)
```
Spending too long scrolling Reels, Shorts, Spotlight? ReelGuard helps you take back control of your time — without deleting the apps you use.

ReelGuard detects when a short‑video feed opens and blocks it according to the rules YOU set. Everything runs on your phone: no account, no server, and none of your data ever leaves your device.

WHAT IT BLOCKS
• Instagram Reels
• YouTube Shorts
• TikTok
• Facebook Reels
• Snapchat Spotlight

WAYS TO STAY IN CONTROL
• Time limit — set a daily cap (e.g. 15 minutes). Get a warning at 80%, then Reels are blocked.
• Blocked time slots — block Reels during chosen hours, like night time or work hours.
• Focus Mode — fully block all Reels for a set duration when you need to concentrate.
• Parental Mode — lock the settings behind a PIN so they can't be changed. Works great with Google Family Link.
• Per‑app control — choose exactly which apps ReelGuard watches.
• Messaging exception — keep watching reels friends send you in private messages, if you want.

PRIVACY FIRST
ReelGuard collects no personal data. Your settings and stats stay on your device. The app uses Android's Accessibility Service only to detect when a Reels/Shorts feed is on screen so it can block it — it never reads your messages, screen content, or keystrokes.

ReelGuard is free and supported by a small banner ad.

Take back your time — install ReelGuard today.
```

### 1b. French

**Nom de l'appli**
```
ReelGuard : bloquer Reels & Shorts
```

**Description courte**
```
Bloquez Reels, Shorts & Spotlight. Limites de temps ou plages horaires. 100% local.
```

**Description complète**
```
Vous passez trop de temps à scroller les Reels, Shorts, Spotlight ? ReelGuard vous aide à reprendre le contrôle — sans supprimer les apps que vous utilisez.

ReelGuard détecte l'ouverture d'un fil de vidéos courtes et le bloque selon VOS règles. Tout se passe sur votre téléphone : pas de compte, pas de serveur, aucune donnée ne quitte votre appareil.

CE QUI EST BLOQUÉ
• Instagram Reels
• YouTube Shorts
• TikTok
• Facebook Reels
• Snapchat Spotlight

POUR GARDER LE CONTRÔLE
• Limite de temps — un quota quotidien (ex : 15 min). Avertissement à 80 %, puis blocage.
• Plages horaires — bloquez les Reels à certaines heures (la nuit, au travail).
• Mode Focus — blocage total pendant une durée choisie quand vous devez vous concentrer.
• Mode Parental — verrouillez les réglages par un PIN. Idéal avec Google Family Link.
• Contrôle par app — choisissez quelles apps ReelGuard surveille.
• Exception messagerie — laissez passer les reels reçus en messages privés, si vous le souhaitez.

LA CONFIDENTIALITÉ D'ABORD
ReelGuard ne collecte aucune donnée personnelle. Vos réglages et stats restent sur votre appareil. L'app utilise le service d'accessibilité d'Android uniquement pour détecter l'affichage d'un fil Reels/Shorts afin de le bloquer — jamais pour lire vos messages, le contenu de l'écran ou vos frappes.

ReelGuard est gratuit, financé par une petite bannière publicitaire.

Reprenez votre temps — installez ReelGuard.
```

**Graphic assets still needed:** app icon (512×512), feature graphic (1024×500),
at least 2 phone screenshots (e.g. dashboard, blocking screen, settings).

---

## 2. Accessibility use — Permissions declaration

In **Play Console → Policy → App content → "Accessibility API" / Permissions declaration**,
you'll be asked to justify the `BIND_ACCESSIBILITY_SERVICE` use. Paste:

**Which functionality uses the Accessibility API?**
```
ReelGuard is a digital‑wellbeing / focus tool that blocks short‑video feeds
(Instagram Reels, YouTube Shorts, TikTok, Facebook Reels, Snapchat Spotlight).
The Accessibility Service is the only reliable way to detect, on‑device, when one
of these feeds is currently displayed, so the app can immediately show a blocking
screen and return the user to the home screen according to limits the user set
(daily time limit, blocked time slots, Focus Mode).
```

**Why no alternative (non‑accessibility) approach works**
```
Android exposes no public API to detect that a specific in‑app feed (e.g. the Reels
tab) is on screen. UsageStats only reports which app is foreground, not which
section, so it cannot distinguish the Reels feed from the rest of the app.
Detecting the feed on‑screen therefore requires the Accessibility Service.
```

**How users are informed (prominent disclosure)**
```
During onboarding, before enabling the service, ReelGuard shows a prominent
disclosure explaining that the Accessibility Service is used only to detect when a
Reels/Shorts feed is open, that it does not read screen content, messages, or
keystrokes, and that no data leaves the device. The same explanation is available
anytime in Settings → Privacy & data.
```

> Google may also ask for a short **demo video** (unlisted YouTube link) showing the
> disclosure and the blocking behaviour. Record a 20–40s screen capture: onboarding
> disclosure → enable service → open Reels → blocking screen appears.

---

## 3. Data safety form

Console → **App content → Data safety**. Suggested answers:

**Does your app collect or share any of the required user data types?**
```
Yes — Device or other IDs (advertising ID), collected by the Google AdMob SDK.
```

**Data types → Device or other IDs → Advertising ID**
- Collected: **Yes** · Shared: **Yes** (with Google for advertising)
- Processed ephemerally: No
- Required or optional: Required
- Purpose: **Advertising or marketing**

**All other data types (personal info, messages, photos, location, contacts, etc.)**
```
Not collected. ReelGuard stores settings and usage stats only on the device and
sends nothing to a server.
```

**Security practices**
- Is data encrypted in transit? **Yes** (AdMob uses HTTPS).
- Can users request data deletion? There is no account; on‑device data is removed
  by clearing app data or uninstalling. Note this in the deletion question.

**Privacy policy URL**
```
[https://<your-hosted-URL>/PRIVACY]   ← host PRIVACY.md (e.g. GitHub Pages) and paste the link
```

---

## 4. Content rating questionnaire

Category: **Utility / Productivity**. Answer honestly — typical outcome is
**Everyone / PEGI 3**:
- No violence, sexual content, profanity, gambling, or user‑generated content.
- Does the app share the user's location? No.
- Does it contain ads? **Yes** (declare it).

---

## 5. Target audience & content (IMPORTANT)

Console → **App content → Target audience and content**.

Because ReelGuard shows **AdMob ads and collects the Advertising ID**, do **not**
include age brackets under 13 in the target audience unless you switch to a
Families‑policy‑compliant ad setup. Recommended:

```
Target age group: 18 and over (or 13+), positioned as a tool for adults / parents.
```

The Parental Mode is framed as a feature a **parent** configures on a child's
device — the app itself is marketed to adults. This keeps you out of the Google
Play Families programme requirements while still supporting parental use.

If you later want to target children directly, you must remove AD_ID collection or
use only Families‑certified ad SDKs, and complete the Families declaration.

---

## 6. Pre‑launch checklist

- [ ] Keystore secrets set in GitHub (`KEYSTORE_PATH`, `KEY_STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)
- [ ] `git push origin v3.43` → AAB built and signed by the workflow
- [ ] Enroll in **Play App Signing**
- [ ] Upload AAB to **Internal testing** first, install, verify blocking works
- [ ] Host `PRIVACY.md`, paste URL in listing + Data safety
- [ ] Complete Data safety, Accessibility declaration, Content rating, Target audience
- [ ] Add icon, feature graphic, screenshots
- [ ] Submit for review
