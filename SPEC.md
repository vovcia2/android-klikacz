# SPEC.md — ClickLog (Android, CAT S75)

## 1. Cel

Aplikacja na Android (CAT S75, Android 12+) rejestrująca naciśnięcia sprzętowego
przycisku SOS (*Programmable Key*) z dokładnym znacznikiem czasu.

Priorytety: (1) niezawodność zapisu kliknięć, (2) dokładność timestampu,
(3) praca w tle przy zgaszonym ekranie, (4) aplikacja **sama** diagnozuje,
jak system dostarcza przycisk — użytkownik nie ma dostępu do komputera z adb.

## 2. Zakres MVP

### 2.1 Zliczanie kliknięć
- Każde `ACTION_DOWN` przycisku programowalnego = jeden wpis w bazie.
- Zapis: `id`, `wall_time_ms` (epoch), `uptime_ms` (`KeyEvent.eventTime`, null dla
  ścieżki B), `source` (`KEY_EVENT` | `LAUNCH_INTENT`), `session_id`.
- Timestamp liczony z czasu zdarzenia jądra, nie z chwili obsługi:
  `wall = System.currentTimeMillis() - (SystemClock.uptimeMillis() - event.eventTime)`.
- Debounce: ignoruj kolejne `ACTION_DOWN` < 150 ms po poprzednim (auto-repeat).
- Obsługiwany tylko `ACTION_DOWN`; `ACTION_UP` ignorowany (ale logowany w trybie diagnostyki).

### 2.2 UI (minimalne)
- **Ekran główny:** licznik kliknięć w bieżącej sesji, ostatni timestamp (z ms),
  status usługi, status uprawnień, aktywna ścieżka przechwytywania.
- **Lista zdarzeń:** wszystkie kliknięcia z sesji, od najnowszych.
- **Ustawienia:** „Nowa sesja", „Eksport CSV", okno debounce, wybór ścieżki
  (AUTO / A / B).
- **Diagnostyka przycisku** — sekcja 3.
- Eksport: `clicks.csv` przez `ACTION_CREATE_DOCUMENT` lub share sheet.
  Kolumny: `id, session_id, wall_time_iso, wall_time_ms, uptime_ms, source`.

## 3. Diagnostyka przycisku — wbudowana w aplikację

Przycisk SOS konfiguruje się w ustawieniach systemu (*Programmable Key* /
*Przycisk programowalny*). Nie wiadomo z góry, czy aplikacja zobaczy go jako
`KeyEvent` (i z jakim keycode), czy tylko jako uruchomienie aplikacji przez `Intent`.
Aplikacja musi to ustalić sama, bez adb.

### 3.1 Kreator pierwszego uruchomienia (`SetupWizard`)
Krok po kroku, z instrukcjami na ekranie:

1. **Instrukcja konfiguracji systemu** — tekst + przycisk otwierający ustawienia
   (`Settings.ACTION_SETTINGS`; jeśli uda się znaleźć bezpośredni Intent do ekranu
   *Programmable Key*, użyć go, w przeciwnym razie ogólne ustawienia). Poprosić o:
   przypisanie przycisku do ClickLog, wyłączenie domyślnej akcji SOS/połączenia alarmowego.
2. **Włączenie usługi dostępności** — przycisk do `ACTION_ACCESSIBILITY_SETTINGS`
   z instrukcją; aplikacja wykrywa, czy `ClickAccessibilityService` jest włączona.
3. **Test przycisku** — ekran „Naciśnij teraz przycisk SOS 3 razy". Aplikacja
   nasłuchuje jednocześnie na wszystkich kanałach (3.2) i pokazuje na żywo,
   co dociera. Po 3 naciśnięciach lub 30 s podsumowuje wynik i zapisuje
   wykrytą konfigurację.
4. **Test przy zgaszonym ekranie** — „Zgaś ekran, naciśnij przycisk 3 razy,
   odblokuj telefon". Aplikacja sprawdza, ile kliknięć zarejestrowano w tym czasie.
5. **Wynik:** wybrana ścieżka (A lub B), keycode, czy działa przy zgaszonym ekranie.
   Zapis w DataStore. Kreator można uruchomić ponownie z ustawień.

### 3.2 Kanały nasłuchu podczas diagnostyki
Wszystkie aktywne równocześnie, każde zdarzenie trafia do `diagnostic_log`
(tabela: `ts_ms`, `channel`, `keycode`, `scancode`, `action`, `intent_action`,
`extras_json`, `screen_on`):

- `Activity.onKeyDown/onKeyUp` (ekran testowy w foreground) — `keyCode`, `scanCode`,
  `source`, `deviceId`, `device.name`.
- `AccessibilityService.onKeyEvent` — to samo, plus flaga, czy ekran był zgaszony.
- `onNewIntent` / `onCreate` z Intentem w `MainActivity` + eksportowana `LaunchReceiverActivity`
  (przezroczysta, kończy się natychmiast) — `action`, `categories`, `extras`,
  `referrer`.
- `BroadcastReceiver` zarejestrowany dynamicznie na wildcard-owe akcje producenta:
  listę kandydatów trzymać w jednym pliku (`VendorActions.kt`), np. akcje zawierające
  `bullitt`, `cat`, `ptt`, `programmable`, `sos`, `hardkey`. Jeśli dotrze cokolwiek
  pasującego — zalogować pełny Intent.
