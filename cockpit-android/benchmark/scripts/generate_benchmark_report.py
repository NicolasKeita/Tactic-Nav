#!/usr/bin/env python3
"""
Generateur de rapport HTML pour les resultats du benchmark
de stabilite memoire MemoryStabilityBenchmark.

Usage :
    python scripts/generate_benchmark_report.py
    python scripts/generate_benchmark_report.py --json <chemin_vers_benchmarkData.json>
"""

import argparse
import json
import os
import sys
from datetime import datetime


def load_benchmark_data(json_path: str) -> dict:
    """Charge et retourne le contenu du fichier JSON de benchmark."""
    with open(json_path, "r", encoding="utf-8") as f:
        return json.load(f)


def extract_device_info(data: dict) -> dict:
    """Extrait les informations du device depuis le contexte."""
    ctx = data.get("context", {})
    build = ctx.get("build", {})
    return {
        "brand": build.get("brand", "N/A"),
        "model": build.get("model", "N/A"),
        "device": build.get("device", "N/A"),
        "sdk": build.get("version", {}).get("sdk", "N/A"),
        "cpu_cores": ctx.get("cpuCoreCount", "N/A"),
        "cpu_max_freq": ctx.get("cpuMaxFreqHz", "N/A"),
        "mem_total": ctx.get("memTotalBytes", "N/A"),
    }


def extract_benchmark_results(data: dict) -> list:
    """Extrait les resultats des benchmarks."""
    benchmarks = data.get("benchmarks", [])
    results = []

    for bench in benchmarks:
        name = bench.get("name", "unknown")
        class_name = bench.get("className", "unknown")
        total_time_ns = bench.get("totalRunTimeNs", 0)
        total_time_s = total_time_ns / 1e9
        total_time_min = total_time_s / 60.0

        metrics = bench.get("metrics", {})
        metric_rows = []

        for metric_name, metric_data in metrics.items():
            metric_rows.append({
                "name": metric_name,
                "minimum": metric_data.get("minimum", 0),
                "maximum": metric_data.get("maximum", 0),
                "median": metric_data.get("median", 0),
                "runs": metric_data.get("runs", []),
            })

        # Determiner PASS/FAIL : si tous les compteurs GC sont a zero -> PASS
        gc_counts = [m for m in metric_rows if "GCCount" in m["name"]]
        all_gc_zero = all(m["median"] == 0.0 for m in gc_counts)

        results.append({
            "name": name,
            "class_name": class_name,
            "total_time_ns": total_time_ns,
            "total_time_s": total_time_s,
            "total_time_min": total_time_min,
            "metrics": metric_rows,
            "status": "PASS" if all_gc_zero else "FAIL",
            "gc_metrics": [m for m in metric_rows if "GC" in m["name"] and "Count" in m["name"]],
        })

    return results


def format_bytes(bytes_val: int) -> str:
    """Formatte les bytes en unite lisible."""
    if bytes_val == "N/A":
        return "N/A"
    for unit in ["B", "KB", "MB", "GB"]:
        if bytes_val < 1024:
            return f"{bytes_val:.2f} {unit}"
        bytes_val /= 1024
    return f"{bytes_val:.2f} TB"


def format_freq(hz) -> str:
    """Formatte la frequence CPU en GHz."""
    if hz == "N/A":
        return "N/A"
    return f"{hz / 1e9:.2f} GHz"


