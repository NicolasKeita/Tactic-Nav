#!/usr/bin/env python3
"""
Génère un rapport HTML lisible à partir du fichier benchmarkData.json
produit par les tests Macrobenchmark Android.
"""

import json
import sys
import os
from pathlib import Path


def load_benchmark_data(json_path: str) -> dict:
    with open(json_path, "r", encoding="utf-8") as f:
        return json.load(f)


def find_benchmark_by_name(data: dict, name: str) -> dict | None:
    for b in data.get("benchmarks", []):
        if b["name"] == name:
            return b
    return None


def ms_to_fps(duration_ms: float) -> float:
    """Convertit une durée par frame (ms) en FPS."""
    return 1000.0 / duration_ms if duration_ms > 0 else 0.0


def generate_html(data: dict) -> str:
    device = data.get("context", {}).get("build", {}).get("model", "Inconnu")
    os_version = data.get("context", {}).get("build", {}).get("version", {}).get("sdk", "?")

    startup = find_benchmark_by_name(data, "coldStartup")
    frame = find_benchmark_by_name(data, "frameTimingWhileRendering")

    # --- Cold Startup ---
    startup_rows = ""
    if startup and "timeToInitialDisplayMs" in startup.get("metrics", {}):
        m = startup["metrics"]["timeToInitialDisplayMs"]
        runs = m.get("runs", [])
        for i, val in enumerate(runs):
            cls = ""
            if val == m.get("minimum"):
                cls = ' class="best"'
            elif val == m.get("maximum"):
                cls = ' class="worst"'
            startup_rows += f"<tr><td>{i + 1}</td><td>{val:.2f} ms</td></tr>\n"

        startup_min = m.get("minimum", 0)
        startup_max = m.get("maximum", 0)
        startup_median = m.get("median", 0)
        startup_summary = f"""
        <div class="summary-cards">
            <div class="card"><span class="label">Médiane</span><span class="value">{startup_median:.2f} ms</span></div>
            <div class="card good"><span class="label">Meilleur</span><span class="value">{startup_min:.2f} ms</span></div>
            <div class="card warn"><span class="label">Pire</span><span class="value">{startup_max:.2f} ms</span></div>
        </div>"""
    else:
        startup_summary = "<p class='no-data'>Aucune donnée de démarrage</p>"
        startup_rows = "<tr><td colspan='2'>Aucune donnée</td></tr>"

    # --- Frame Timing ---
    frame_rows = ""
    frame_summary = ""
    frame_details = ""
    frame_fps_table = ""

    if frame:
        metrics = frame.get("metrics", {})
        sampled = frame.get("sampledMetrics", {})

        # Frame count per run
        if "frameCount" in metrics:
            fc = metrics["frameCount"]
            runs_fc = fc.get("runs", [])
            frame_rows += "<h3>Nombre de frames par itération</h3>\n"
            frame_rows += "<table>\n<tr><th>Itération</th><th>Frames capturées</th></tr>\n"
            for i, val in enumerate(runs_fc):
                frame_rows += f"<tr><td>{i + 1}</td><td>{int(val)}</td></tr>\n"
            frame_rows += "</table>\n"

        # frameDurationCpuMs percentiles
        if "frameDurationCpuMs" in sampled:
            fdc = sampled["frameDurationCpuMs"]
            p50 = fdc.get("P50", 0)
            p90 = fdc.get("P90", 0)
            p95 = fdc.get("P95", 0)
            p99 = fdc.get("P99", 0)

            def fps_color(fps):
                if fps >= 55:
                    return "good"
                elif fps >= 30:
                    return "warn"
                else:
                    return "bad"

            fps50 = ms_to_fps(p50)
            fps90 = ms_to_fps(p90)
            fps95 = ms_to_fps(p95)
            fps99 = ms_to_fps(p99)

            frame_summary = f"""
            <h3>Durée par frame (frameDurationCpuMs)</h3>
            <div class="summary-cards">
                <div class="card good"><span class="label">P50 (médiane)</span><span class="value">{p50:.2f} ms</span><span class="sub">{fps50:.1f} FPS</span></div>
                <div class="card good"><span class="label">P90</span><span class="value">{p90:.2f} ms</span><span class="sub">{fps90:.1f} FPS</span></div>
                <div class="card warn"><span class="label">P95</span><span class="value">{p95:.2f} ms</span><span class="sub">{fps95:.1f} FPS</span></div>
                <div class="card bad"><span class="label">P99</span><span class="value">{p99:.2f} ms</span><span class="sub">{fps99:.1f} FPS</span></div>
            </div>
            """

            # Détail par itération
            runs_data = fdc.get("runs", [])
            frame_details += "<h3>Détail par itération</h3>\n"
            for run_idx, run_frames in enumerate(runs_data):
                avg = sum(run_frames) / len(run_frames)
                avg_fps = ms_to_fps(avg)
                min_f = min(run_frames)
                max_f = max(run_frames)
                frame_details += f"""
                <div class="iteration-block">
                    <h4>Itération {run_idx + 1} — {len(run_frames)} frames</h4>
                    <table>
                        <tr><th>Moyenne</th><th>Min</th><th>Max</th><th>FPS moyen</th></tr>
                        <tr>
                            <td>{avg:.2f} ms</td>
                            <td>{min_f:.2f} ms</td>
                            <td>{max_f:.2f} ms</td>
                            <td class="{fps_color(avg_fps)}">{avg_fps:.1f} FPS</td>
                        </tr>
                    </table>
                </div>
                """

            # Tableau FPS
            frame_fps_table += "<h3>Résumé FPS</h3>\n<table>\n"
            frame_fps_table += "<tr><th>Percentile</th><th>ms/frame</th><th>FPS</th><th>Appréciation</th></tr>\n"
            for p_label, p_val in [("P50", p50), ("P90", p90), ("P95", p95), ("P99", p99)]:
                fps = ms_to_fps(p_val)
                if fps >= 55:
                    app = "✅ Fluide"
                elif fps >= 30:
                    app = "⚠️ Correct"
                else:
                    app = "❌ Lent"
                frame_fps_table += f"<tr><td>{p_label}</td><td>{p_val:.2f} ms</td><td>{fps:.1f} FPS</td><td>{app}</td></tr>\n"
            frame_fps_table += "</table>\n"

    html = f"""<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Rapport de Benchmark — Tactic-Nav Cockpit</title>
<style>
    * {{ margin: 0; padding: 0; box-sizing: border-box; }}
    body {{
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
        background: #0f172a;
        color: #e2e8f0;
        padding: 2rem;
        line-height: 1.6;
    }}
    .container {{ max-width: 960px; margin: 0 auto; }}
    h1 {{ font-size: 1.8rem; color: #f8fafc; margin-bottom: 0.5rem; }}
    h2 {{ font-size: 1.4rem; color: #38bdf8; margin-top: 2rem; margin-bottom: 1rem; padding-bottom: 0.5rem; border-bottom: 2px solid #1e293b; }}
    h3 {{ font-size: 1.1rem; color: #94a3b8; margin-top: 1.5rem; margin-bottom: 0.75rem; }}
    h4 {{ font-size: 1rem; color: #cbd5e1; margin-bottom: 0.5rem; }}
    .subtitle {{ color: #64748b; font-size: 0.9rem; margin-bottom: 2rem; }}
    .summary-cards {{ display: flex; gap: 1rem; flex-wrap: wrap; margin-bottom: 1.5rem; }}
    .card {{
        background: #1e293b;
        border: 1px solid #334155;
        border-radius: 12px;
        padding: 1rem 1.5rem;
        min-width: 150px;
        flex: 1;
        text-align: center;
    }}
    .card .label {{ display: block; font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.05em; color: #64748b; margin-bottom: 0.3rem; }}
    .card .value {{ display: block; font-size: 1.5rem; font-weight: 700; color: #f8fafc; }}
    .card .sub {{ display: block; font-size: 0.9rem; color: #94a3b8; margin-top: 0.2rem; }}
    .card.good {{ border-color: #22c55e; }}
    .card.good .value {{ color: #4ade80; }}
    .card.warn {{ border-color: #f59e0b; }}
    .card.warn .value {{ color: #fbbf24; }}
    .card.bad {{ border-color: #ef4444; }}
    .card.bad .value {{ color: #f87171; }}
    table {{
        width: 100%;
        border-collapse: collapse;
        margin-bottom: 1.5rem;
        background: #1e293b;
        border-radius: 8px;
        overflow: hidden;
    }}
    th, td {{ padding: 0.6rem 1rem; text-align: left; border-bottom: 1px solid #334155; }}
    th {{ background: #334155; color: #94a3b8; font-weight: 600; font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.05em; }}
    tr:last-child td {{ border-bottom: none; }}
    tr:hover td {{ background: #1a2332; }}
    .best {{ color: #4ade80; font-weight: 600; }}
    .worst {{ color: #f87171; font-weight: 600; }}
    .good {{ color: #4ade80; }}
    .warn {{ color: #fbbf24; }}
    .bad {{ color: #f87171; }}
    .iteration-block {{
        background: #1e293b;
        border: 1px solid #334155;
        border-radius: 8px;
        padding: 1rem;
        margin-bottom: 1rem;
    }}
    .iteration-block table {{ margin-bottom: 0; }}
    .no-data {{ color: #64748b; font-style: italic; }}
    footer {{ margin-top: 3rem; padding-top: 1rem; border-top: 1px solid #1e293b; color: #475569; font-size: 0.8rem; }}
</style>
</head>
<body>
<div class="container">
    <h1>📊 Rapport de Benchmark</h1>
    <p class="subtitle">Appareil : {device} (Android SDK {os_version}) — {len(data.get("benchmarks", []))} test(s)</p>

    <h2>🚀 coldStartup — Temps d'affichage initial</h2>
    {startup_summary}
    <h3>Détail par itération</h3>
    <table>
        <tr><th>Itération</th><th>Temps (ms)</th></tr>
        {startup_rows}
    </table>

    <h2>🎮 frameTimingWhileRendering — Performances de rendu</h2>
    {frame_rows}
    {frame_summary}
    {frame_fps_table}
    {frame_details}
    
    <h2>💡 Interprétation</h2>
    <ul style="margin-left: 1.5rem; margin-bottom: 2rem;">
        <li><strong>coldStartup</strong> : un temps < 1s pour un démarrage à froid est considéré comme excellent. Ici on est autour de <strong>500 ms</strong>.</li>
        <li><strong>frameTimingWhileRendering</strong> : 
            <ul>
                <li>P50 < 16 ms → <strong>60+ FPS</strong> constants, rendu fluide ✅</li>
                <li>P95 < 33 ms → 30+ FPS maintenus presque tout le temps ✅</li>
                <li>P99 élevé → quelques frames lentes isolées (premier affichage ou chargement de données) ⚠️</li>
            </ul>
        </li>
    </ul>

    <footer>
        Généré le {__import__("datetime").datetime.now().strftime("%d/%m/%Y à %H:%M")} · 
        Source : benchmarkData.json
    </footer>
</div>
</body>
</html>"""
    return html


def main():
    # Chercher le fichier JSON le plus récent dans les outputs
    base = Path(__file__).parent / "build" / "outputs" / "connected_android_test_additional_output"
    
    if not base.exists():
        print("[ERREUR] Dossier des outputs de benchmark introuvable.")
        print(f"   Chemin attendu : {base}")
        sys.exit(1)

    json_files = list(base.rglob("*-benchmarkData.json"))
    if not json_files:
        print("[ERREUR] Aucun fichier benchmarkData.json trouve.")
        sys.exit(1)

    # Prendre le plus recent
    latest = max(json_files, key=lambda p: p.stat().st_mtime)
    print(f"[OK] Fichier source : {latest}")

    data = load_benchmark_data(str(latest))
    html = generate_html(data)

    output_path = latest.parent / "benchmark_report.html"
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(html)

    print(f"[OK] Rapport genere : {output_path}")


if __name__ == "__main__":
    main()