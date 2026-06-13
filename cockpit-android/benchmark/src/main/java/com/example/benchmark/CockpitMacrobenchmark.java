package com.example.benchmark;

import android.os.SystemClock;

import androidx.benchmark.macro.CompilationMode;
import androidx.benchmark.macro.FrameTimingMetric;
import androidx.benchmark.macro.StartupMode;
import androidx.benchmark.macro.StartupTimingMetric;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import kotlin.Unit;

@RunWith(AndroidJUnit4.class)
@LargeTest
public final class CockpitMacrobenchmark {
    private static final String PACKAGE_NAME = "com.tacticnav.cockpit";
    private static final int STARTUP_ITERATIONS = 5;
    private static final int FRAME_ITERATIONS = 5;
    private static final long FRAME_MEASURE_MS = 15_000L;

    @Rule
    public NoGrantMacrobenchmarkRule benchmarkRule = new NoGrantMacrobenchmarkRule();

    @Test
    public void coldStartup() {
        benchmarkRule.getDelegate().measureRepeated(
                PACKAGE_NAME,
                Collections.singletonList(new StartupTimingMetric()),
                CompilationMode.DEFAULT,
                StartupMode.COLD,
                STARTUP_ITERATIONS,
                scope -> {
                    CockpitBenchmarkSetup.prepareLaunch();
                    scope.pressHome();
                    return Unit.INSTANCE;
                },
                scope -> {
                    scope.startActivityAndWait();
                    return Unit.INSTANCE;
                }
        );
    }

    // @Test
    // public void frameTimingWhileRendering() {
    //     benchmarkRule.getDelegate().measureRepeated(
    //             PACKAGE_NAME,
    //             Collections.singletonList(new FrameTimingMetric()),
    //             CompilationMode.DEFAULT,
    //             null,
    //             FRAME_ITERATIONS,
    //             scope -> {
    //                 CockpitBenchmarkSetup.prepareLaunch();
    //                 scope.pressHome();
    //                 scope.killProcess();
    //                 return Unit.INSTANCE;
    //             },
    //             scope -> {
    //                 scope.startActivityAndWait();
    //                 scope.getDevice().waitForIdle();
    //                 SystemClock.sleep(FRAME_MEASURE_MS);
    //                 return Unit.INSTANCE;
    //             }
    //     );
    // }
    @Test
    public void frameTimingWhileRendering() {
        benchmarkRule.getDelegate().measureRepeated(
                PACKAGE_NAME,
                Collections.singletonList(new FrameTimingMetric()),
                CompilationMode.DEFAULT,
                null,
                FRAME_ITERATIONS,
                scope -> {
                    CockpitBenchmarkSetup.prepareLaunch();
                    scope.pressHome();
                    scope.killProcess();
                    return Unit.INSTANCE;
                },
                scope -> {
                    scope.startActivityAndWait();
                    scope.getDevice().waitForIdle();
                    
                    // 1. On récupère la taille de l'écran de l'émulateur
                    int width = scope.getDevice().getDisplayWidth();
                    int height = scope.getDevice().getDisplayHeight();
                    
                    // 2. On simule des mouvements (swipes) pour forcer le rendu graphique
                    // On fait par exemple 3 glissements de droite à gauche
                    for (int i = 0; i < 3; i++) {
                        scope.getDevice().swipe(
                                (int) (width * 0.8),  // X de départ (à droite)
                                (int) (height * 0.5), // Y de départ (au milieu)
                                (int) (width * 0.2),  // X d'arrivée (à gauche)
                                (int) (height * 0.5), // Y d'arrivée (au milieu)
                                20                    // Vitesse du geste (plus c'est bas, plus c'est rapide)
                        );
                        
                        // Une petite pause d'une demi-seconde entre chaque geste
                        SystemClock.sleep(500);
                    }
                    
                    return Unit.INSTANCE;
                }
        );
    }
}