def generate_html(data: dict, output_path: str):
    """Genere la page HTML de rapport."""
    device = extract_device_info(data)
    results = extract_benchmark_results(data)
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    # Couleurs selon le statut
    status_color = "#22c55e" if all(r["status"] == "PASS" for r in results) else "#ef4444"
    status_text = "TOUS LES TESTS ONT REUSSI" if all(r["status"] == "PASS" for r in results) else "ECHEC"
    status_icon = "PASS" if all(r["status"] == "PASS" for r in results) else "FAIL"

    # Construction des donnees pour les graphiques Chart.js
    chart_datasets = []
    chart_labels = []

    for res in results:
        for gc_m in res["gc_metrics"]:
            label = gc_m["name"].replace("GCCount", "").strip()
            chart_labels.append(label)
            chart_datasets.append({
                "label": label,
                "value": gc_m["median"],
                "status": "PASS" if gc_m["median"] == 0 else "FAIL",
            })

    # Si aucun GC Count trouve, on prend toutes les metriques
    if not chart_labels:
        for res in results:
            for m in res["metrics"]:
                chart_labels.append(m["name"])
                chart_datasets.append({
                    "label": m["name"],
                    "value": m["median"],
                    "status": "PASS" if m["median"] == 0 else "FAIL",
                })

    # Generation du tableau de metriques
    metrics_rows_html = ""
    for res in results:
        for m in res["metrics"]:
            color = "#22c55e" if m["median"] == 0 else "#ef4444"
            metrics_rows_html += f"""
            <tr>
                <td>{res['name']}</td>
                <td style="font-family: monospace;">{m['name']}</td>
                <td style="color: {color}; font-weight: bold;">{m['median']:.2f}</td>
                <td>{m['minimum']:.2f}</td>
                <td>{m['maximum']:.2f}</td>
                <td>{m['runs']}</td>
            </tr>"""

    html_content = f"""<!DOCTYPE html>
<html lang="fr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Rapport Benchmark - MemoryStability</title>
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.7/dist/chart.umd.min.js"></script>
    <style>
        * {{
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }}

        body {{
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: #0f172a;
            color: #e2e8f0;
            padding: 24px;
        }}

        .container {{
            max-width: 1200px;
            margin: 0 auto;
        }}

        .header {{
            text-align: center;
            padding: 40px 20px;
            background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
            border-radius: 16px;
            border: 1px solid #334155;
            margin-bottom: 24px;
        }}

        .header h1 {{
            font-size: 28px;
            margin-bottom: 8px;
        }}

        .header .status-badge {{
            display: inline-block;
            padding: 8px 24px;
            border-radius: 9999px;
            font-weight: bold;
            font-size: 18px;
            color: white;
            background: {status_color};
            margin-top: 12px;
        }}

        .header .meta {{
            color: #94a3b8;
            font-size: 14px;
            margin-top: 12px;
        }}

        .stats-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 16px;
            margin-bottom: 24px;
        }}

        .stat-card {{
            background: #1e293b;
            border: 1px solid #334155;
            border-radius: 12px;
            padding: 20px;
            text-align: center;
        }}

        .stat-card .label {{
            font-size: 12px;
            text-transform: uppercase;
            letter-spacing: 1px;
            color: #64748b;
        }}

        .stat-card .value {{
            font-size: 22px;
            font-weight: bold;
            margin-top: 8px;
            color: #f1f5f9;
        }}

        .chart-container {{
            background: #1e293b;
            border: 1px solid #334155;
            border-radius: 12px;
            padding: 24px;
            margin-bottom: 24px;
        }}

        .chart-container h2 {{
            font-size: 18px;
            margin-bottom: 16px;
            color: #f1f5f9;
        }}

        .chart-wrapper {{
            position: relative;
            height: 400px;
        }}

        .table-container {{
            background: #1e293b;
            border: 1px solid #334155;
            border-radius: 12px;
            padding: 24px;
            overflow-x: auto;
        }}

        .table-container h2 {{
            font-size: 18px;
            margin-bottom: 16px;
            color: #f1f5f9;
        }}

        table {{
            width: 100%;
            border-collapse: collapse;
            font-size: 14px;
        }}

        th {{
            text-align: left;
            padding: 12px 16px;
            background: #334155;
            color: #94a3b8;
            font-size: 12px;
            text-transform: uppercase;
            letter-spacing: 1px;
            border-bottom: 2px solid #475569;
        }}

        td {{
            padding: 10px 16px;
            border-bottom: 1px solid #334155;
        }}

        tr:hover td {{
            background: rgba(51, 65, 85, 0.5);
        }}

        .footer {{
            text-align: center;
            padding: 20px;
            color: #64748b;
            font-size: 12px;
            margin-top: 24px;
        }}

        @media (max-width: 768px) {{
            body {{
                padding: 12px;
            }}
            .header h1 {{
                font-size: 22px;
            }}
            .stats-grid {{
                grid-template-columns: repeat(2, 1fr);
            }}
        }}
    </style>
</head>
<body>
    <div class="container">
        <!-- Header -->
        <div class="header">
            <h1>Rapport de Stabilite Memoire</h1>
            <p style="color: #94a3b8;">MemoryStabilityBenchmark</p>
            <div class="status-badge">{status_text}</div>
            <div class="meta">
                Genere le {now} &middot; {device['brand']} {device['model']} (SDK {device['sdk']})
            </div>
        </div>

        <!-- Stats Grid -->
        <div class="stats-grid">
            <div class="stat-card">
                <div class="label">Appareil</div>
                <div class="value">{device['brand']}</div>
            </div>
            <div class="stat-card">
                <div class="label">Modele</div>
                <div class="value">{device['model']}</div>
            </div>
            <div class="stat-card">
                <div class="label">SDK Android</div>
                <div class="value">{device['sdk']}</div>
            </div>
            <div class="stat-card">
                <div class="label">Coeurs CPU</div>
                <div class="value">{device['cpu_cores']}</div>
            </div>
            <div class="stat-card">
                <div class="label">Frequence CPU Max</div>
                <div class="value">{format_freq(device['cpu_max_freq'])}</div>
            </div>
            <div class="stat-card">
                <div class="label">RAM Totale</div>
                <div class="value">{format_bytes(device['mem_total'])}</div>
            </div>
        </div>

        <!-- Bar Chart -->
        <div class="chart-container">
            <h2>Compteurs GC - Distribution</h2>
            <div class="chart-wrapper">
                <canvas id="gcChart"></canvas>
            </div>
        </div>

        <!-- Resultats individuels -->
        {''.join(f'''
        <div class="chart-container">
            <h2>[{r['status']}] {r['name']}
                <span style="float: right; font-size: 14px; color: #94a3b8;">
                    Duree : {r['total_time_s']:.1f}s ({r['total_time_min']:.2f} min)
                </span>
            </h2>
            <div class="chart-wrapper">
                <canvas id="chart_{r['name']}"></canvas>
            </div>
        </div>
        ''' for r in results)}

        <!-- Tableau des metriques -->
        <div class="table-container">
            <h2>Metriques detaillees</h2>
            <table>
                <thead>
                    <tr>
                        <th>Test</th>
                        <th>Metrique</th>
                        <th>Mediane</th>
                        <th>Min</th>
                        <th>Max</th>
                        <th>Runs</th>
                    </tr>
                </thead>
                <tbody>
                    {metrics_rows_html}
                </tbody>
            </table>
        </div>

        <div class="footer">
            TACTIC-NAV - Benchmark de Stabilite Memoire &middot; Genere automatiquement
        </div>
    </div>

    <script>
        // Graphique principal : toutes les metriques GC
        const gcCtx = document.getElementById('gcChart').getContext('2d');
        new Chart(gcCtx, {{
            type: 'bar',
            data: {{
                labels: {json.dumps(chart_labels)},
                datasets: [{{
                    label: 'Valeur mediane',
                    data: {json.dumps([d['value'] for d in chart_datasets])},
                    backgroundColor: {json.dumps([d['value'] == 0 and 'rgba(34, 197, 94, 0.7)' or 'rgba(239, 68, 68, 0.7)' for d in chart_datasets])},
                    borderColor: {json.dumps([d['value'] == 0 and '#22c55e' or '#ef4444' for d in chart_datasets])},
                    borderWidth: 2,
                    borderRadius: 6,
                }}]
            }},
            options: {{
                responsive: true,
                maintainAspectRatio: false,
                plugins: {{
                    legend: {{ display: false }},
                    tooltip: {{
                        callbacks: {{
                            label: function(ctx) {{
                                const label = ctx.label || '';
                                const value = ctx.raw;
                                const status = value === 0 ? 'OK - Aucun GC' : 'FAIL - GC detecte';
                                return label + ': ' + value + ' (' + status + ')';
                            }}
                        }}
                    }}
                }},
                scales: {{
                    y: {{
                        beginAtZero: true,
                        grid: {{ color: 'rgba(71, 85, 105, 0.3)' }},
                        ticks: {{ color: '#94a3b8' }}
                    }},
                    x: {{
                        grid: {{ display: false }},
                        ticks: {{ color: '#94a3b8' }}
                    }}
                }}
            }}
        }});

        // Graphiques individuels par benchmark
        {''.join(f'''
        (function() {{
            const ctx = document.getElementById('chart_{r['name']}').getContext('2d');
            const labels = {json.dumps([m['name'] for m in r['metrics']])};
            const values = {json.dumps([m['median'] for m in r['metrics']])};
            const colors = values.map(v => v === 0 ? 'rgba(34, 197, 94, 0.7)' : 'rgba(239, 68, 68, 0.7)');
            const borders = values.map(v => v === 0 ? '#22c55e' : '#ef4444');

            new Chart(ctx, {{
                type: 'bar',
                data: {{
                    labels: labels,
                    datasets: [{{
                        label: 'Valeur mediane',
                        data: values,
                        backgroundColor: colors,
                        borderColor: borders,
                        borderWidth: 2,
                        borderRadius: 6,
                    }}]
                }},
                options: {{
                    responsive: true,
                    maintainAspectRatio: false,
                    indexAxis: 'y',
                    plugins: {{
                        legend: {{ display: false }},
                    }},
                    scales: {{
                        x: {{
                            beginAtZero: true,
                            grid: {{ color: 'rgba(71, 85, 105, 0.3)' }},
                            ticks: {{ color: '#94a3b8' }}
                        }},
                        y: {{
                            grid: {{ display: false }},
                            ticks: {{ color: '#94a3b8', font: {{ size: 11 }} }}
                        }}
                    }}
                }}
            }});
        }})();
        ''' for r in results)}
    </script>
</body>
</html>"""

    with open(output_path, "w", encoding="utf-8") as f:
        f.write(html_content)

    print("[OK] Rapport HTML genere : " + output_path)
    print("[STATUT] " + status_text + " (" + status_icon + ")")
    print("[APPAREIL] " + device['brand'] + " " + device['model'])
    if results:
        print("[DUREE] " + f"{results[0]['total_time_s']:.1f}s")
    print("")
    print("Ouvrez le fichier dans votre navigateur pour visualiser les resultats.")


