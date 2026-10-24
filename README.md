# Isaac Companion

Affiche en temps réel la **map du niveau** et l'**inventaire** de *The Binding of Isaac: Repentance+* sur l'écran du bas d'une **AYN Thor**, pendant que le jeu tourne sur l'écran du haut via GameNative.

```
[Isaac sous Wine]                         [Android, écran du bas]
mod Lua ── serveur TCP 127.0.0.1:47777 ──> app companion (client)
           NDJSON, 1 message par ligne
```

- `mod/isaac-companion-hello/` : test de faisabilité (étape 0).
- `mod/isaac-companion/` : le vrai mod (serveur TCP, collecte de l'état).
- `android/` : l'app (Kotlin, Jetpack Compose dessinant un `Canvas` en pixel art).
- `tools/fake_server.py` : simule le mod pour développer sans le jeu.
- `docs/protocol.md` : le protocole. `docs/DECISIONS.md` : les choix pris et les points à vérifier en jeu. `TODO.md` : ce qui reste.

> **Avertissement.** `--luadebug` donne aux mods l'accès à `io` et `os`. Avec cette option, n'activer que des mods de confiance.

## 1. Test du pont Wine → Android (à faire en premier)

1. Copier `mod/isaac-companion-hello/` dans le dossier `mods/` du jeu (dans le conteneur GameNative).
2. Lancer Isaac avec l'option `--luadebug` (arguments de lancement du conteneur GameNative). Sans elle, `require("socket")` échoue et le mod ne fait rien (une ligne d'avertissement dans `log.txt`).
3. Démarrer une **run** : les mods ne tournent pas dans le menu principal.
4. Dans Termux, sur l'écran du bas : `pkg install netcat-openbsd` puis `nc 127.0.0.1 47777`.
5. `hello from isaac` doit s'afficher. Sinon, regarder `log.txt` (`Documents/My Games/Binding of Isaac Repentance+/` dans le conteneur) : le mod y écrit des lignes `[IsaacCompanionHello]`.

## 2. Installer le vrai mod

Remplacer le mod « hello » par `mod/isaac-companion/` (même dossier `mods/`, toujours avec `--luadebug`). Pour tester le flux sans l'app : `nc 127.0.0.1 47777` affiche les messages JSON du protocole.

Si **External Item Descriptions** (EID) est installé, le mod en lit les descriptions détaillées.

## 3. Extraire les ressources du jeu

Les sprites et polices du jeu ne sont **pas** dans ce dépôt. L'app les lit dans un dossier que vous extrayez vous-même :

1. Dans le dossier du jeu, lancer `tools/ResourceExtractor/ResourceExtractor.exe` (sous Windows/Proton/Wine). Il produit `extracted_resources/resources/`.
2. Copier ce dossier sur la Thor (par exemple `Documents/IsaacResources/`).
3. Dans l'app, toucher le voyant d'état en haut à droite (ou le bouton de l'écran d'attente) et choisir le dossier. L'app accepte `resources/` ou son parent. L'autorisation est mémorisée.

Sans ressources, l'app fonctionne avec un habillage de remplacement (icônes : numéros d'objets).

## 4. Installer l'app

La CI produit `app-debug.apk` (artefact du job *app*). Sinon : `cd android && ./gradlew :app:assembleDebug` (JDK 17, SDK Android 34). Placer l'app sur l'écran du bas avec la gestion double écran de la Thor.

Utilisation : toucher un objet ouvre sa description (nom, phrase du jeu, détail EID si disponible) ; toucher ailleurs la ferme. Glisser verticalement sur la grille de passifs la fait défiler. En co-op ou Jacob & Esau, des onglets P1/P2 apparaissent.

## Développement

```sh
python3 tools/fake_server.py                  # un joueur ; --players 2 | jacob ; --chaos ; --mode mute|idle
luacheck mod/ tests/                          # lint (globales Isaac dans .luacheckrc)
lua5.3 tests/run.lua                          # tests du mod (faux Isaac + vrai LuaSocket)
cd android && ./gradlew :app:testDebugUnitTest :preview:test
```

`:preview:test` exécute le vrai code d'affichage sur la JVM et écrit des captures dans `android/preview/build/screens/`. Rien du jeu n'est utilisé : les icônes de ces captures sont de l'art de remplacement.
