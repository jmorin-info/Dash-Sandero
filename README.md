# OBDash

Tableau de bord OBD-II temps reel pour Dacia Sandero 1.0 SCe 73 (B4D), sur autoradio
Android (tablette 2 DIN) relie en USB a un Vgate vLinker FS (pont FTDI, 115200 bauds).

## Moteur allume ou coupe ? (important)

Deux familles de fonctions, deux reponses :

- **LECTURE (dashboard, params Renault temps reel, DTC)** : concue pour **moteur allume**,
  c'est meme son mode principal. Regime, vitesse, MAP, temps d'injection, richesse, compteurs
  de rates/cliquetis, couple estime... tout cela n'a de sens qu'en roulant.
- **TESTS ACTIONNEURS (onglet Carrosserie : clignotants, condamnation, ventilation...)** :
  **non, a faire moteur coupe / contact mis / vehicule a l'arret**. Ce n'est pas une limite de
  l'app : le calculateur lui-meme conditionne ces sorties (il refuse la commande avec un negatif
  UDS si les conditions ne sont pas reunies) et on ne manipule pas les ouvrants en roulant.
  L'app pose d'ailleurs un bandeau d'avertissement permanent sur cet onglet.

## Design

Direction "combine d'instruments" : eclairage de cadran retro-eclaire. Ambre chaud pour le
compte-tours et les instruments, blanc-glace froid pour la vitesse (contraste chaud/froid),
fond charbon quasi noir. Chiffres en chasse fixe **tabulaire** (pas de tremblement de largeur).
- Compte-tours signature : cadran gradue, aiguille avec leger rebond, zone rouge qui pulse
- **Balayage d'allumage** : a la connexion, l'aiguille balaie toute l'echelle et revient
  (self-test, comme un vrai cluster a la mise du contact)
- Afficheurs numeriques qui glissent, courbe de conso temps reel (debit L/h sur 60 s),
  point "live" pulse, transitions animees entre onglets, navigation ambre
- **Anneau de vitesse numerique** : chiffre geant tabulaire au centre d'un cadran gradue (sans aiguille = lecture moderne), tachy a aiguille a cote
- **Modes de conduite Normal / Sport / Eco** : re-teintent l'accent de tout le tableau (ambre / rouge / vert menthe)
- **Jauges stylisees** conso instantanee et temperature d'eau (arc colore + valeur centree, zones froid/normal/chaud)
- **Historique des trajets** persiste (date, duree, distance, conso moyenne, V.max, cout) + synthese globale, dans l'onglet Trajets
- Telemetrie etendue : avance allumage, corrections carburant STFT/LTFT, temps depuis demarrage, temp. ambiante, **tension calculateur (PID 42) en plus de la tension adaptateur**, distance avec MIL, autonomie estimee


## Ce que fait la v1

- Connexion ELM327 auto (init ATZ/ATE0/ATS0/ATH0, protocole auto ATSP0, detection des PIDs supportes 0100/0120)
- Dashboard temps reel : vitesse, regime (jauge), T° eau, T° air admission, MAP, papillon, charge,
  tension batterie (ATRV), jauge carburant, barometre
- **Conso instantanee et moyenne en speed-density** (le SCe 73 n'a pas de MAF) :
  debit d'air estime a partir de MAP + regime + T° admission + VE + cylindree, puis L/h et L/100
- Totaux trajet : km, litres, cout (prix carburant parametrable), RAZ
- **G-metre** via l'accelerometre de la tablette (montage fixe = mesures propres)
- **DTC** : lecture modes 03 (confirmes) + 07 (en attente), descriptions FR, effacement mode 04 avec confirmation
- **Terminal** AT/OBD brut (ATRV, 010C, 0902 pour le VIN, etc.) + journal de session
- **Overlay** : mini-jauges flottantes (vitesse, conso, T° eau) affichees PAR-DESSUS Waze en plein ecran, deplacables
- Boutons **Waze** (plein ecran) et **Waze 1/2 ecran** (split-screen a cote du dash)
- **Demarrage auto au boot** (option) : la tablette reboote a la mise du contact -> reconnexion OBD automatique
- Lancement auto au branchement USB du vLinker (filtre VID FTDI 0x0403)
- Ecran maintenu allume tant que l'app est au premier plan
- **Mode simulation** (Reglages) : faux ECU integre pour tester sans voiture
- UI adaptative : 2 colonnes sur ecran large (autoradio paysage), 1 colonne sur telephone

## Module Renault (onglet dedie)

Diagnostic constructeur UDS du calculateur injection **Continental EMS3140** (Sandero II X52,
1.0 SCe B4D), parametres extraits de la base DDT (EMS3140_0C00_..._V1.4), en **lecture seule** :

- Session etendue (10 03) avec tester-present automatique (3E 00), CAN 7E0/7E8
- ~33 parametres avec les facteurs d'echelle exacts de la base : temps d'injection par cylindre,
  correction et adaptatifs de richesse, couple effectif estime, compteurs de rates de combustion
  et de cliquetis par cylindre, VVT admission (position/consigne), consigne de ralenti,
  charge en air cylindre, GMV, purge canister, tensions/pressions haute resolution...
- Identification calculateur (F187 ref piece, F190 VIN, F18C n° serie, calibration, versions soft)
- DTC constructeur via 19 02 (status mask AF) avec decodage statut actif/en attente/confirme
- Chaque parametre se coche pour entrer dans le polling (entrelace avec l'OBD standard,
  3 lectures UDS par cycle)

NB : la base DDT contient plusieurs variantes EMS3140/EMS3141 selon le millesime ; en cas de
valeurs incoherentes, verifier l'identification (bouton Ident) et ajuster la table RenaultDb.kt.

## Module Carrosserie (tests actionneurs)

Commandes d'actionneur de diagnostic (issues de la base DDT), en **ecriture sur le bus** :

- **BCM / UCH** (adr 26, CAN 745/765, service KWP 30) : feux de detresse / clignotants,
  condamnation portes, super-condamnation, condamnation par zone, autorisation leve-vitres
- **ClimBox / HVAC** (adr 29, CAN 744/764, service UDS 2F) : pulseur avant/arriere, recyclage
  d'air, volet pieds, requete compresseur, sieges chauffants, volant chauffant, ioniseur
- **Tableau de bord** (adr 51, CAN 743/763, service 30) : allumer tous les voyants, balayage des
  aiguilles, buzzer, tests eclairage/afficheur

Aucun de ces deux calculateurs n'expose de SecurityAccess (27) : les tests passent apres une
simple session etendue (10 C0), maintenue par tester-present automatique.

**Garde-fous cablees dans l'app** :
- Bandeau d'avertissement permanent (contact mis, MOTEUR COUPE, vehicule a l'arret)
- Le mode atelier suspend le polling OBD tant qu'il est actif
- Bouton "COUPER TOUT + fermer session" qui rend chaque actionneur au calculateur et referme les sessions
- Sondage automatique BCM/HVAC a l'activation : un calculateur absent (ex. clim manuelle a
  molettes, frequente sur SCe 73 de base) s'affiche "absent" et ses commandes sont grisees