def find_latest_benchmark_json(benchmark_module_dir: str = "benchmark") -> str:
    """Cherche automatiquement le fichier benchmarkData.json le plus recent dans le module benchmark."""
    import glob

    base_dir = benchmark_module_dir
    search_patterns = [
        os.path.join(base_dir, "build", "outputs", "**", "*benchmarkData.json"),
        os.path.join(base_dir, "build", "outputs", "**", "*benchmark*.json"),
    ]

    candidates = []
    for pattern in search_patterns:
        candidates.extend(glob.glob(pattern, recursive=True))

    if not candidates:
        return None

    # Prendre le plus recent
    candidates.sort(key=os.path.getmtime, reverse=True)
    return candidates[0]


def print_terminal_summary(data: dict):
    """Affiche un resume lisible dans le terminal des resultats du benchmark."""
    device = extract_device_info(data)
    results = extract_benchmark_results(data)

    overall_status = "PASS" if all(r["status"] == "PASS" for r in results) else "FAIL"
    print("")
    print("=" * 72)
    print("  RAPPORT BENCHMARK - STABILITE MEMOIRE")
    print("=" * 72)
    print(f"  Appareil   : {device['brand']} {device['model']} (SDK {device['sdk']})")
    print(f"  CPU        : {device['cpu_cores']} coeurs @ {format_freq(device['cpu_max_freq'])}")
    print(f"  RAM        : {format_bytes(device['mem_total'])}")
    print("-" * 72)

    for res in results:
        status_icon = "[PASS]" if res['status'] == 'PASS' else '[FAIL]'
        print(f"  {status_icon} {res['name']}")
        print(f"     Duree    : {res['total_time_s']:.1f}s ({res['total_time_min']:.2f} min)")

        for m in res['metrics']:
            icon = " OK " if m['median'] == 0 else 'FAIL'
            color = "\033[92m" if m['median'] == 0 else "\033[91m"
            reset = "\033[0m"
            print(f"     [{icon}] {m['name']:45s}  median={color}{m['median']:.2f}{reset}  min={m['minimum']:.2f}  max={m['maximum']:.2f}")

    print("-" * 72)
    final_icon = " ALL PASS " if overall_status == "PASS" else " SOME FAILED "
    print(f" [{final_icon}] RESULTAT GLOBAL : {overall_status}")
    print("=" * 72)
    print("")


