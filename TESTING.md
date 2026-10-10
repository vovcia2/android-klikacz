# TESTING.md — checklista ręczna na CAT S75

Wyniki wpisywać w kolumnie *Wynik* (data, wersja aplikacji, uwagi).

## Testy automatyczne

```
./gradlew testDebugUnitTest
```

- `ClickProcessorTest` — debounce 149/151 ms, granica okna, konwersja `eventTime → wall`,
  kolejność i zawartość zapisu, `reset()`.
- `PathSelectorTest` — reguła wyboru ścieżki (§3.3) dla wszystkich kombinacji wejść,
  liczniki naciśnięć (na żywo i przy zgaszonym ekranie).
- `CsvExporterTest` — nagłówek i format wierszy `clicks.csv`.

## Checklista na S75

| # | Test | Kroki | Oczekiwane | Wynik |
|---|---|---|---|---|
| 1 | Kreator wykrywa przycisk i wybiera ścieżkę | Pierwsze uruchomienie → kroki 1–5 kreatora, w kroku 3 nacisnąć SOS 3× | Krok 3 pokazuje zdarzenia na żywo i podsumowanie z wybraną ścieżką (A lub B) i keycode; wynik zapisany, na ekranie głównym „Aktywna ścieżka” ≠ brak | ✅ 2026-10-10, v0.1.5: 3/3 naciśnięć, usługa dostępności 3 · Activity 3 · Intenty 0; wybrano ścieżkę A, keycode 291 |
| 2 | 100 kliknięć przy zgaszonym ekranie | Zgasić ekran, nacisnąć SOS 100× (odstępy > 150 ms), odblokować | Licznik sesji wzrósł dokładnie o 100; lista zdarzeń bez duplikatów (różnice czasu > okno debounce) | |
| 3 | Odchylenie timestampu < 50 ms (ścieżka A) | Nagrać filmem telefon + zewnętrzny zegar z ms (np. time.is) przy naciśnięciu; porównać `wall_time_ms` z CSV | Różnica < 50 ms | |
| 4 | Działa po restarcie telefonu | Z włączoną usługą zrestartować telefon, nie otwierać aplikacji, nacisnąć SOS | Notyfikacja usługi wraca po starcie; kliknięcie zarejestrowane | |
| 5 | Długie przytrzymanie nie uruchamia połączenia alarmowego | Przytrzymać SOS 3 s | Brak połączenia alarmowego / ekranu SOS (ścieżka A konsumuje klawisz; w ścieżce B wymaga wyłączenia akcji SOS w ustawieniach systemu) | |
| 6 | Eksport CSV i logu diagnostycznego | Ustawienia → Eksport CSV → „Udostępnij” oraz „Zapisz plik”; Diagnostyka → ikona udostępniania | Share sheet otwiera się, plik ma nagłówek `id,session_id,wall_time_iso,wall_time_ms,uptime_ms,source`; `diagnostic_log.txt` zawiera wpisy | |
| 7 | Debounce | Ustawić 1000 ms, kliknąć 2× w ciągu 0,5 s | Zarejestrowane 1 kliknięcie | |
| 8 | Nowa sesja | Ustawienia → Nowa sesja | Licznik = 0, nowy `session_id`, stare wpisy w „Eksport CSV (wszystkie sesje)” | |
| 9 | Optymalizacja baterii | Główny ekran → „Napraw” przy optymalizacji baterii | Systemowy dialog; po zgodzie status „wyłączona (OK)” | |
| 10 | Test ekranu zgaszonego w kreatorze | Krok 4 kreatora, naciśnięcia co ok. 2 s | Licznik pokazuje liczbę naciśnięć przy zgaszonym ekranie; w wyniku „Działa przy zgaszonym ekranie: tak” | ⚠️ 2026-10-10, v0.1.5: 1/3 – wszystkie jako LAUNCH_INTENT z `com.bullitt.wave`, zero KeyEvent. Prawdopodobna przyczyna: 3 szybkie naciśnięcia = 1 gest podwójny po stronie systemu. Poprawki w v0.1.7 (onNewIntent, bez singleInstance, uruchomienia liczone zawsze) i v0.1.8 (ustawienie „Gest: podwójne naciśnięcie”). v0.1.7–v0.1.8: crash `LaunchReceiverActivity` (Theme.NoDisplay bez natychmiastowego finish) – naprawione w v0.1.9. Powtórzyć z gestem przypisanym w systemie |

## Otwarte pytania (§11) — do ustalenia kreatorem

1. Czy *Programmable Key* pozwala przypisać dowolną aplikację (ścieżka B)? Wynik: ______
   (nieistotne, dopóki działa ścieżka A)
2. Czy `AccessibilityService` widzi klawisz SOS (ścieżka A)? **Tak, ale tylko przy włączonym
   ekranie** (2026-10-10, v0.1.5). Przy zgaszonym ekranie klawisz nie trafia do usługi
   dostępności; zamiast tego `com.bullitt.wave` (referrer `android-app://android`, flagi
   `NEW_TASK|EXCLUDE_FROM_RECENTS`) uruchamia `LaunchReceiverActivity`. Stąd tryb hybrydowy:
   KeyEvent przy włączonym ekranie + uruchomienie aplikacji przy zgaszonym.
   Otwarte (2026-10-10, v0.1.9): przy zgaszonym ekranie i podwójnym naciśnięciu zarejestrowano
   tylko 1 z kilku gestów. Od v0.1.10 każde uruchomienie i start procesu są zawsze w
   `diagnostic_log` (z flagami `screen_on`, `keyguardLocked`, `serviceRunning`), a uruchomienie
   zawsze startuje usługę – powtórzyć test i wyeksportować log.
   Keycode: **291** (vendor: `KEYCODE_MACRO1_ON`), scancode: **183**, urządzenie wejściowe:
   `gpio_keys` (deviceId 9, source 0x101). Krótkie naciśnięcie trwa ok. 180 ms
   (DOWN→UP), więc domyślny debounce 150 ms nie zjada naciśnięć.
