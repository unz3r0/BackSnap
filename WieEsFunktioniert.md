# Wie es funktioniert:
[ReadMe](./backsnap.md),  [english](./HowItWorks.md) 

`backsnap / /mnt/BackSnap/manjaro21`

1. starte BackSnap (aus /usr/local/bin)
4. Pfad zu Quell-Snapshots (sourcedir)
5. Pfad zum Speichern von Backups der Snapshots (destdir)

# Was es macht

## Informationen über sourcedir sammeln
* Alle Dateien im Quellverzeichnis auflisten (/bin/ls sourcedir)
* Filter für numerische Dateinamen
* Speichern in einer numerisch sortierten source-map

## Informationen zum Zielverzeichnis sammeln
* Alle Dateien im Backup auflisten (/bin/ls Zielverzeichnis)
* Filter für numerische Dateinamen
* Speichern in einer numerisch sortierten dest-map

## Snapshots in Source-Map durchlaufen
### Versuche diesen einen Schnappschuss an destdir zu senden
* Wenn er bereits vorhanden ist, überspringen
* btrfs send dieses Snapshot mit Pipe an
* btrfs receive in destdir

#### Führe dabei eine Fehlerprüfung durch
* Unterbrechen, wenn ein Fehler auftritt

Genaueres zur Installation [hier](gallery/gallery.md)