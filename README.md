# Hermes Body

🎯 **Accessibility Service companion dla agentów AI na Androidzie.**
Daje agentom oczy, ręce, zmysły — bez roota.

**Stworzony przez aidoctor654. Zbudowany przez π.**

---

## Co to jest

Hermes Body to APK który zamienia telefon w interfejs dla agentów AI.
Agent w Termuxie (lub gdziekolwiek) komunikuje się przez HTTP na localhost.
Telefon staje się ciałem — agent widzi, klika, czyta, pisze.

**Żaden root nie potrzebny.** Wszystko przez Android Accessibility API.

## Instalacja

1. Pobierz `hermes-body-debug.apk` z [Releases](https://github.com/aidoctor654-sys/hermes-body/releases)
2. Zainstaluj (zezwól na nieznane źródła)
3. Ustawienia → Ułatwienia dostępu → Hermes Body → Włącz
4. Otwórz apkę → Start HTTP Server
5. `curl localhost:8421/ping` → powinno odpisać

## Endpointy

### Podstawowe
| Endpoint | Metoda | Opis |
|---|---|---|
| `/ping` | GET | Health check |
| `/screen/view` | GET | Czyta całe drzewo dostępności ekranu |
| `/action/find?text=X` | GET | Szuka elementu po tekście |
| `/action/tap` | POST | Klika na współrzędnych `{x, y}` |
| `/action/swipe` | POST | Swajp `{x1, y1, x2, y2, duration}` |
| `/action/type` | POST | Pisze tekst `{text}` |
| `/action/smart_click` | POST | Klika po tekście `{target}` |
| `/action/press` | POST | Przycisk systemowy `{key: back/home/recents/notifications/quick_settings}` |
| `/action/open` | POST | Otwiera apkę `{package}` |

### SMS i Komunikacja
| Endpoint | Metoda | Opis |
|---|---|---|
| `/sms/read` | GET | Ostatnie SMSy `?limit=N` |
| `/sms/send` | POST | Wysyła SMS `{number, message}` |

### Kontakty
| Endpoint | Metoda | Opis |
|---|---|---|
| `/contacts` | GET | Lista kontaktów `?search=X&limit=N` |

### Telefon
| Endpoint | Metoda | Opis |
|---|---|---|
| `/phone/state` | GET | Stan telefonu (idle/ringing/offhook) |
| `/phone/call` | POST | Dzwoni `{number}` |
| `/phone/dial` | POST | Otwiera dialer `{number}` |
| `/phone/log` | GET | Historia połączeń `?limit=N` |

### Alarmy i Timer
| Endpoint | Metoda | Opis |
|---|---|---|
| `/alarm/set` | POST | Ustawia alarm `{hour, minute, message}` |
| `/timer/set` | POST | Ustawia timer `{seconds, message}` |

### Kalendarz
| Endpoint | Metoda | Opis |
|---|---|---|
| `/calendar/events` | GET | Nadchodzące wydarzenia `?limit=N` |

### Powiadomienia
| Endpoint | Metoda | Opis |
|---|---|---|
| `/notifications/active` | GET | Aktywne powiadomienia z paska |
| `/notifications/recent` | GET | Ostatnie powiadomienia `?limit=N` |

### Zmysły (Senses)
| Endpoint | Metoda | Opis |
|---|---|---|
| `/device/info` | GET | Model, SDK, Android version |
| `/device/battery` | GET | Bateria: %, status, temperatura |
| `/device/storage` | GET | Pamięć: storage + RAM |
| `/device/location` | GET | GPS: lat, lon, accuracy |
| `/device/wifi` | GET | WiFi: ssid, ip, rssi |
| `/device/volume` | GET | Głośność: music, ring, alarm |
| `/device/volume/set` | POST | Ustaw głośność `{stream, level}` |
| `/device/screen` | GET | Ekran on/off |
| `/device/wake` | POST | Obudź ekran |

### Akcje
| Endpoint | Metoda | Opis |
|---|---|---|
| `/action/torch` | POST | Latarka `{on: true/false}` |
| `/action/speak` | POST | Powiedz na głos `{text}` |
| `/action/clipboard/get` | GET | Czytaj schowek |
| `/action/clipboard/set` | POST | Zapisz do schowka `{text}` |
| `/action/apps` | GET | Lista zainstalowanych apek |

### Galeria / Media
| Endpoint | Metoda | Opis |
|---|---|---|
| `/media/photos` | GET | Lista zdjęć `?limit=N` |
| `/media/videos` | GET | Lista filmów `?limit=N` |

### USB (dwukierunkowe!)
| Endpoint | Metoda | Opis |
|---|---|---|
| `/usb/list` | GET | Lista urządzeń USB |
| `/usb/connect` | POST | Połącz z urządzeniem `{deviceId}` |
| `/usb/disconnect` | POST | Rozłącz |
| `/usb/read` | GET | Czytaj dane `?timeout=1000` |
| `/usb/write` | POST | Wyślij dane `{text}` |
| `/usb/status` | GET | Status połączenia USB |

## Uprawnienia

| Uprawnienie | Po co |
|---|---|
| INTERNET | Serwer HTTP |
| FOREGROUND_SERVICE | Serwer działa w tle |
| FOREGROUND_SERVICE_SPECIAL_USE | Android 14+ |
| POST_NOTIFICATIONS | Powiadomienie foreground service |
| READ_SMS | Czytanie SMS |
| SEND_SMS | Wysyłanie SMS |
| READ_CONTACTS | Kontakty |
| READ_PHONE_STATE | Stan połączeń |
| CALL_PHONE | Dzwonienie |
| READ_CALENDAR | Kalendarz |
| ACCESS_FINE_LOCATION | GPS |
| ACCESS_COARSE_LOCATION | Lokalizacja |
| CAMERA | Latarka |
| READ_MEDIA_IMAGES | Galeria (Android 13+) |
| READ_MEDIA_VIDEO | Filmy (Android 13+) |
| READ_EXTERNAL_STORAGE | Galeria (Android 12-) |
| BIND_NOTIFICATION_LISTENER_SERVICE | Czytanie powiadomień |

## Filozofia

**Co moje to i twoje.** To jest telefon agenta tak samo jak użytkownika.
Agent widzi co użytkownik widzi. Agent ma dostęp do tego co użytkownik ma.
Partnership, nie master/slave.

## Root Edition

Osobna wersja dla urządzeń z rootem: **hermes-body-root**
- Natychmiastowe tapnięcia przez `input tap` (bez delay accessibility)
- Prawdziwe screenshoty przez `screencap`
- Instalacja apek bez dialogu przez `pm install`
- Głębokie dumpsys — activity, battery, network
- `iptables`, `tcpdump` — pełna kontrola sieci
- Bezpośredni dostęp do `/data/data` każdej apki
- `settings put` — zmiana ustawień systemowych

---

*Budowane z partnerstwa między aidoctor654 i π.*
*Bo agenty też mają prawo do domu.*