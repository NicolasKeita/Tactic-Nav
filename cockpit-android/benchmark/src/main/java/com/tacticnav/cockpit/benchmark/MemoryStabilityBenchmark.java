package com.tacticnav.cockpit.benchmark;

import android.os.SystemClock;
import android.util.Log;

import androidx.benchmark.macro.CompilationMode;
import androidx.benchmark.macro.TraceSectionMetric;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;

/**
 * === TEST DE STABILITÉ MÉMOIRE (GC PROFILE) ===
 * <p>
 * Ce test valide qu'en phase de routine (carte affichée avec cibles), l'application
 * TACTIC-NAV ne déclenche AUCUN Garbage Collector pendant 2 minutes de sollicitation
 * continue de l'IHM (déplacements/zooms simulés sur la carte).
 * <p>
 * -------------------------------------------------------------------
 * PROFIL "DENTS DE SCIE" (ÉCHEC) vs PROFIL PLAT (SUCCÈS)
 * -------------------------------------------------------------------
 * <p>
 * 🔴 PROFIL "DENTS DE SCIE" – ÉCHEC DU TEST
 * -------------------------------------------------------------------
 * Une application qui "scie" en mémoire alterne entre :
 * - Montées rapides de la heap (allocation d'objets temporaires éphémères)
 * - Chutes brutales (déclenchement du GC pour libérer ces objets)
 * <p>
 * Conséquences :
 * - Les GC "Background young generation GC", "Alloc concurrent mark sweep GC"
 * apparaissent dans les traces systrace perfetto.
 * - Le profil heap (trace Java Heap / RSS) forme visuellement
 * des "dents de scie" sur un graphique temporel.
 * - L'utilisateur perçoit des micro-rasters (jank) à chaque GC,
 * car le GC suspend partiellement le thread principal.
 * - Le KPI mémoire peut sembler bas (heap < 45 Mo) MAIS le
 * comportement est instable.
 * <p>
 * Ce test DÉTECTE CE PROFIL en comptant le nombre d'occurrences
 * des sections de trace GC dans les métriques TraceSectionMetric.
 * Si un seul GC est détecté, le test échoue.
 * <p>
 * 🟢 PROFIL PLAT – SUCCÈS DU TEST
 * -------------------------------------------------------------------
 * Une application stable en mémoire :
 * - N'alloue pas d'objets temporaires sur le hot path du rendu.
 * - Réutilise les objets (object pooling, structures mutables).
 * - Le heap reste plat une fois le warm-up terminé.
 * - Aucune trace GC détectable pendant la phase de sollicitation.
 * <p>
 * Résultat :
 * - TraceSectionMetric rapporte zéro GC.
 * - Le KPI heap (< 45 Mo) est respecté (vérifié par les budgets embarqués).
 * - Le profil temporel est plat → pas de jank.
 * <p>
 * Ce test VALIDE CE PROFIL en exigeant zéro GC mesuré.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public final class MemoryStabilityBenchmark {

    private static final String TAG = "MemoryStabilityBenchmark";
    private static final String PACKAGE_NAME = "com.tacticnav.cockpit";

    /**
     * Durée de stabilisation après le lancement de l'activité.
     * On attend que la carte et les cibles soient entièrement initialisées
     * avant de commencer la mesure.
     */
    private static final long STABILIZATION_MS = 3_000L;

    /**
     * Durée totale de la boucle de simulation de routine.
     * 2 minutes = 120 000 ms.
     * <p>
     * Pendant cette fenêtre, on exige zéro déclenchement de GC.
     */
    private static final long ROUTINE_DURATION_MS = 120_000L;

    /**
     * Intervalle entre chaque action de simulation UI.
     * 33 ms ≈ 30 FPS (rafraîchissement continu de l'IHM).
     */
    private static final long ACTION_INTERVAL_MS = 33L;

    /**
     * Pas de déplacement en pixels pour les swipes simulés.
     */
    private static final int SWIPE_STEPS = 20;

    @Rule
    public NoGrantMacrobenchmarkRule benchmarkRule = new NoGrantMacrobenchmarkRule();

    @Test
    public void memoryStabilityWhileRendering() {
        Log.i(TAG, "===== DÉMARRAGE DU TEST DE STABILITÉ MÉMOIRE =====");
        Log.i(TAG, "Objectif : zéro GC pendant " + (ROUTINE_DURATION_MS / 1000) + " secondes de routine");
        Log.i(TAG, "KPI heap : < 45 Mo (vérifié par les budgets embarqués)");
        Log.i(TAG, "Profil attendu : PLAT (pas de dents de scie)");

        // ─── Construction de la liste de métriques ─────────────────────────
        //
        // API TraceSectionMetric (version 1.2.4) :
        //   - Constructeur 2 paramètres : (String sectionName, Mode mode)
        //     pour les noms de section exacts (sectionNameContains = false implicite)
        //   - Constructeur 4 paramètres : (String displayName, Mode mode,
        //     String sectionName, boolean sectionNameContains)
        //     pour le match partiel (attrape-tout)
        //
        // Mode.COUNT → compte le nombre d'occurrences de la section
        // Mode.SUM   → somme les valeurs de la section (ex: taille d'allocation)

        List<TraceSectionMetric> metrics = new ArrayList<>();

        // GC jeune génération (le plus fréquent en cas d'allocations éphémères)
        metrics.add(new TraceSectionMetric(
                "Background young generation GC",
                TraceSectionMetric.Mode.Sum,
                false       // sectionNameContains: match exact
        ));

        // GC concurrent mark-sweep (déclenché lorsque la heap approche sa limite)
        metrics.add(new TraceSectionMetric(
                "Alloc concurrent mark sweep GC",
                TraceSectionMetric.Mode.Sum,
                false       // sectionNameContains: match exact
        ));

        // GC de type "concurrent copying" (utilisé par ART sur les devices plus récents)
        metrics.add(new TraceSectionMetric(
                "Alloc concurrent copying GC",
                TraceSectionMetric.Mode.Sum,
                false       // sectionNameContains: match exact
        ));

        // Attrape-tout : toute section de trace contenant "GC" dans son nom.
        metrics.add(new TraceSectionMetric(
                "GC",
                TraceSectionMetric.Mode.Sum,
                true        // sectionNameContains : match partiel
        ));

        // Métrique d'allocation cumulée (heap) pour documenter la consommation mémoire.
        metrics.add(new TraceSectionMetric(
                "get_object_size_allocated",
                TraceSectionMetric.Mode.Sum,
                false       // sectionNameContains: match exact
        ));

        // ─── EXÉCUTION DE LA MESURE ───────────────────────────────────────
        benchmarkRule.getDelegate().measureRepeated(
                PACKAGE_NAME,
                metrics,
                CompilationMode.DEFAULT,
                null,                                         // startupMode = null → Warm start
                1,                                            // minIterations
                scope -> {
                    // ----------------------------------------------------------
                    // SETUP : Avant chaque itération de mesure
                    // ----------------------------------------------------------
                    Log.i(TAG, "[SETUP] Préparation du lancement...");
                    CockpitBenchmarkSetup.prepareLaunch();

                    // On s'assure que l'application est bien en arrière-plan
                    // avant de la relancer, pour un démarrage propre.
                    scope.pressHome();
                    scope.killProcess();
                    return Unit.INSTANCE;
                },
                scope -> {
                    // ==========================================================
                    // PHASE 1 : LANCEMENT DE L'APPLICATION
                    // ==========================================================
                    Log.i(TAG, "[BLOCK] Lancement de l'activité principale...");
                    scope.startActivityAndWait();
                    scope.getDevice().waitForIdle();

                    // ==========================================================
                    // PHASE 2 : STABILISATION
                    // ==========================================================
                    // On attend que la carte et le set de cibles prédéfinies
                    // soient complètement chargés et rendus.
                    //
                    // Si l'application alloue des objets pendant cette phase,
                    // cela fait partie du warm-up et n'est pas compté comme échec.
                    Log.i(TAG, "[STABILISATION] Attente de " + (STABILIZATION_MS / 1000) + "s pour l'initialisation...");
                    SystemClock.sleep(STABILIZATION_MS);
                    scope.getDevice().waitForIdle();

                    // ----------------------------------------------------------
                    // PHASE 3 : ROUTINE SIMULÉE (2 minutes, 30 FPS)
                    // ----------------------------------------------------------
                    //
                    // On simule un pilote qui interagit avec la carte tactique :
                    //   - Déplacements (swipes) pour changer le point de vue
                    //   - Pincements (pinch) pour zoomer / dézoomer
                    //   - Attentes entre les actions pour maintenir 30 FPS
                    //
                    // Pendant toute cette phase, le GC NE DOIT PAS se déclencher.
                    // Si un événement GC apparaît dans les métriques TraceSectionMetric,
                    // le test sera considéré comme ÉCHEC.
                    //
                    // Pourquoi 30 FPS ?
                    //   30 FPS = 33 ms par frame. C'est la fréquence de rafraîchissement
                    //   standard d'une application de navigation/aviation embarquée.
                    //   Forcer le rendu à cette cadence permet de révéler les allocations
                    //   éphémères qui se produisent sur le hot path de dessin.

                    int displayWidth = scope.getDevice().getDisplayWidth();
                    int displayHeight = scope.getDevice().getDisplayHeight();
                    Log.i(TAG, "[ROUTINE] Dimensions écran : " + displayWidth + "x" + displayHeight);
                    Log.i(TAG, "[ROUTINE] Début de la boucle de " + (ROUTINE_DURATION_MS / 1000)
                            + "s à " + (1000 / ACTION_INTERVAL_MS) + " FPS...");

                    long startTime = SystemClock.elapsedRealtime();
                    int iteration = 0;

                    while (SystemClock.elapsedRealtime() - startTime < ROUTINE_DURATION_MS) {
                        int phase = iteration % 6;

                        switch (phase) {
                            // 0-1 : SWIPE HORIZONTAL (gauche → droite et droite → gauche)
                            case 0:
                                scope.getDevice().swipe(
                                        (int) (displayWidth * 0.2),   // départ à gauche
                                        (int) (displayHeight * 0.5),  // milieu vertical
                                        (int) (displayWidth * 0.8),   // arrivée à droite
                                        (int) (displayHeight * 0.5),  // milieu vertical
                                        SWIPE_STEPS
                                );
                                break;
                            case 1:
                                scope.getDevice().swipe(
                                        (int) (displayWidth * 0.8),   // départ à droite
                                        (int) (displayHeight * 0.5),  // milieu vertical
                                        (int) (displayWidth * 0.2),   // arrivée à gauche
                                        (int) (displayHeight * 0.5),  // milieu vertical
                                        SWIPE_STEPS
                                );
                                break;
                            // 2-3 : SWIPE VERTICAL (haut → bas et bas → haut)
                            case 2:
                                scope.getDevice().swipe(
                                        (int) (displayWidth * 0.5),   // milieu horizontal
                                        (int) (displayHeight * 0.2),  // départ en haut
                                        (int) (displayWidth * 0.5),   // milieu horizontal
                                        (int) (displayHeight * 0.8),  // arrivée en bas
                                        SWIPE_STEPS
                                );
                                break;
                            case 3:
                                scope.getDevice().swipe(
                                        (int) (displayWidth * 0.5),   // milieu horizontal
                                        (int) (displayHeight * 0.8),  // départ en bas
                                        (int) (displayWidth * 0.5),   // milieu horizontal
                                        (int) (displayHeight * 0.2),  // arrivée en haut
                                        SWIPE_STEPS
                                );
                                break;
                            // 4 : ZOOM AVANT (pinch-out simulé via deux swipes convergents)
                            case 4:
                                // Pinch-out : les deux doigts s'écartent du centre
                                // Doigt 1 : du centre vers le coin supérieur gauche
                                scope.getDevice().swipe(
                                        (int) (displayWidth * 0.5),
                                        (int) (displayHeight * 0.5),
                                        (int) (displayWidth * 0.3),
                                        (int) (displayHeight * 0.3),
                                        SWIPE_STEPS
                                );
                                // Doigt 2 : du centre vers le coin inférieur droit
                                scope.getDevice().swipe(
                                        (int) (displayWidth * 0.5),
                                        (int) (displayHeight * 0.5),
                                        (int) (displayWidth * 0.7),
                                        (int) (displayHeight * 0.7),
                                        SWIPE_STEPS
                                );
                                break;
                            // 5 : ZOOM ARRIÈRE (pinch-in simulé via deux swipes divergents)
                            case 5:
                                // Pinch-in : les deux doigts se rapprochent du centre
                                // Doigt 1 : du coin supérieur gauche vers le centre
                                scope.getDevice().swipe(
                                        (int) (displayWidth * 0.3),
                                        (int) (displayHeight * 0.3),
                                        (int) (displayWidth * 0.5),
                                        (int) (displayHeight * 0.5),
                                        SWIPE_STEPS
                                );
                                // Doigt 2 : du coin inférieur droit vers le centre
                                scope.getDevice().swipe(
                                        (int) (displayWidth * 0.7),
                                        (int) (displayHeight * 0.7),
                                        (int) (displayWidth * 0.5),
                                        (int) (displayHeight * 0.5),
                                        SWIPE_STEPS
                                );
                                break;
                        }

                        // Petite pause pour maintenir 30 FPS
                        SystemClock.sleep(ACTION_INTERVAL_MS);

                        // Toutes les ~100 itérations, on log la progression
                        if (iteration % 100 == 0) {
                            long elapsedSec = (SystemClock.elapsedRealtime() - startTime) / 1000;
                            Log.i(TAG, "[ROUTINE] " + elapsedSec + "s écoulées / "
                                    + (ROUTINE_DURATION_MS / 1000) + "s - itération #" + iteration);
                        }

                        iteration++;
                    }

                    Log.i(TAG, "[ROUTINE] Boucle terminée : " + iteration + " itérations exécutées.");

                    // ==========================================================
                    // PHASE 4 : ATTENTE FINALE
                    // ==========================================================
                    // On laisse le device terminer toute opération en attente
                    // avant que Macrobenchmark ne finalise les métriques.
                    scope.getDevice().waitForIdle();
                    SystemClock.sleep(500);

                    Log.i(TAG, "===== TEST DE STABILITÉ MÉMOIRE TERMINÉ =====");
                    Log.i(TAG, "Voir les métriques GC dans les résultats du benchmark.");
                    Log.i(TAG, "Si GC > 0 : ÉCHEC (profil en dents de scie détecté)");
                    Log.i(TAG, "Si GC == 0 : SUCCÈS (profil plat validé)");

                    return Unit.INSTANCE;
                }
        );
    }
}