# GCU Auto Connexion (v4.0.0)

Application Android native senior-friendly (Kotlin + Android 15 / Target SDK 35) permettant la reconnexion automatique continue au portail captif ALCASAR / CoovaChilli du camping GCU (Jard-sur-Mer).

## 🌟 Fonctionnalités Principales

- **Totalement Automatique** : Reconnexion toutes les 10 minutes (aux créneaux :00, :10, :20, :30, :40, :50 min) et dès que le réseau Wi-Fi GCU est à portée.
- **Survie aux optimisations Android** :
  - **Foreground Service** avec notification permanente (`FOREGROUND_SERVICE_DATA_SYNC`).
  - **AlarmManager** avec alarmes exactes (`setExactAndAllowWhileIdle`) pour réveiller l'appareil en mode Doze / écran éteint / téléphone verrouillé.
  - **WorkManager** comme système de secours d'arrière-plan.
  - **BootReceiver** pour relancer l'auto-reconnexion au démarrage (`BOOT_COMPLETED`), après déverrouillage ou lors d'une mise à jour.
- **Sécurité Maximale** :
  - Identifiants chiffrés avec **Android Keystore (AES/GCM)**.
  - Envoi des identifiants **uniquement** sur le sous-réseau GCU (`192.168.182.0/24` / passerelle `192.168.182.1`). Aucun envoi sur d'autres réseaux.
- **Zero WebView / HTTP Natif** :
  - Requêtes HTTP/HTTPS exécutées via OkHttp lié directement aux sockets de l'interface Wi-Fi.
  - Analyse dynamique du formulaire HTML `intercept.php` avec détection automatique des noms de champs (`username`, `UserName`, `password`, `Password`, etc.).
  - Récupération d'un challenge neuf avant chaque tentative.
- **Interface Senior / Ergonomie** :
  - Grandes polices de caractères, contrastes élevés et boutons très larges (56dp+).
  - Écran principal avec état en temps réel (🟢 Connecté, 🟡 En attente, 🔴 Déconnecté) et heure de dernière reconnexion.
  - Écran de Journal complet détaillant chaque étape (`prelogin`, `formulaire`, `POST`, `logon`, `HTTP 204`).
  - Export Diagnostic au format ZIP (redactant automatiquement tout mot de passe).

---

## 🏗️ Structure du Projet Android Studio

```
Dossier auto connexion/
├── app/
│   ├── build.gradle
│   ├── release.keystore
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/fr/gcu/jardsurmer/autoconnect/
│           │   ├── GCUApplication.kt
│           │   ├── model/         (Credentials, LoginResult, LogEntry, DiagnosticInfo)
│           │   ├── data/          (CredentialStore, LogRepository, NetworkInspector, DnsResolver, HtmlFormParser, PortalLoginClient, DiagnosticExporter)
│           │   ├── service/       (AutoConnectService, AlarmReceiver, AutoConnectWorker, BootReceiver)
│           │   └── ui/            (MainActivity, MainViewModel, ConnectionFragment, LogsFragment, LogAdapter, ViewPagerAdapter)
│           └── res/               (layout, drawable, values, xml)
├── build.gradle
├── settings.gradle
├── gradle.properties
├── GCU-Auto-Connexion-4.0.0.apk        # APK Release Signé
└── GCU-Auto-Connexion-4.0.0-debug.apk  # APK Debug
```

---

## 🛠️ Procédure de Build (Compilation)

### Prérequis
- Java JDK 17+
- Android SDK avec Target SDK 35 et Build-Tools 35.0.0+
- Gradle 8.5+

### Instructions de compilation en ligne de commande :

1. Définissez vos variables d'environnement JDK et Android SDK :
   ```bash
   export JAVA_HOME=/chemin/vers/jdk-17
   export ANDROID_HOME=/chemin/vers/android-sdk
   export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
   ```

2. Générer l'APK Release signé et l'APK Debug :
   ```bash
   gradle assembleDebug assembleRelease
   ```

3. Les APKs générés se trouvent dans :
   - Release signé : `app/build/outputs/apk/release/app-release.apk`
   - Debug : `app/build/outputs/apk/debug/app-debug.apk`

---

## 📜 Vérification de la Signature Release

Pour vérifier la validité de la signature de l'APK Release :
```bash
apksigner verify --verbose GCU-Auto-Connexion-4.0.0.apk
```