**Limites importantes** :
- Ce sont des tests momentanes : le calculateur reprend la main a la fermeture de session.
  Ce n'est PAS un pilotage permanent ni du "drive-by-wire", et rien ne doit etre commande en roulant.
- "Autorisation leve-vitres" active les commandes de vitres, il ne monte/descend pas la vitre a distance
  (la base ne fournit pas de commande de moteur de vitre directe sur ce BCM).
- Peut generer des DTC (effacables dans l'onglet DTC).
- Angle securite : c'est faisable ici precisement parce que ce BCM T4 n'a pas de SecurityAccess
  sur l'output control ; les plateformes recentes (diagnostic securise / SFD) bloquent ce type d'acces.

## Build

Android Studio (Koala ou plus recent) : ouvrir le dossier, sync Gradle, Run.
En CLI : `./gradlew :app:assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`

- AGP 8.5.2, Kotlin 2.0.20, Compose BOM 2024.09, min SDK 26, target SDK 34
- Lib serie USB : `com.github.mik3y:usb-serial-for-android:3.7.0` (JitPack)

## Premiere mise en route

1. Installer l'APK sur l'autoradio (`adb install` ou copie USB + installation manuelle).
2. Tester d'abord en **mode simulation** (Reglages -> Mode simulation, puis Connecter).
3. Brancher le vLinker FS USB sur le port USB de la tablette (port host sur les autoradios),
   prise OBD sous le volant, contact mis. Accepter la permission USB (cocher "toujours").
4. Accorder la permission **Affichage par-dessus les autres applis** pour l'overlay,
   et activer **Demarrage auto au boot** dans les Reglages.
5. Waze s'installe directement sur l'autoradio (necessite les services Google/Play Store
   sur la tablette) : bouton Waze + Overlay = navigation avec jauges par-dessus.

## Calibrage de la conso (important)

La conso speed-density depend du VE (rendement volumetrique), inconnu precisement :
1. Plein a ras bord, RAZ du trajet.
2. Rouler normalement jusqu'au plein suivant (a ras bord aussi).
3. Nouveau VE = VE actuel x (litres reels a la pompe / litres affiches par OBDash).
Apres 2-3 pleins, l'ecart passe typiquement sous ~5 %. AFR ~14.1 en E10.

## Limites v1 / pistes v2

- Polling sequentiel (~6-10 Hz sur le groupe rapide) ; le batch multi-PID CAN (jusqu'a 6 PIDs/trame) triplerait la cadence
- Pas encore de persistance des trajets (Room), ni d'export CSV/GELF
- Chrono 0-100 / 80-120 exploitant vitesse OBD + G-metre
- Alertes a seuils (T° eau, tension) avec TTS

Ecrit pour un usage lecture seule (mode 01/03/07 + effacement 04). Aucune ecriture calculateur.

## Journal v6 — audit, optimisation, features

### Bugs corriges
- **Commandes actionneurs inoperantes** : parsing casse (`p.size < 6` sur 5 champs) -> aucune
  commande ne partait. Corrige (destructuring 5 champs).
- **PIDs 0x42 / 0x46 / 0x5E jamais lus sur voiture reelle** : le bitmap de support 0140 n'etait
  pas interroge, donc tension calculateur, temp. ambiante et surtout la detection du fuel-rate
  (0x5E) etaient invisibles hors simulation. Corrige (0100 + 0120 + 0140).
- **Trames stop du tableau de bord erronees** (aiguilles/buzzer/eclairage/afficheur) : alignees
  sur la base DDT.
- **Autonomie** : calculee sur un reservoir fige a 50 L -> utilise desormais le reservoir des reglages.

### Optimisations
- **Cache d'en-tete CAN** : `ATSH`/`ATCRA` ne sont renvoyes que si la cible change (avant, ils
  etaient repetes a chaque commande UDS/atelier) -> nette reduction du trafic ELM.
- **Reconnexion automatique** : boucle de (re)connexion avec detection de perte de liaison
  (20 cycles sans donnee) sans clore le trajet en cours.
- **Cadence maitrisee** : delai minimal par cycle -> plus de spin CPU en simulation ou sur coupure.

### Nouvelles fonctions
- **Conso precise via temps d'injection** (UDS EMS3140) : bien plus exacte que le speed-density et
  gere nativement la coupure a la deceleration (ti ~ 0 -> conso ~ 0). Debit injecteur calibrable.
  Le tableau de bord indique la source (INJECTION en vert / ESTIMEE).
- **Alertes vocales (TTS)** : surchauffe moteur et tension batterie basse, avec hysteresis, + bandeau
  visuel clignotant. Desactivable.
- **Trace GPS des trajets** (optionnelle) : distance et vitesse max recoupees par le GPS, enregistrees
  dans l'historique. Permission de localisation demandee au 1er lancement.

## Journal v7 — chrono performance + controle technique

- **Page Performance** (2e page du tableau de bord, glisser horizontal) : chrono base sur la
  vitesse OBD avec interpolation lineaire du franchissement des seuils. Detection automatique du
  depart arrete (0-50, 0-100, 400 m avec vitesse de passage) et de la reprise 80-120. Records
  persistes, effacables. Indicateur de pages en bas.
- **Preparation controle technique** (onglet DTC) : lecture des moniteurs de preparation OBD
  (Mode 01 PID 01) - etat MIL, nombre de DTC, et etat Pret / Non pret de chaque moniteur d'emission
  (catalyseur, sonde lambda, EVAP, rates de combustion...). Verdict global PRET / NON PRET.

## Journal v8 — rapport engage, simulateur UDS, export CSV

- **Indicateur de rapport engage** au centre du tableau de bord (entre vitesse et compte-tours),
  estime par le ratio tr/min / vitesse. Rapports de boite configurables dans les Reglages
  (tr/min par km/h, defaut SCe 73 : 110/62/43/33/27). Affiche N au point mort / embrayage.
- **Simulateur enrichi** : repond desormais a l'UDS (sessions, temps d'injection, parametres
  Renault, identification), aux bitmaps 0140/0101 et aux commandes d'actionneur. Tous les onglets
  (Renault, Actionneurs, conso precise par injection, controle technique) sont donc demontrables
  en mode simulation, sans voiture.
