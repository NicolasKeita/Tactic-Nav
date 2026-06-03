<div align="center">

# ✈️ TACTIC-NAV
### Tactical Air Navigation & Centre de Contrôle du Trafic Aérien

*Objectif principal : éviter les collisions entre aéronefs en fournissant une situation tactique partagée et en temps réel.*

*La communication se fait via le protocole ADS-B (Automatic Dependent Surveillance-Broadcast).*

---

![Java](https://img.shields.io/badge/Java-Core%20%2B%20Android%2013-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Protocol](https://img.shields.io/badge/Protocole-UDP%20-0078D4?style=flat-square)
![RAM](https://img.shields.io/badge/Cockpit%20RAM-45%20Mo%20Heap-2ea44f?style=flat-square)
![Latency](https://img.shields.io/badge/Cockpit%20calcul-%3C%2030%20ms-blueviolet?style=flat-square)
![FPS](https://img.shields.io/badge/Rendu-30%20FPS%20min-red?style=flat-square)
![Offline](https://img.shields.io/badge/Carte-100%25%20Offline-lightgrey?style=flat-square)

</div>

---

## 1. Contexte du Projet

Dans le cadre de la modernisation des systèmes avioniques d'aéronefs militaires, le projet **TACTIC-NAV** vise à développer un prototype de système d'affichage tactique et de gestion des flux de données cartographiques en temps réel.

Le système s'appuie sur la technologie **ADS-B** (*Automatic Dependent Surveillance-Broadcast*). Il s'agit d'un système de surveillance coopérative dans lequel les aéronefs déterminent leur position par navigation satellite (GPS) et la diffusent périodiquement par broadcast omnidirectionnel (sans destinataire ciblé) à destination des autres avions et des stations au sol à portée.

Pour modéliser cet écosystème en temps réel, l'architecture du projet est découpée en trois entités distinctes :

*   **Le Terminal Embarqué (Cockpit de l'Avion A)** : Une application Java Android 13 native installée sur tablette tactile. Elle intercepte directement les trames ADS-B diffusées en mode Air-Air par les avions environnants (Avions B, C, D) pour afficher instantanément une situation tactique anti-collision. Pour garantir une autonomie critique totale, le fond de carte ainsi que les limites des zones d'exclusion (*No-Fly Zones*) sont stockés à 100% en local (Offline), éliminant toute dépendance à l'ATC.
*   **Les Stations Sol (Récepteurs)** *(implémentées dans le projet sous la forme d'un programme indépendant)* : Réparties géographiquement, ces antennes captent le segment de diffusion Air-Sol des trames ADS-B émises par le trafic aérien et les retransmettent en continu au centre de contrôle via le protocole UDP.
*   **Le Système de Traitement Central (ATC)** : Un serveur en Java pur (Core) destiné aux opérateurs au sol. Il agrège les flux de données provenant de la multitude de stations sol connectées afin d'offrir une console centralisée de supervision et de contrôle du trafic aérien global.

> 💡 **Le Paradoxe du Contexte Militaire :**
> Dans la vraie vie, les militaires désactivent l'ADS-B en mission opérationnelle car ce signal est public, non chiffré, et facilement falsifiable (*spoofing*). Un aéronef militaire en opération coupe son émission ADS-B pour rester furtif et utilise à la place une **Liaison de Données Tactiques** (comme la *Liaison 16*), hautement sécurisée, chiffrée et résistante au brouillage pour ses échanges Air-Air et Air-Sol. L'implémentation de l'ADS-B dans ce projet sert de démonstrateur technologique civilo-militaire pour valider l'agrégation de données de surveillance et la cartographie dynamique en temps réel.

> 💡 **Note sur le choix de l'UDP & l'émulation radio :**
> Dans le monde réel, l'ADS-B n'utilise ni l'UDP ni le TCP dans le ciel, car il n'y a pas de réseau Internet ou IP entre les avions : les données transitent par des ondes radio pures sur la fréquence 1090 MHz. Dans le cadre de cette simulation informatique, le choix du protocole **UDP** est une analogie logicielle parfaite de la physique radio. Tout comme une onde, l'UDP fonctionne en mode *« Fire and Forget »* (tirer et oublier) : l'aéronef diffuse ses paquets à la volée sans se soucier de savoir qui écoute et sans attendre d'accusé de réception. En cas de perte de paquet, aucune retransmission n'est tentée (contrairement au TCP) car une position géographique reçue en retard est obsolète et dangereuse. Le système attend simplement la trame suivante, garantissant la fluidité et la très basse latence indispensables aux systèmes critiques.

### 📸 Aperçu Global du Système

<table width="100%">
  <tr>
    <td width="33%" align="center"><b>1. Terminal Embarqué (Cockpit)</b></td>
    <td width="33%" align="center"><b>2. Architecture des Flux</b></td>
    <td width="33%" align="center"><b>3. Centre de Contrôle (ATC)</b></td>
  </tr>

  <tr>
    <td>
      <img src="documentation/images/Android_app.png" width="100%">
    </td>
    <td>
      <img src="documentation/images/schema_architecture.png" width="100%">
    </td>
    <td>
      <img src="documentation/images/backlog_backend.png" width="100%">
    </td>
  </tr>

  <tr>
    <td><small><i>Application Android native utilisant le moteur <b>Mapsforge</b> pour un rendu tactique fluide 100% Offline.</i></small></td>
    <td><small><i>Pipeline de données synoptique : du broadcast ADS-B Air-Air direct vers la tablette et du segment Air-Sol via les récepteurs vers l'ATC.</i></small></td>
    <td><small><i>Console Java Core en action : logs en temps réel illustrant l'agrégation des flux et la supervision du trafic.</i></small></td>
  </tr>
</table>

---

## 2. Architecture Technique & Schéma Réseau

Le système repose sur deux composants indépendants communiquant au sein d'un réseau privatif simulé via des flux de données UDP :

- **Le Système de Traitement Central (ATC)** : Une application Java Core développée sans framework lourd (ni Spring, ni Quarkus). Elle écoute en parallèle les flux provenant de plusieurs récepteurs au sol à portée, agglomère les coordonnées des cibles aériennes reçues et assure le suivi global du trafic.
- **Le Terminal Embarqué (Cockpit)** : Une application Android native écrite en Java pur, conçue pour équiper les tablettes tactiles des cockpits afin d'intercepter en direct le trafic environnant et de restituer graphiquement l'environnement tactique anti-collision de manière autonome.

### Schéma des flux réseau

```
[ Avion B ] ───(ADS-B Out/UDP)───► [ COCKPIT (Avion A) : Terminal Android ]
[ Avion C ] ───(ADS-B Out/UDP)───▲          │
[ Avion D ] ───(ADS-B Out/UDP)───┤          │ (Calcul anti-collision local)
│          ▼
│       Moteur SIG Hors-ligne & No-Fly Zones locales
│
▼ (Diffusion Air-Sol)
[ Multiples Récepteurs Sol ] ───(UDP)───► [ ATC : Supervision du Trafic ]
│
Multi-threads d'écoute
Suivi du trafic global

```

## 3. Contraintes de l'Embarqué Critique & Sûreté

Ces contraintes s'appliquent en priorité au **Terminal Embarqué (Cockpit)**, qui doit rester fluide sur tablette Android avec une carte hors-ligne et un rendu temps réel. Le **serveur ATC**, exécuté sur poste sol Java Core, privilégie la séparation des couches, la cohérence des snapshots et une stratégie claire de backpressure UDP.

### Cockpit : maîtrise mémoire et fluidité

Le cockpit limite les allocations dans les boucles de rendu et de calcul géospatial afin d'éviter les pauses visibles dues au *Garbage Collector*. Les structures réutilisables sont pertinentes côté affichage, carte et diagnostic embarqué.

### ATC : robustesse serveur

L'ATC rejette les paquets ADS-B invalides, maintient un modèle de piste cohérent malgré l'ordre non garanti d'UDP, et traite les snapshots de trafic sans bloquer le moteur d'agrégation. Les erreurs réseau ou de parsing sont isolées et journalisées.

### Indicateurs de Performance (KPIs)

| Métrique | Seuil |
|---|---|
| **Temps de démarrage** | Application prête, carte et No-Fly Zones chargées en **< 1.2 seconde** |
| **Empreinte RAM Cockpit** | Consommation stabilisée sous la barre des **45 Mo de Heap** (courbe plate) |
| **Latence Cockpit** | Traitement complet d'un message reçu, calcul géospatial et rendu en **< 30 millisecondes** |
| **ATC** | Agrégation déterministe, snapshots cohérents et traitement UDP non bloquant |

---

## 4. Architecture et Abstraction du Moteur Cartographique

Afin de garantir la compatibilité du système avec les standards cartographiques de la défense (tels que la suite logicielle industrielle **Luciad**), le projet met en œuvre une architecture hautement découplée reposant sur l'**Inversion de Dépendance** (principes SOLID) :

- L'application Android interagit exclusivement avec une interface d'abstraction baptisée `TacticalMapEngine`.
- Pour cette démonstration grand public, l'interface est concrétisée par la bibliothèque open-source **Mapsforge** (ou **Osmdroid**), configurée pour lire localement un fichier de carte pré-téléchargé au format `.map` (région Nouvelle-Aquitaine).
- Cette modularité permet de basculer sur n'importe quel autre SDK cartographique propriétaire par simple injection de dépendance, sans modifier la logique métier sous-jacente.

---

## 5. Spécifications Fonctionnelles & Multithreading

Le système gère le traitement des flux de manière hautement asynchrone :

### Collecte Concurrente (ATC Sol)

Le Système de Traitement Central dédie un thread à chaque flux provenant des récepteurs au sol pour écouter en parallèle et intercepter les données de positionnement sans interférence.

### Pipeline d'Affichage (Cockpit)

| Thread | Rôle |
|---|---|
| **Thread Réseau** | Intercepte en continu les paquets de données ADS-B provenant directement des avions à portée. |
| **Thread de Calcul (Worker)** | Décode les trames et calcule l'intersection géospatiale (algorithme *Point-in-Polygon* reposant sur la formule de **Haversine**) avec les No-Fly Zones stockées localement pour détecter si un aéronef pénètre une zone interdite. |
| **Thread UI** | Consomme les données prêtes pour mettre à jour la carte en maintenant un taux de rafraîchissement fluide de **30 FPS** minimum. |

---

## 6. Stratégie de Tests Automatisés "Anti-Crash"

La stabilité et la sûreté de fonctionnement sont validées par une suite de tests automatisés exigeants :

### Fuzzing de Données Réseau

Un test de stress injecte en continu des données corrompues, tronquées ou hors-normes via des flux concurrents. Le système doit rejeter proprement ces anomalies sans propager de `NullPointerException`, `ArrayIndexOutOfBoundsException` ou erreur réseau fatale vers les traitements applicatifs.

### Robustesse aux Coupures de Flux

Simulation d'une perte totale et intermittente du signal. L'IHM doit maintenir l'affichage cartographique gelé sur le dernier état stable connu, sans aucun blocage du thread d'interface principal.

---

## 7. Validation Finale : "Opération MISTRAL"

Ce protocole valide le comportement du système en conditions réelles d'utilisation à travers le scénario de démonstration appelé **"Opération MISTRAL"**.

### Configuration de l'environnement

🏗️ Architecture de démonstration

✈️ Simulateurs d'aéronefs

- Émettent périodiquement des trames ADS-B en diffusion UDP.
- Représentent les aéronefs présents dans l'espace aérien simulé.

📱 Tablette Android (Cockpit)

- Reçoit directement les émissions ADS-B simulées.
- Affiche les informations de trafic à bord de l'aéronef.
- Fonctionne de manière autonome, sans dépendre du système ATC.

📡 Stations de réception au sol

- Captent les mêmes émissions ADS-B que la tablette Android.
- Représentent des récepteurs de surveillance déployés au sol.

🖥️ Système central ATC

- Agrège les données provenant des stations de réception.
- Assure le suivi des pistes aériennes.
- Fournit une vue globale du trafic aérien simulé.
```
[Aéronefs simulés]
        |
        | ADS-B (UDP)
        |
        +------------------> [Cockpit Android]
        |
        +--> [Station Sol 1] --\
        +--> [Station Sol 2] ----> [ATC Central]
        +--> [Station Sol 3] --/
```
<details>
<summary><b>Phase 1 — Le Démarrage "À Froid"</b> · Vitesse et Autonomie</summary>

| Élément | Description |
|---|---|
| **Action** | Lancement de l'application sur la tablette déconnectée. |
| **Résultat** | Rendu visuel immédiat (< 1s) de la topographie de la Nouvelle-Aquitaine centrée sur la zone aéronautique de Mont-de-Marsan, ainsi que le tracé des No-Fly Zones, de manière 100 % autonome. |
| **Validation** | Le chargement SIG autonome, la lecture des zones d'exclusion locales et la rapidité de démarrage sont validés. |

</details>

<details>
<summary><b>Phase 2 — L'Activation des Flux ADS-B</b> · Concurrence</summary>

| Élément | Description |
|---|---|
| **Action** | Lancement de la simulation de vol des aéronefs environnants. Émission en continu de trajectoires ADS-B de **50 cibles mobiles**. |
| **Résultat** | Les 50 appareils aériens apparaissent et se déplacent de façon fluide et asynchrone directement sur la carte de la tablette grâce au flux Air-Air, tandis que l'ATC affiche la même situation globale grâce aux récepteurs sol. |
| **Validation** | La réception réseau asynchrone par thread dédié et le décodage direct du broadcast fonctionnent sans conflit. |

</details>

<details>
<summary><b>Phase 3 — Manipulation sous Stress</b> · Zéro-Allocation &amp; Fluidité</summary>

| Élément | Description |
|---|---|
| **Action** | Sollicitation intensive et rapide de l'IHM tactile (zooms et déplacements continus de la carte). |
| **Résultat** | L'affichage reste parfaitement réactif (30 FPS constants) sans aucune saccade. Le profileur de performances affiche une consommation mémoire plate et verrouillée sous la barre des 45 Mo. |
| **Validation** | L'efficacité du système de recyclage d'objets (`ObjectPool`) est démontrée. Le *Garbage Collector* n'interrompt jamais l'application. |

</details>

<details>
<summary><b>Phase 4 — L'Alerte d'Intrusion Géospatiale</b> · Calcul SIG</summary>

| Élément | Description |
|---|---|
| **Action** | Le scénario amène une piste ADS-B identifiée comme suspecte (couleur rouge) à franchir la frontière de la zone de non-survol stockée localement. |
| **Résultat** | À la milliseconde précise du franchissement, l'icône de l'appareil clignote intensément et une alerte de sécurité s'affiche instantanément (< 30 ms) sur l'écran du cockpit grâce au calcul embarqué local. |
| **Validation** | Les algorithmes de projection et d'intersection géospatiale à basse latence s'exécutant de manière autonome dans le cockpit sont validés. |

</details>

<details>
<summary><b>Phase 5 — Résilience et Autonomie</b> · Robustesse Sûreté</summary>

| Élément | Description |
|---|---|
| **Action** | Extinction brutale du Système de Traitement Central ATC ou défaillance du segment de réception sol au milieu des opérations. |
| **Résultat** | L'application Android (Cockpit) ne subit aucun impact, ne plante pas et continue de rafraîchir en temps réel la position des avions environnants grâce à la liaison ADS-B Air-Air directe. |
| **Validation** | L'indépendance critique du cockpit vis-à-vis de l'ATC pour les fonctions anti-collision vitales est confirmée. |

</details>