- `MediaSession` z `MediaButtonReceiver` — na wypadek, gdyby system mapował
  przycisk na media key.

### 3.3 Reguła wyboru ścieżki (AUTO)
1. Jeśli `AccessibilityService.onKeyEvent` dostał ≥ 3 zdarzenia z tym samym keycode
   → **ścieżka A**, zapamiętaj keycode.
2. Inaczej, jeśli `Activity.onKeyDown` dostał zdarzenia, ale AS nie → ścieżka A nie
   przechwytuje tego klawisza; pokaż komunikat i użyj **B**.
3. Inaczej, jeśli były Intenty uruchomienia → **ścieżka B**.
4. Inaczej → komunikat „Nie wykryto przycisku" z checklistą (czy przypisany w
   ustawieniach, czy usługa dostępności włączona) i możliwością powtórzenia testu.

### 3.4 Ekran diagnostyki (dostępny zawsze z ustawień)
- Podgląd `diagnostic_log` na żywo.
- Eksport logu do pliku tekstowego (share sheet) — żeby użytkownik mógł go
  przesłać dalej bez komputera.
- Przycisk „Wyczyść log".

## 4. Ścieżki przechwytywania (wspólny interfejs `ClickSource`)

| Ścieżka | Mechanizm | Plusy | Minusy |
|---|---|---|---|
| A. `KEY_EVENT` | `AccessibilityService.onKeyEvent()` (`canRequestFilterKeyEvents=true`), filtruje zapamiętany keycode | tło, zgaszony ekran, dokładny `eventTime` | wymaga włączenia usługi dostępności |
| B. `LAUNCH_INTENT` | przycisk uruchamia `LaunchReceiverActivity`; klik = Intent | działa, gdy system pozwala tylko „uruchom aplikację" | +30–100 ms latencji, budzi ekran, brak `eventTime` (użyć `currentTimeMillis` najwcześniej jak się da — w `onCreate`) |

Obie zaimplementowane; aktywna wybierana przez wynik kreatora lub ręcznie.
W ścieżce A zdarzenie z zapamiętanym keycode jest konsumowane (`return true`),
żeby system nie wykonał dodatkowo domyślnej akcji.

## 5. Praca w tle

- `ForegroundService` (`ClickLogService`) z trwałą notyfikacją pokazującą licznik;
  trzyma `ClickProcessor` i zapis do Room. W ścieżce A sam AS wystarczyłby,
  ale FGS daje widoczny status i odporność na ubicie procesu.
- Prośba o wyłączenie optymalizacji baterii (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
- Restart po `BOOT_COMPLETED`, jeśli usługa była włączona.
- Zapis do bazy synchroniczny w wątku IO z kolejką — klik nie może zginąć,
  jeśli proces zostanie ubity chwilę po nim.

## 6. Uprawnienia

- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` (z deklaracją `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`),
  `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`.
- `BIND_ACCESSIBILITY_SERVICE` (ścieżka A).
- Brak mikrofonu, kamery, lokalizacji.

## 7. Stack techniczny

- Kotlin, minSdk 31, targetSdk aktualny.
- Jetpack Compose, Room, DataStore (ustawienia + wynik diagnostyki).
- Hilt.
- Logi przez `Timber`; w trybie diagnostyki także do `diagnostic_log` w Room.

## 8. Struktura

```
app/
  clicks/       ClickSource (interfejs), KeyEventClickSource, IntentClickSource,
                ClickProcessor (debounce), ClickRepository
  diagnostics/  SetupWizard, DiagnosticLogger, VendorActions, DiagnosticScreen
  service/      ClickLogService (FGS), ClickAccessibilityService, BootReceiver,
                LaunchReceiverActivity
  data/         AppDatabase, ClickDao, DiagnosticDao, Settings (DataStore)
  ui/           MainScreen, EventsScreen, SettingsScreen
  export/       CsvExporter
```

## 9. Testy

- Unit: `ClickProcessor` — debounce (149/151 ms), konwersja `eventTime → wall`,
  kolejność zapisu.
- Unit: reguła wyboru ścieżki (3.3) dla wszystkich kombinacji wejść.
- Checklista ręczna na S75 (wyniki wpisać do `TESTING.md`):
  - [ ] kreator wykrywa przycisk i wybiera ścieżkę
  - [ ] 100 kliknięć przy zgaszonym ekranie → 100 wpisów, brak duplikatów
  - [ ] odchylenie timestampu vs. zewnętrzny zegar < 50 ms (ścieżka A)
  - [ ] działa po restarcie telefonu
  - [ ] długie przytrzymanie nie uruchamia połączenia alarmowego
  - [ ] eksport CSV i logu diagnostycznego działa z share sheet

## 10. Poza zakresem

- Nagrywanie audio/wideo, podwójne kliknięcie i inne gesty, synchronizacja
  w chmurze, wielu użytkowników.

## 11. Otwarte pytania

1. Czy system Cat/Bullitt pozwala przypisać do *Programmable Key* dowolną
   aplikację (ścieżka B), czy tylko z listy predefiniowanej? — ustali kreator.
2. Czy `AccessibilityService` widzi ten klawisz (niektóre producenci przechwytują
   go wcześniej)? — ustali kreator.