- **Export CSV de l'historique** des trajets (bouton Exporter dans l'onglet Trajets), partage via
  la feuille de partage Android (FileProvider). Colonnes date/duree/distance/GPS/litres/moy/vmax/cout.
- Correction d'incoherence : suppression de la tuile "Regime" redondante avec le compte-tours,
  remplacee par le debit instantane (L/h).

## Journal v9 — enrichissement de l'existant

- **Overlay enrichi** : ajoute le rapport engage et le regime a la mini-fenetre flottante, palette
  alignee sur l'app (vitesse blanc-glace, rapport ambre, conso vert).
- **Records de session** sur le tableau de bord : V.max, regime max, temperature d'eau max et
  tension mini depuis le debut du trajet (remis a zero avec le trajet).
- **Terminal** : rangee de commandes rapides (ATRV, ATDPN, 010C, 0101, 0902 VIN...) en un clic.
- **Reglages** reorganises en sections (Systeme / Affichage & alertes / Consommation / Vehicule)
  pour la lisibilite.

## Installation

### Option A — installer l'APK (le plus simple)
1. Copier `OBDash-v8-debug.apk` (ou la derniere version) sur la tablette : cle USB, cable, ou
   telechargement direct sur la tablette.
2. Sur la tablette : Parametres > Securite > autoriser "Sources inconnues" (ou, a l'installation,
   autoriser l'explorateur de fichiers a installer des applications).
3. Ouvrir le fichier .apk avec l'explorateur de fichiers et confirmer l'installation.
4. Au 1er lancement, accorder : notifications, localisation (pour le GPS), et "affichage
   par-dessus les autres applis" (pour l'overlay). Brancher le vLinker FS en USB et accepter la
   permission USB (cocher "toujours").
5. Tester d'abord en **mode simulation** (Reglages > Mode simulation) : tous les onglets
   fonctionnent sans voiture.

### Option B — installer par ADB (depuis un PC)
```
adb install -r OBDash-v8-debug.apk
```

### Option C — compiler depuis les sources
```
# decompresser OBDash-v8-src.zip, puis :
./gradlew :app:assembleDebug
# APK genere dans app/build/outputs/apk/debug/app-debug.apk
```
Ou ouvrir le dossier dans Android Studio (Koala+) et lancer Run.

## Journal v10 — trajets approfondis

- **Vue detaillee par trajet** : tape un trajet dans l'onglet Trajets pour l'ouvrir. Stats
  completes (duree, distance OBD et GPS, vitesse moyenne/max, carburant, conso moyenne, cout).
- **Trace GPS de la route** : si la trace GPS etait active, le parcours est dessine (projection
  lon x cos(lat), degrade depart vert -> arrivee ambre, points de depart/arrivee).
- **Alerte carburant bas** ajoutee aux alertes vocales et au bandeau (seuil ~8 %, avec hysteresis).

## Journal v11 — DTC enrichi + harmonisation de l'affichage

### Onglet DTC enrichi
- **Distinction confirme / en attente** : les modes 03 et 07 ne sont plus fusionnes, chaque code
  porte un badge (Confirme en rouge / En attente en ambre) et une barre de couleur laterale par
  systeme (P groupe motopropulseur, C chassis, B carrosserie, U reseau).
- **En-tete voyant moteur** : bandeau MIL allume/eteint bien visible + nombre de DTC.
- **Image figee (freeze frame, mode 02)** : bouton dedie qui lit les conditions du moteur au
  moment ou le defaut a ete memorise (regime, vitesse, charge, temperatures, MAP, papillon) et
  le code qui a declenche le gel.
- Verdict preparation controle technique conserve.

### Harmonisation de l'affichage (tous les modules)
- **DTC, Renault, Terminal** alignes sur le design system du tableau de bord : etiquettes en
  petites capitales espacees, valeurs en chasse fixe tabulaire, cartes a filet fin.
- **Terminal** : journal colore (commandes envoyees en ambre, succes en vert, erreurs/refus en
  rouge, reste en gris).
- **Simulateur** : repond au mode 02, donc le freeze frame est demontrable sans voiture.

## Journal v12 — passe UI/UX complete

- **Kit d'interface partage** (UiKit) : en-tetes d'ecran, cartes, boutons, pastilles d'etat et
  barre d'activite, reutilises partout pour un rendu homogene.
- **En-tetes coherents** sur tous les ecrans (Diagnostic, Diagnostic Renault, Actionneurs,
  Trajets, Terminal, Performance, Reglages) : titre + sous-titre, meme traitement typographique.
- **Retour visuel des actions longues** : une barre d'activite indeterminee s'affiche pendant la
  lecture des codes, l'image figee, l'ouverture de session, l'identification, la detection des
  calculateurs (plus de "clic sans reaction").
- **Navigation epuree** : le libelle n'apparait que sous l'onglet actif (7 onglets moins charges).
- **Onglet Actionneurs entierement restyle** : bandeau d'avertissement, pastilles joignable/absent,
  cartes de commande coherentes, bouton de coupure globale en rouge.
- **Reglages, Renault, DTC, Terminal** alignes sur le meme systeme (petites capitales, valeurs
  tabulaires, filets fins, journal colore).

L'optimisation (performances, trafic ELM, memoire) fera l'objet de la passe suivante.

## Journal v13 — passe d'optimisation complete

### Trafic ELM (le plus gros levier)
- **Requetes multi-PID** : au lieu d'interroger les PID un par un, une seule requete
  "01 0C 0D 0B 11 ..." rapatrie tout le groupe rapide d'un coup. **5 allers-retours -> 1**.
  Detection automatique de la capacite au 1er cycle, repli transparent sur lecture individuelle
  si le calculateur ne gere pas (parseur multi-trame ISO-TP robuste, resync sur "41",
  gestion du padding). Groupe lent regroupe en 1 bloc / 8 cycles -> donnees 6x plus fraiches.

### Memoire / GC
- **Une seule copie d'etat par cycle** : le decodage des PID construit un unique VehicleState
  (au lieu d'enchainer ~17 copies via .copy()). ~17x moins d'allocations sur la boucle chaude.
- **Pics emis seulement au changement** : plus de re-emission (et de recomposition) inutile du
  bandeau records a chaque cycle.

### Rendu Compose
- **Page Performance** : refactoree autour d'un StateFlow d'instantane. Elle ne se recompose plus
  a chaque cycle mais uniquement quand le chrono change (silencieuse au ralenti). La vitesse live
  n'entre dans l'instantane que pendant une mesure.
- **Overlay** redessine a ~5 Hz (1 cycle sur 4) au lieu de chaque cycle.
- Navigation deja optimisee (label sur onglet actif), cadence de boucle calibree (~15-20 Hz).

Ces gains ciblent les autoradios Android d'entree de gamme (CPU limite) : moins d'attente serie,
moins de pression GC, moins de recompositions.

## Journal v14 — mode nuit + enregistreur graphique

- **Mode nuit** (Reglages > Affichage) : Off / Nuit / Auto (21h-7h, reevalue chaque minute).
  Filtre d'attenuation global au-dessus de toute l'interface (navigation comprise), intensite
  reglable de 15 a 75 %. N'intercepte pas le tactile. Pense aux trajets de nuit sur autoradio.
- **Enregistreur graphique** (3e page du tableau de bord, glisser horizontal) : 4 canaux
  superposes en temps reel — regime (ambre), vitesse (glace), MAP (bleu), papillon (vert) —
  normalises pleine echelle, ~2 minutes glissantes a ~3 Hz, grille 0-100 %, legende avec valeurs
  courantes, Pause / Effacer. L'echantillonnage ne tourne que quand la page est affichee
  (cout nul sinon). Outil de diagnostic visuel : trou a l'acceleration, chute de MAP,
  papillon incoherent se reperent d'un coup d'oeil.

## Journal v15 — theme clair, personnalisation, canaux selectionnables + passe accents

- **Passe accents** : toutes les chaines d'interface passent en francais correctement accentue
  (Regime -> Regime avec accents, Deconnecte, controle technique, temperature, etc.), design system
  compris. Encodage UTF-8 verifie, aucun impact sur le code (identifiants en anglais).
- **Theme clair** (Reglages > Affichage > Theme clair) : palette bi-theme commutable. Le design
  system est refondu en getters lisant un etat instantane -> bascule immediate de toute l'interface,
  schema Material clair/sombre inclus (interrupteurs, sliders, dialogues suivent).
- **Personnalisation du tableau de bord** (Reglages > Tableau de bord) : afficher/masquer la courbe
  de debit, les records de session, les parametres moteur etendus (STFT/LTFT/uptime/dist MIL).
- **Enregistreur** : 8 canaux **selectionnables** par puces (regime, vitesse, MAP, papillon, charge,
  avance, tension, eau) + bascule **echelle auto** (min/max par canal, signal faible lisible) vs
  **pleine echelle** (amplitudes comparables). Legende dynamique des canaux actifs.

## Journal v16 — refonte design PAYSAGE (cockpit)

Reponse aux critiques : format tablette 9" horizontal + identite visuelle affirmee.

- **Verrouillage paysage** : l'app est fixee en landscape (head unit fixe).
- **Navigation en rail vertical** a gauche (au lieu d'une barre en bas) : adapte au 16:9.
- **Tableau de bord repense en cockpit 3 colonnes** : bandeau de regime type "shift-light"
  en haut (segments ambre->rouge en zone rouge) + statut ; a gauche l'anneau de vitesse
  signature (halo diffus, cercle-lentille, graduations majeures/mineures) ; au centre le
  rapport engage + jauges conso/eau + parametres moteur ; a droite conso moyenne, trajet,
  bloc electrique & moteur, records de session, bandeau d'alerte.
- **Nouvelle identite** : palette graphite profond + signal **cyan-ion** (fini l'ambre retro et
  le vert acide generiques ; l'ambre est reserve aux avertissements). Typographie **Chakra Petch**
  (terminaisons carrees, chiffres a caractere telemetrie) sur toute l'interface. Cartes a fond
  tonal (moins de contours, plus de respiration).
- Le **bi-theme** (jour/nuit), les 3 pages du tableau de bord (cockpit / performance /
  enregistreur) et toutes les fonctions precedentes sont conservees.

## Journal v17 — ecrans secondaires repenses en paysage

- **Trajets en maitre-detail** : liste a gauche (synthese globale + cartes de trajet, selection
  surlignee), detail complet a droite (titre, trace GPS, 8 indicateurs). Plus de bascule
  plein-ecran : tout est visible d'un coup, la carte de route occupe enfin la largeur.
- **DTC en deux colonnes** : a gauche les boutons + en-tete voyant + liste des codes ; a droite
  l'image figee (freeze frame) et l'etat de preparation au controle technique cote a cote.
- **Reglages en deux colonnes** : Systeme / Affichage & alertes a gauche ; Tableau de bord /
  Consommation / Vehicule a droite. Beaucoup moins de defilement.

## Journal v17 — tous les ecrans repenses en paysage

Suite de la refonte : les ecrans secondaires exploitent enfin la largeur du 16:9.

- **Diagnostic (DTC)** en 2 colonnes : a gauche le bandeau voyant moteur + la liste des codes
  (barre de couleur par systeme, badge confirme/en attente) ; a droite l'image figee (grille 3
  colonnes) et la preparation au controle technique (moniteurs sur 2 colonnes, verdict PRET).
  Le dialogue d'effacement adopte le theme.
- **Actionneurs** en 2 colonnes : a gauche la consigne de securite et l'etat des calculateurs
  (adresses CAN + pastilles joignable/absent) ; a droite les commandes en **grille de tuiles**
  entierement cliquables (cible large, adaptee au tactile en voiture), pastille d'etat par tuile.
- **Trajets** en **maitre/detail** : la liste a gauche (avec synthese globale et marqueur GPS),
  le detail du trajet selectionne a droite en permanence — trace GPS en grand (le trace occupe
  toute la hauteur disponible) et 8 statistiques en grille. Plus besoin de naviguer en arriere.
- **Cockpit** : la courbe de debit instantane reintegree sous les parametres moteur.
- **Reglages** deja en 2 colonnes.

## Journal v18 — derniers ecrans paysage, immersif, icone

- **Diagnostic Renault** repense en 2 colonnes : a gauche la **selection des parametres** par
  groupe (lignes cliquables a pastille) ; a droite un panneau segmente **Valeurs live**
  (grille 3 colonnes de tuiles telemetrie) / **Identification** / **DTC constructeur**. Les
  lectures d'identification et de DTC se declenchent a l'ouverture de l'onglet.
- **Console** en 2 colonnes : le journal (fond profond, monospace couleur) + champ d'envoi avec
  bouton ENVOYER integre a gauche ; a droite les **commandes rapides documentees** par groupe
  (Adaptateur / OBD / UDS EMS3140) avec leur description — on sait ce qu'on envoie.
- **Mode immersif** : les barres systeme Android sont masquees (plein ecran total sur le head
  unit) et se rappellent d'un glissement depuis le bord.
- **Icone d'application** : adaptive icon vectorielle a l'identite du projet — anneau de vitesse
  cyan-ion, aiguille, moyeu, fond graphite degrade (apercu icon_preview.png).

## Journal v19 — sequence d'allumage, calibration a chaud, blocs reorganisables

- **Sequence d'allumage** (au lancement, une fois par session) : self-test facon combine
  d'instruments — les segments de regime s'allument en cascade, l'anneau balaie sa pleine
  echelle en affichant la vitesse defiler, le nom s'inscrit, puis tout se retracte et s'efface
  en fondu vers le cockpit. Signature du produit, pas une decoration.
- **Calibration a chaud** (bouton CALIBRER sur le cockpit) : apres un plein, saisis les litres
  reellement mis a la pompe et la distance parcourue. L'app compare a son estimation, affiche
  l'ecart en %, et corrige **automatiquement le bon parametre** — le debit injecteur si la conso
  precise est active, sinon le rendement volumetrique (VE). Fini le calcul manuel.
- **Blocs du cockpit reorganisables** (bouton ORGANISER) : l'ordre des cartes de la colonne
  droite (conso / trajet / electrique) se change par fleches monter-descendre — cibles larges,
  fiables au doigt en voiture, contrairement a un glisser-deposer. L'ordre est persiste.

## Journal v20 — direction « Sirius » : esprit competition, discipline

Refonte du design system dans un esprit sport automobile, mais tenue en bride.

### Couleur — le jaune comme outil, pas comme teinture
- Fond **asphalte** quasi noir, panneaux **carbone**. Le **jaune Sirius (#FFD100)** est employe
  chirurgicalement : un seul element jaune par zone, celui qui porte la valeur qui compte
  (la conso moyenne, l'anneau de vitesse, le bloc actif). Partout ailleurs : craie, gris, noir.
- La gradation **jaune -> orange -> rouge course** est celle d'un shift-light : elle porte du
  sens (normal / attention / danger) au lieu de decorer. L'orange remplace l'ambre pour rester
  distinct du jaune d'accent.
- Modes de conduite alignes : Normal = Sirius, Sport = rouge course, Eco = vert technique.

### Forme — le biseau
- **Tous les coins arrondis sont remplaces par des coins BISEAUTES** (54 formes converties).
  Les plaques de carbone, les badges de course et les afficheurs de competition sont coupes en
  diagonale, jamais arrondis : c'est le detail qui separe un instrument de course d'une app.
- Deux familles : **plaque** (4 coins coupes) pour les panneaux, **lame** (2 coins opposes) pour
  les boutons, tags et elements actifs — la diagonale donne une lecture directionnelle.
- **Segments de regime carres** (plus d'arrondis) et arcs a bouts francs : lecture instrument.
- **Hachures de danger** sur le bandeau d'avertissement des actionneurs et les alertes actives ;
  **lisere lateral** d'accent sur les blocs cles.

### Typographie — deux largeurs, aucune couleur en plus
- **Saira Condensed** pour les etiquettes, titres et boutons : condense + capitales espacees,
  comme un marquage de carrosserie.
- **Chakra Petch** conserve pour les chiffres d'instrument (geometrie carree, figures tabulaires).
- Le contraste de largeur entre les deux porte la hierarchie sans ajouter une seule couleur.

### Icone
- Alignee sur la direction : anneau Sirius, aiguille craie, fond asphalte.

## Journal v21 — design assume + fonctions sport

### Matiere et profondeur
- **Grain de carbone** (trame diagonale croisee ~2 % d'intensite) et **vignette** sur les fonds
  du cockpit et de la page performance : on ne les voit pas, on les sent — une surface, pas un aplat.
- **Anneau de vitesse** de vrai instrument : **graduations chiffrees** (0..200 en Chakra Petch),
  **marqueur du pic de session** (trace rouge fin, memoire de l'aiguille), **reflet specular**
  discret facon verre. Segments carres, arcs a bouts francs.

### Fonctions sport
- **Assistant de passage de rapport** : conseil ▲ PASSER / ▼ RÉTROGRADER sous l'anneau, cale sur
  le B4D (couple ~3500, puissance ~6250) et **dependant du mode** — Éco monte a ~2100, Normal
  ~2700, Sport garde le regime jusqu'a ~5900. C'est ce qui rend les modes reellement actionnables.
- **G-metre inertiel** (page Performance, colonne dediee) : accelerometre de la tablette,
  cible graduee 0.25/0.5/0.75/1 g, point vif, **trainee** des dernieres secondes, et **memoire
  des pics** acceleration / freinage / virage — comme une centrale d'acquisition de course.
- Page Performance repensee en 2 colonnes (chrono | G-metre) pour exploiter le 16:9.

### Captures
- Les 9 ecrans rendus fidelement (01_cockpit ... 09_reglages).

## Journal v23 — animations sport, rev-matching, G-metre calibre, alerte perimetrique

### Animations (toutes a cout conditionnel : rien ne tourne quand ce n'est pas visible)
- **Strobe de zone rouge** : au-dela de ~5600 tr/min, les segments rouges du shift-light
  clignotent a ~11 Hz avec halo, comme un vrai shift-light de competition. La transition
  n'est composee QUE en zone rouge — cout strictement nul le reste du temps.
- **Rapport anime** : au changement de rapport, le chiffre glisse verticalement (vers le haut
  en montant, vers le bas en retrogradant) avec fondu — facon afficheur de boite sequentielle.
- **Compteur fluide** : le chiffre de vitesse suit la meme valeur animee que l'anneau (ressort
  critique) au lieu de sauter de 3-4 km/h entre deux trames OBD.

### Fonctions
- **Rev-matching** : le conseil de rapport affiche maintenant le REGIME CIBLE apres changement
  (vitesse x demultiplication du rapport vise, arrondi aux 50 tr). Au retrogradage c'est le
  regime a atteindre au talon-pointe pour un passage sans a-coup. Utilise les demultiplications
  reglables (Reglages > Vehicule), defaut SCe 73.
- **Calibration zero du G-metre** (bouton ZERO sur le panneau) : capture l'inclinaison de
  montage de la tablette comme reference, persistee. Indispensable si la dalle n'est pas
  parfaitement verticale dans la planche.
- **Alerte perimetrique** : en cas d'alerte critique (surchauffe, tension...), un lisere rouge
  pulse autour de TOUT l'ecran, quel que soit l'onglet actif. Compose uniquement en alerte.

### Optimisation
- **G-metre throttle** : le capteur tourne a ~50 Hz mais l'etat Compose n'est emis qu'a ~30 Hz
  max ; les pics sont accumules entre deux emissions (rien n'est perdu). ~40 % de
  recompositions en moins sur un panneau visible en continu.

## Journal v24 — fluidite structurelle, degrades, personnalisation des instruments

### Fluidite (le gain le plus important depuis le multi-PID)
- **Collecte du flux vehicule scopee au tableau de bord** : `ObdRepository.state` (qui change
  ~15 fois/s) etait collecte a la racine de l'app -> TOUT l'arbre (rail de navigation,
  transitions, ecran actif quel qu'il soit) recomposait a chaque trame OBD. Il est desormais
  collecte dans le tableau de bord uniquement : la Console, les Reglages, les Trajets... sont
  au repos complet quand ils sont affiches. Sur un head unit modeste, c'est la difference
  entre "ca rame parfois" et "c'est fluide partout".

### Degrades — propres, porteurs de sens
- **Barre de regime en degrade CONTINU** : interpolation jaune -> orange -> rouge le long de la
  barre (plus de paliers durs a 72 %/86 %). La montee en regime se lit comme une rampe
  thermique. Segments toujours carres (identite instrument).
- **Echelle gravee sous la barre** : reperes 1..6 (x1000 tr), chiffres rouges en zone rouge,
  trait rouge au seuil — la barre devient un instrument complet, pas un simple bargraphe.
- **Anneau de vitesse en degrade balaye** : l'arc s'eclaircit vers sa pointe (sweep gradient
  aligne sur l'origine de l'arc) — profondeur discrete, zero surcharge.

### Personnalisation (Reglages > Instruments)
- **Pleine echelle vitesse** (120-300 km/h), **pleine echelle regime** (4000-9000),
  **debut de zone rouge** (3000-8500) et **strobe debrayable**. Les graduations chiffrees de
  l'anneau et l'echelle de la barre suivent automatiquement. Le seuil "Sport" de l'assistant
  de rapport se cale desormais sur la zone rouge personnalisee (redline + 300 tr).

### Confort tactile
- Pastilles d'action et poignees de reorganisation agrandies (cibles ~40 dp), bandeau de
  regime rehausse pour accueillir l'echelle.

## Journal v25 — benchmark des references + parametres vehicule-exact (base DDT)

### Analyse des references (Torque Pro, DashCommand, Infocar, Car Scanner)
Les avis convergent sur ce qui fait un grand dashboard : personnalisation profonde des jauges,
dyno/skid-pad (mesure des G et de la puissance), lecture de couple, scores de conduite. On avait
deja le G-metre, les chronos, la personnalisation des instruments et l'enregistreur multi-canaux.
Ajout de la piece maitresse manquante cote "performance ressentie" :

- **Dyno inertiel** (page Performance) : puissance a la roue estimee en direct, facon "dyno" de
  Torque/DashCommand. P = (m.a + pertes roulement + trainee aero) x v ; a vient de l'accelerometre
  (comme le G-metre), v de l'OBD, m est la masse saisie (Reglages > Vehicule). Affiche kW + ch et
  conserve le PIC. Indicatif (route plane, pleine charge) mais l'ordre de grandeur et le pic sont
  fiables — et desormais recoupables avec le couple reel du calculateur.

### Parametres SPECIFIQUES a ton vehicule (extraits de ecu.zip / base DDT EMS3140)
Fouille du fichier DDT EMS3140_0C00 (1974 requetes, echelles step/offset lues telles quelles).
Ajout de 8 parametres live que les apps generiques n'exposent PAS, avec decodage exact :

- **Couple moteur modelise** (Nm, signe — negatif en frein moteur) : le vrai couple vilebrequin
  estime par le calculateur. Plus "Couple indique brut".
- **Position pedale accelerateur** (%), distincte de l'ouverture papillon.
- **Consigne de ralenti** (tr/min), **Temperature d'huile** (°C, modelisee si pas de capteur),
  **Distance depuis derniere vidange** (km), **Demarrages cumules**.
- **Embrayage** et **Point mort** (codes d'etat 2 bits) — utiles sur ta boite manuelle.

Honnetete : certains DID existent dans le soft du calculateur mais dependent de l'equipement
reel (capteur huile, cablage embrayage/point mort). Sur ta config exacte, quelques-uns peuvent
renvoyer une valeur modelisee ou figee — a confirmer en roulant. Les echelles, elles, sont exactes.

## Journal v26 — couple ECU trace, score de conduite par trajet

- **Canal COUPLE dans l'enregistreur** (9e canal, teinte cuivre) : trace le couple vilebrequin
  modelise par l'EMS3140 (DID 2037, signe). Alimente quand la session Renault est ouverte —
  superposer COUPLE + PAPILLON + REGIME transforme l'enregistreur en banc d'analyse moteur.
  Le simulateur genere un couple plausible pour la demo.
- **Score de conduite par trajet** (detail de trajet) : note 0-100 calculee a la cloture depuis
  des compteurs tenus en roulant — accelerations franches (papillon > 85 %, detection sur front),
  freinages appuyes (decel > 3 m/s² sur la vitesse OBD), temps en zone rouge, et surconsommation
  vs la reference du SCe 73 (~5.5 L/100). Penalites ramenees a la distance (un long trajet n'est
  pas desavantage). Barre coloree vert / orange / rouge + detail des compteurs.
  Retro-compatible : les trajets archives avant la fonction n'affichent pas de score.

## Journal v27 — revue de code, personnalisation complete, logging

### Revue de code : 5 corrections
1. **Fuite des compteurs de style** : un trajet avorte (< 100 m) sortait de finalizeTrip AVANT
   la remise a zero -> les accelerations/freinages fuyaient dans le trajet suivant. Corrige
   (resetStyleCounters systematique).
2. **Pulse du ShiftBanner permanent** : l'infinite transition tournait meme banniere cachee
   (recompositions constantes du cockpit). Deplacee DANS le bloc visible : cout nul au repos.
3. **Couleurs de l'enregistreur figees a l'init** : les canaux capturaient les teintes une seule
   fois -> theme clair et changement d'accent casses. Les canaux sont desormais construits a
   l'appel et suivent theme + accent, avec variantes assombries lisibles en clair.
4. **Journal sans horodatage** : chaque ligne (hors echo de trame) est prefixee HH:mm:ss.
5. **Grain carbone invisible en clair** (trame blanche sur fond clair) : contraste selon le theme.
   Bonus : pulse du point de statut compose seulement quand connecte.

### Personnalisation depuis l'interface (Reglages > Affichage)
- **6 teintes d'accent** au choix (Sirius, Ion, Course, Circuit, Nuit, Cuivre), selecteur a
  pastilles biseautees. Chaque teinte a sa variante assombrie pour le theme clair (contraste
  garanti). TOUTE l'interface suit instantanement : anneau, barre de regime, rail, boutons,
  liseres, canal Regime de l'enregistreur — zero code, tokens reactifs.
- **Texture carbone debrayable**, **sequence d'allumage debrayable**.
- (Deja la : theme clair/sombre, mode nuit auto + intensite, echelles d'instruments, zone rouge,
  strobe, blocs reorganisables, canaux selectionnables.)

### Logging & exports
- Journal horodate, plafonne a 400 lignes (pas de fuite memoire sur long roulage).
- **Exporter le journal** (Console) : fichier texte horodate via la feuille de partage Android.
- **CSV des trajets enrichi** : score eco, accelerations franches, freinages, secondes en zone rouge.

### Animations
- **Transitions d'onglets directionnelles** : glisse laterale discrete dans le sens de la
  navigation + fondu (au lieu du simple fondu).

## Journal v28 — chassis multi-calculateurs : 4 roues, volant, odometre reel

Fouille elargie de la base DDT (ecu.zip) aux AUTRES calculateurs du X52 — pas seulement
l'injection. Adresses CAN lues telles quelles dans le champ obd de chaque fichier :
ABS/ESC X10.52.79.98 = 740/760, Cluster X52 = 743/763.

### Nouvelles lectures vehicule-exact (lecture seule, service 22)
- **Vitesses des 4 roues** (ABS, DID 4B00-4B03, x0.01 km/h) : rangee AVG/AVD/ARG/ARD sur la
  page Performance, avec **detection de patinage** — une roue qui s'ecarte de plus de 2 km/h
  de la mediane passe orange (utile aussi pour reperer un pneu sous-gonfle a vitesse stable).
- **Angle volant** (ABS, DID 0100, x0.1 deg, signe) affiche a cote des roues.
- **Odometre officiel du tableau de bord** (Cluster, DID 0207, 3 octets) : lu a la connexion
  puis toutes les ~4 min, affiche discretement dans le statut du cockpit ("ODO 84 512 KM").
  C'est le kilometrage AFFICHE au combine — celui qui fait foi.

### Integration boucle & simulation
- La boucle bascule l'en-tete CAN (ATSH/ATCRA) vers l'ABS ou le cluster puis le repositionne
  immediatement sur le moteur. Cout : ~2 allers-retours ELM par lecture, d'ou la cadence
  volontairement lente (roues ~1 lecture/12 cycles, odometre quasi-statique).
- Le simulateur repond aux DIDs chassis (roues = vitesse +/- ecart realiste, volant sinusoidal,
  odometre 84 512 km) : la demo sans voiture montre tout.
- Fluidite preservee : la rangee des roues collecte le flux LOCALEMENT (comme le cockpit),
  le reste de la page Performance ne recompose pas au rythme OBD.

## Journal v29 — auto-diagnostic de compatibilite vehicule

Reponse a la question "est-ce que tout marchera sur MA voiture ?" : l'app le mesure elle-meme.

- **Verifier la compatibilite vehicule** (Reglages > Systeme) : sequence de tests en LECTURE
  SEULE qui valide chaque sous-systeme sur le vehicule branche — adaptateur ELM (tension),
  protocole CAN (ATDPN), OBD-II standard (0100), identite de l'injection (ref. piece F187),
  acceptation des DID DDT (2037), ABS 4 roues (avec repli automatique en session etendue 10C0
  si la variante l'exige), odometre du combine, et presence du BCM (session atelier ouverte
  puis refermee, AUCUN actionneur declenche).
- Rapport en direct ligne par ligne : pastille verte / orange / rouge + detail mesure, verdict
  final "N/8 sous-systemes valides". Relancable. Resultats aussi traces dans le journal.
- C'est l'outil qui transforme les niveaux de confiance theoriques en faits mesures en ~30 s.

### Captures ajoutees
- 10_verification (rapport), 11_cockpit_ion / 12_cockpit_course / 13_cockpit_clair /
  14_cockpit_nuit (variantes de personnalisation).

## Journal v30 — performance de rendu, identite du rail, UX hors ligne

### Performance visuelle (architecture de dessin)
- **Instruments en deux couches** : l'anneau de vitesse et la barre de regime sont scindes en
  une couche STATIQUE (drawWithCache : lentille, arc de fond, graduations chiffrees, echelle
  x1000, reflet) et une couche DYNAMIQUE (arcs de progression, halo, segments, pic).
  Consequence : les mesures de texte et la geometrie des graduations, qui etaient recalculees
  A CHAQUE FRAME d'animation (~60 fps en roulant), ne sont plus construites qu'une fois par
  taille/theme/echelle. C'est le plus gros gain de rendu depuis le scoping des recompositions.

### Design — le dernier element "stock" remplace
- **Pictogrammes maison dans le rail** : les icones Material generiques (maison, etoile,
  cadenas...) sont remplacees par 7 glyphes vectoriels dessines au trait dans le langage de
  l'app — jauge a aiguille, losange diagnostic, eclair, trace de route, triangle, terminal,
  engrenage. Aucun asset, pur Canvas, teinte suivant l'etat et l'accent choisi.

### UX de l'etat hors ligne
- **Respiration du bouton CONNEXION** (composee uniquement hors connexion : cout nul en
  roulant) : l'oeil sait ou aller au premier lancement.
- **Invite contextuelle** sous les modes : "Branche le vLinker, contact mis · ou active la
  simulation" — et si la simulation est deja armee, le texte le dit.

### Captures
- Les 14 captures regenerees avec le nouveau rail (9 ecrans + rapport + 4 variantes).

## Journal v31 — identite complete, tracabilite base DDT, proprete

### Logos & marque (100 % vectoriel, zero asset)
- **Wordmark OBDash** (LogoMark) : picto jauge — arc 270° + aiguille, celui de l'icone — +
  lettrage Saira espace. Utilise dans la sequence d'allumage (remplace le texte brut) et le
  pied de page des Reglages. Teinte par l'accent choisi.
- **Icone de notification dediee** (ic_stat_obdash, monochrome 24 dp) : la notification du
  service de fond affiche la jauge OBDash au lieu du glyphe generique.
- **Icone themable Android 13+** (couche monochrome dans l'adaptive icon) : le launcher peut
  teinter l'icone aux couleurs du fond d'ecran (Material You) sans perdre la forme.

### Tracabilite "liee a la base"
- **Reglages > A propos & sources** : table de provenance affichant, pour chaque calculateur,
  le fichier DDT exact (nom + version + annee), les adresses CAN et ce qui en est utilise.
  Objet DdtSources dans le code : si un comportement etonne, la source se retrouve en un regard.

### Proprete
- versionCode 31 / versionName "1.0 (v31)" + constante AppInfo.VERSION affichee dans l'app.
- Suppression des 9 imports d'icones Material devenus morts apres le passage aux pictos maison.
- Note d'environnement : BuildConfig genere du Java et la chaine javac de ce conteneur est
  cassee (JdkImageTransform) — contourne proprement par une constante Kotlin, l'app reste
  100 % Kotlin sans source generee.
