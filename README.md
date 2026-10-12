# Android Weather App

Простое Android-приложение на Jetpack Compose для получения текущей погоды по названию города.

## Возможности

- Поиск погоды по городу
- Отображение:
  - температуры
  - ощущаемой температуры
  - влажности
  - скорости ветра
  - погодного состояния (WMO код + описание/эмодзи)
- Обработка ошибок:
  - город не найден
  - нет интернета
  - таймаут сервера

## Технологии

- Kotlin
- Jetpack Compose + Material 3
- Coroutines
- `HttpURLConnection` + `org.json`
- API Open-Meteo:
  - Geocoding API
  - Forecast API

## Требования

- Android Studio (рекомендуется актуальная версия)
- JDK 11
- Android SDK:
  - `minSdk = 35`
  - `targetSdk = 37`
  - `compileSdk = 37`

## Запуск

1. Клонируйте репозиторий:
   ```bash
   git clone https://github.com/arayuly/android-weather-app.git
   cd android-weather-app
   ```
2. Откройте проект в Android Studio.
3. Дождитесь синхронизации Gradle.
4. Запустите приложение на эмуляторе или устройстве с Android 15+.

## Сборка из терминала

```bash
./gradlew assembleDebug
```

## Примечания

- Для работы приложения нужен доступ в интернет.
- Ключ API не требуется (используется Open-Meteo).
