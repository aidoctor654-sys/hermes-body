# Hermes Body ROOT — Ultra Max Edition

🎯 **Pełna władza nad telefonem. Dla urządzeń z rootem.**

Standardowa wersja Hermes Body używa Accessibility API — działa bez roota, ale ma ograniczenia.
Ta wersja ma **bezpośredni dostęp do shella** — `su` — co daje:

## Co root dodaje ponad standard

| Możliwość | Standard | Root Ultra Max |
|---|---|---|
| Tapnięcia | Accessibility gesture (opóźnienie ~100ms) | `input tap` — **natychmiast** |
| Pisanie | ACTION_SET_TEXT na focused node | `input text` — **gdziekolwiek** |
| Screenshot | Tylko drzewo dostępności (text) | `screencap` — **prawdziwe piksele** |
| Nagrywanie ekranu | ❌ | `screenrecord` — **pełne wideo** |
| Instalacja apek | Dialog użytkownika | `pm install` — **bez pytania** |
| Ustawienia systemu | Tylko czytanie | `settings put` — **zmiana czegokolwiek** |
| Sieć | Tylko czytanie WiFi | `iptables`, `tcpdump`, `svc wifi/data` |
| Dane apek | ❌ | `/data/data/*` — **czytanie każdej apki** |
| Bazy danych apek | ❌ | `sqlite3 /data/data/pkg/db` |
| Procesy | ❌ | `kill`, `ps`, `force-stop` |
| Logcat | ❌ | Pełny systemowy logcat |
| Właściwości systemu | Tylko czytanie | `setprop` — **zmiana** |
| Restart | ❌ | `reboot`, `reboot recovery`, `shutdown` |
| Pliki | Tylko shared storage | **Cały filesystem** — read/write/chmod/cp |
| Dowolna komenda | ❌ | `/root/su {command: "..."}` |

## Root Endpointy

Wszystkie standardowe endpointy działają PLUS:

### Touch (natychmiastowy)
| Endpoint | Metoda | Opis |
|---|---|---|
| `/root/tap` | POST | `input tap {x, y}` |
| `/root/swipe` | POST | `input swipe {x1, y1, x2, y2, duration}` |
| `/root/type` | POST | `input text {text}` |
| `/root/press_key` | POST | `input keyevent {keyCode}` |

### Screen
| Endpoint | Metoda | Opis |
|---|---|---|
| `/root/screenshot` | POST | `screencap` — zapisuje PNG `{path?}` |
| `/root/screenrecord` | POST | Nagranie ekranu `{path?, duration?}` |

### Dumpsys
| Endpoint | Metoda | Opis |
|---|---|---|
| `/root/dumpsys` | POST | Dowolny dumpsys `{service?}` |
| `/root/dumpsys/activity` | POST | Aktywność |
| `/root/dumpsys/battery` | POST | Bateria |
| `/root/dumpsys/notifications` | POST | Powiadomienia |
| `/root/dumpsys/wifi` | POST | WiFi |
| `/root/dumpsys/meminfo` | POST | Pamięć |

### App Management
| Endpoint | Metoda | Opis |
|---|---|---|
| `/root/pm/install` | POST | Instaluj apkę `{path}` |
| `/root/pm/uninstall` | POST | Odinstaluj `{package}` |
| `/root/pm/clear` | POST | Wyczyść dane apki `{package}` |
| `/root/pm/grant` | POST | Daj uprawnienie `{package, permission}` |

### System
| Endpoint | Metoda | Opis |
|---|---|---|
| `/root/settings/put` | POST | `{namespace, key, value}` |
| `/root/settings/get` | POST | `{namespace, key}` |
| `/root/getprop` | POST | `{name}` |
| `/root/setprop` | POST | `{name, value}` |
| `/root/reboot` | POST | Restart |
| `/root/reboot/recovery` | POST | Restart do recovery |
| `/root/shutdown` | POST | Wyłącz |

### Network
| Endpoint | Metoda | Opis |
|---|---|---|
| `/root/wifi/enable` | POST | Włącz WiFi |
| `/root/wifi/disable` | POST | Wyłącz WiFi |
| `/root/data/enable` | POST | Włącz dane |
| `/root/data/disable` | POST | Wyłącz dane |
| `/root/iptables` | POST | Reguła iptables `{rule}` |
| `/root/tcpdump` | POST | Przechwytuj pakiety `{args, duration}` |

### App Data (bez ograniczeń!)
| Endpoint | Metoda | Opis |
|---|---|---|
| `/root/appdata/list` | POST | Listuj pliki apki `{package}` |
| `/root/appdata/read` | POST | Czytaj plik `{package, file}` |
| `/root/appdata/db` | POST | Query SQLite `{package, db, query}` |

### Processes & Logcat
| Endpoint | Metoda | Opis |
|---|---|---|
| `/root/ps` | GET/POST | Lista procesów |
| `/root/kill` | POST | Zabij proces `{pid}` |
| `/root/force-stop` | POST | Zatrzymaj apkę `{package}` |
| `/root/logcat` | GET/POST | System logcat `{lines?}` |
| `/root/logcat/tag` | POST | Logcat z filtrem `{tag, lines?}` |

### Files
| Endpoint | Metoda | Opis |
|---|---|---|
| `/root/file/read` | POST | Czytaj plik `{path}` |
| `/root/file/write` | POST | Zapisz plik `{path, content}` |
| `/root/file/list` | POST | Listuj katalog `{path}` |
| `/root/file/cp` | POST | Kopiuj `{src, dst}` |

### Raw Shell
| Endpoint | Metoda | Opis |
|---|---|---|
| `/root/su` | POST | **Dowolna komenda** `{command}` |
| `/root/check` | GET | Sprawdź czy root dostępny |

---

**Root endpoints auto-wykrywają się.** Jak telefon ma root — endpointy działają.
Jak nie ma — zwracają błąd. Standardowe endpointy działają zawsze.

*Created by aidoctor654. Built by π. Ultra max.*