def main():
    parser = argparse.ArgumentParser(
        description="Genere un rapport HTML de visualisation des resultats du benchmark memoire."
    )
    parser.add_argument(
        "--json",
        "-j",
        type=str,
        default=None,
        help="Chemin vers le fichier benchmarkData.json (recherche automatique si non specifie)",
    )
    parser.add_argument(
        "--output",
        "-o",
        type=str,
        default=None,
        help="Chemin du fichier HTML de sortie (defaut: rapport_benchmark_<date>.html)",
    )
    parser.add_argument(
        "--terminal",
        "-t",
        action="store_true",
        default=False,
        help="Affiche uniquement un resume dans le terminal, sans generer de HTML",
    )
    args = parser.parse_args()

    # Determiner le chemin du JSON
    json_path = args.json
    if not json_path:
        json_path = find_latest_benchmark_json()
        if not json_path:
            print("[ERREUR] Aucun fichier benchmarkData.json trouve.")
            print("   Utilisez --json <chemin> pour specifier le fichier.")
            sys.exit(1)
        print("[RECHERCHE] Fichier JSON trouve automatiquement : " + json_path)

    if not os.path.exists(json_path):
        print("[ERREUR] Fichier introuvable : " + json_path)
        sys.exit(1)

    # Charger les donnees
    data = load_benchmark_data(json_path)

    # Mode terminal uniquement
    if args.terminal:
        print_terminal_summary(data)
        return

    # Determiner le chemin de sortie
    if args.output:
        output_path = args.output
    else:
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        output_path = "rapport_benchmark_" + timestamp + ".html"

    # Generer le HTML
    generate_html(data, output_path)


if __name__ == "__main__":
    main()