# Android Weather App

A simple Android weather application built with Kotlin and Jetpack Compose.  
Enter a city name to get the current weather conditions.

## Features

- Search weather by city name
- City geocoding via Open-Meteo Geocoding API
- Current weather details:
  - Temperature
  - Feels like
  - Humidity
  - Wind speed
  - Weather condition (WMO code mapped to text/emoji)
- Loading and error states
- Modern Compose UI

## Tech Stack

- Kotlin
- Jetpack Compose (Material 3)
- Coroutines
- `HttpURLConnection`
- Open-Meteo APIs

## Requirements

- Android Studio (latest stable recommended)
- JDK 11
- Android SDK:
  - minSdk 35
  - targetSdk 37

## Getting Started

1. Clone the repository:
   ```bash
   git clone https://github.com/arayuly/android-weather-app.git
   cd android-weather-app
   ```
2. Open the project in Android Studio.
3. Let Gradle sync complete.
4. Run the app on an emulator or Android device.

## Build and Test

```bash
./gradlew assembleDebug
./gradlew test
```

## API Notes

The app uses:

- Geocoding: `https://geocoding-api.open-meteo.com/v1/search`
- Weather: `https://api.open-meteo.com/v1/forecast`

No API key is required for these endpoints at the time of writing.

## Project Structure

- `app/src/main/java/com/example/weatherapp/MainActivity.kt` – main UI, networking, and weather logic
- `app/src/main/AndroidManifest.xml` – app manifest and permissions
- `app/src/main/java/com/example/weatherapp/ui/theme/` – UI theme files
