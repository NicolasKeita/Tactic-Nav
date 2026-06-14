# Commandes Gradle — TacticNavCockpit

## Structure du projet

```
TacticNavCockpit          (projet racine)
├── :app                  (application Android :com.tacticnav.cockpit)
└── :benchmark            (module de benchmark :com.tacticnav.cockpit.benchmark)
```

Les commandes s'exécutent depuis le répertoire `cockpit-android/` avec `.\gradlew.bat`.

---

## Tâches de base

| Commande | Description |
|---|---|
| `.\gradlew.bat build` | Compile et vérifie l'ensemble du projet |
| `.\gradlew.bat clean` | Nettoie tous les répertoires de build |
| `.\gradlew.bat assemble` | Assemble tous les artefacts |
| `.\gradlew.bat check` | Exécute toutes les vérifications |

---

## Tâches par module

### Module `:app` — Application Android

#### Compilation et assemblage

| Commande | Description |
|---|---|
| `.\gradlew.bat :app:assembleDebug` | Assemble l'APK debug |
| `.\gradlew.bat :app:assembleRelease` | Assemble l'APK release |
| `.\gradlew.bat :app:assembleBenchmark` | Assemble l'APK benchmark (variante debug) |
| `.\gradlew.bat :app:assembleBenchmark1` | Assemble l'APK benchmark1 (variante release, non débuggable) |

#### Tests

| Commande | Description |
|---|---|
| `.\gradlew.bat :app:testDebugUnitTest` | Exécute les tests unitaires debug |
| `.\gradlew.bat :app:connectedDebugAndroidTest` | Exécute les tests instrumentés debug sur un appareil connecté |
| `.\gradlew.bat :app:pixel6api34DebugAndroidTest` | Exécute les tests instrumentés debug sur l'appareil virtuel managé Pixel 6 (API 34) |

#### Vérifications des budgets embarqués

| Commande | Description |
|---|---|
| `.\gradlew.bat :app:checkEmbeddedBudgets` | Vérifie que la taille de l'APK debug, du DEX et des ressources respecte les budgets définis dans `embedded-budgets.properties` |
| `.\gradlew.bat :app:checkHotPathAllocations` | Analyse les fichiers source du rendu pour détecter les appels à `String.format()` dans les chemins chauds (interdit pour les performances) |
| `.\gradlew.bat :app:printEmbeddedBudgetSummary` | Affiche un résumé complet des vérifications de budgets embarqués (APK, DEX, heap, FPS). Dépend de `checkHotPathAllocations` et `checkEmbeddedBudgets` |

#### Rapports runtime (nécessite un appareil connecté)

| Commande | Description |
|---|---|
| `.\gradlew.bat :app:connectedDebugAndroidTest` | Exécute les tests instrumentés (prérequis pour générer le rapport runtime) |
| `.\gradlew.bat :app:pullCockpitRuntimeBudgetReport` | Récupère le rapport de budget runtime (heap, FPS) depuis l'application déboguée connectée via `adb run-as` |
| `.\gradlew.bat :app:printCockpitRuntimeBudgetSummary` | Affiche les résultats mesurés du heap et des FPS runtime et les compare aux budgets. Dépend de `pullCockpitRuntimeBudgetReport` |
| `.\gradlew.bat :app:verifyCockpitRuntimeBudgets` | Exécute toutes les vérifications runtime et affiche les résultats. Dépend de `printCockpitRuntimeBudgetSummary` |

#### Benchmarks

| Commande | Description |
|---|---|
| `.\gradlew.bat :app:runCockpitMacrobenchmark` | Lance les macrobenchmarks (démarrage + trames) sur l'appareil virtuel managé Pixel 6. Déclenche en réalité `:benchmark:pixel6api34Benchmark1AndroidTest` |
| `.\gradlew.bat :app:printBenchmarkResults` | Affiche les derniers résultats de benchmark (timeToInitialDisplay, frameCount, frameDuration, frameOverrun) depuis le fichier JSON généré |

---

### Module `:benchmark` — Benchmarks

Les benchmarks utilisent Android Benchmark Macro (JUnit 4) avec le type de build `benchmark1`.

#### Tests

| Commande | Description |
|---|---|
| `.\gradlew.bat :benchmark:pixel6api34Benchmark1AndroidTest` | Exécute tous les benchmarks sur l'appareil virtuel managé Pixel 6 (API 34). C'est la commande principale pour lancer les tests de performance |
| `.\gradlew.bat :benchmark:assembleBenchmark1` | Assemble l'APK de benchmark |

#### Rapports

| Commande | Description |
|---|---|
| `.\gradlew.bat :benchmark:generateMemoryStabilityReport` | Génère un rapport HTML détaillé des résultats du `MemoryStabilityBenchmark` (GC profile). Le rapport est créé dans `benchmark/build/reports/benchmark/rapport_benchmark_<timestamp>.html` |
| `.\gradlew.bat :benchmark:printMemoryStabilitySummary` | Affiche un résumé coloré des résultats du `MemoryStabilityBenchmark` directement dans le terminal (via le script Python `benchmark/scripts/generate_benchmark_report.py`) |

---

## Build types disponibles

### Module `:app`

| Build type | Hérite de | Débuggable | Signature |
|---|---|---|---|
| `debug` | — | Oui | debug |
| `release` | — | Non | release |
| `benchmark` | `debug` | Non (debuggable = false) | debug |
| `benchmark1` | `release` | Non (debuggable = false) | debug |

### Module `:benchmark`

| Build type | Hérite de | Débuggable | Signature |
|---|---|---|---|
| `benchmark1` | — | Oui (debuggable = true) | debug |

---

## Appareil virtuel managé (Managed Device)

Un appareil Pixel 6 (API 34) est configuré dans les deux modules :

```
pixel6api34 → Pixel 6, API 34, AOSP ATD, 64-bit, x86_64
```

Utilisation :
- `.\gradlew.bat :app:pixel6api34DebugAndroidTest`
- `.\gradlew.bat :benchmark:pixel6api34Benchmark1AndroidTest`

---

## Exemples d'utilisation

```bash
# Vérifier les budgets embarqués
.\gradlew.bat :app:printEmbeddedBudgetSummary

# Lancer les benchmarks de performance sur l'émulateur Pixel 6
.\gradlew.bat :benchmark:pixel6api34Benchmark1AndroidTest

# Générer le rapport HTML après un benchmark
.\gradlew.bat :benchmark:generateMemoryStabilityReport

# Afficher les derniers résultats dans le terminal
.\gradlew.bat :benchmark:printMemoryStabilitySummary

# Lancer les benchmarks via le module app
.\gradlew.bat :app:runCockpitMacrobenchmark

# Afficher les résultats de benchmark
.\gradlew.bat :app:printBenchmarkResults

# Vérification complète (unit tests + budgets)
.\gradlew.bat :app:check
```

---

## Notes

- Le daemon Gradle peut prendre du temps au premier démarrage.
- Les benchmarks nécessitent :
  - Un émulateur Android (Pixel 6 API 34) ou un appareil physique connecté
  - Le type de build `benchmark1` (configuré automatiquement par la tâche)
  - Les erreurs d'émulateur/débogable sont supprimées via `androidx.benchmark.suppressErrors`
- Les budgets sont définis dans le fichier `embedded-budgets.properties` à la racine du projet.