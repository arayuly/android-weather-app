package com.example.weatherapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import kotlin.math.roundToInt

/* ============================================================
 *  1. МОДЕЛИ ДАННЫХ
 * ============================================================ */

/** Результат запроса: всё, что мы показываем на экране. */
data class WeatherInfo(
    val cityName: String,     // Название города (как его вернул геокодер)
    val country: String,      // Страна
    val temperature: Double,  // Температура, °C
    val feelsLike: Double,    // Ощущаемая температура, °C
    val humidity: Int,        // Влажность, %
    val windSpeed: Double,    // Скорость ветра, м/с
    val weatherCode: Int      // Код погоды WMO (0 — ясно, 61 — дождь и т.д.)
)

/** Своё исключение: город с таким названием не найден. */
class CityNotFoundException(val city: String) : Exception("City not found: $city")

/* ============================================================
 *  2. СЕТЕВОЙ СЛОЙ (без Retrofit — только HttpURLConnection)
 * ============================================================ */

/**
 * Выполняет простой GET-запрос и возвращает тело ответа строкой.
 * ВАЖНО: функция блокирующая, поэтому вызывать её нужно только
 * из фонового потока (мы вызываем её внутри Dispatchers.IO).
 */
private fun httpGet(urlString: String): String {
    val connection = URL(urlString).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000 // 10 секунд на подключение
        connection.readTimeout = 10_000    // 10 секунд на чтение ответа
        connection.setRequestProperty("Accept", "application/json")

        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            throw java.io.IOException("Сервер вернул код $code")
        }
        // Читаем весь ответ в строку и автоматически закрываем поток
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect() // Всегда освобождаем соединение
    }
}

/**
 * Получает погоду по названию города.
 *
 * suspend + withContext(Dispatchers.IO) — сетевая работа выполняется
 * в пуле потоков ввода-вывода, главный (UI) поток не блокируется.
 */
suspend fun fetchWeather(city: String): WeatherInfo = withContext(Dispatchers.IO) {

    // --- Шаг 1. Геокодинг: название города -> координаты ---
    // URLEncoder нужен, чтобы кириллица и пробелы корректно попали в URL
    val encodedCity = URLEncoder.encode(city.trim(), "UTF-8")
    val geoUrl = "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=$encodedCity&count=1&language=ru&format=json"

    val geoJson = JSONObject(httpGet(geoUrl))
    val results = geoJson.optJSONArray("results")

    // Если города нет, поле "results" в ответе отсутствует
    if (results == null || results.length() == 0) {
        throw CityNotFoundException(city.trim())
    }

    val place = results.getJSONObject(0)
    val latitude = place.getDouble("latitude")
    val longitude = place.getDouble("longitude")
    val foundName = place.optString("name", city.trim())
    val country = place.optString("country", "")

    // --- Шаг 2. Запрос текущей погоды по координатам ---
    val weatherUrl = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m," +
            "wind_speed_10m,weather_code" +
            "&wind_speed_unit=ms" +   // ветер в метрах в секунду
            "&timezone=auto"

    val current = JSONObject(httpGet(weatherUrl)).getJSONObject("current")

    // --- Шаг 3. Разбираем JSON и собираем модель ---
    WeatherInfo(
        cityName = foundName,
        country = country,
        temperature = current.getDouble("temperature_2m"),
        feelsLike = current.getDouble("apparent_temperature"),
        humidity = current.getInt("relative_humidity_2m"),
        windSpeed = current.getDouble("wind_speed_10m"),
        weatherCode = current.getInt("weather_code")
    )
}

/**
 * Переводит код погоды WMO в пару «эмодзи + описание на русском».
 * Таблица кодов: https://open-meteo.com/en/docs (раздел Weather variable documentation)
 */
private fun describeWeather(code: Int): Pair<String, String> = when (code) {
    0 -> "☀️" to "Ясно"
    1 -> "🌤️" to "Преимущественно ясно"
    2 -> "⛅" to "Переменная облачность"
    3 -> "☁️" to "Пасмурно"
    45, 48 -> "🌫️" to "Туман"
    51, 53, 55, 56, 57 -> "🌦️" to "Морось"
    61, 63, 65, 66, 67 -> "🌧️" to "Дождь"
    71, 73, 75, 77 -> "❄️" to "Снег"
    80, 81, 82 -> "🌧️" to "Ливень"
    85, 86 -> "🌨️" to "Снегопад"
    95, 96, 99 -> "⛈️" to "Гроза"
    else -> "🌡️" to "Нет данных"
}

/* ============================================================
 *  3. ACTIVITY И ТЕМА
 * ============================================================ */

/** Собственная светлая цветовая схема (чтобы файл не зависел от темы шаблона). */
private val WeatherColors = lightColorScheme(
    primary = Color(0xFF1E5EFF),
    onPrimary = Color.White,
    surface = Color.White,
    onSurface = Color(0xFF1B1F3B),
    outline = Color(0xFF9AA5C4)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Рисуем под системными панелями (на API 35 это и так по умолчанию)

        setContent {
            MaterialTheme(colorScheme = WeatherColors) {
                // Внешний Box рисует градиентный фон на весь экран, включая область под status bar
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF4FACFE), Color(0xFF1E5EFF))
                            )
                        )
                ) {
                    // Scaffold сам вычисляет отступы под системные панели (innerPadding)
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent // чтобы был виден градиент
                    ) { innerPadding ->
                        WeatherScreen(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}

/* ============================================================
 *  4. ГЛАВНЫЙ ЭКРАН
 * ============================================================ */

@Composable
fun WeatherScreen(modifier: Modifier = Modifier) {

    // --- Состояние экрана ---
    // rememberSaveable сохраняет введённый город при повороте экрана
    var city by rememberSaveable { mutableStateOf("") }
    var weather by remember { mutableStateOf<WeatherInfo?>(null) } // результат
    var isLoading by remember { mutableStateOf(false) }             // идёт ли загрузка
    var errorMessage by remember { mutableStateOf<String?>(null) }  // текст ошибки

    // Скоуп корутин, привязанный к жизненному циклу этого Composable
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    /** Запускает загрузку погоды. Вызывается по кнопке и по клавише «Поиск» на клавиатуре. */
    fun loadWeather() {
        if (city.isBlank() || isLoading) return
        focusManager.clearFocus() // прячем клавиатуру

        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                // Приостанавливаем корутину, пока IO-поток ждёт ответ сервера
                weather = fetchWeather(city)
            } catch (e: CancellationException) {
                throw e // отмену корутины нельзя «проглатывать»
            } catch (e: CityNotFoundException) {
                weather = null
                errorMessage = "Город «${e.city}» не найден. Проверьте название."
            } catch (e: UnknownHostException) {
                weather = null
                errorMessage = "Нет подключения к интернету."
            } catch (e: SocketTimeoutException) {
                weather = null
                errorMessage = "Сервер не отвечает. Попробуйте позже."
            } catch (e: Exception) {
                weather = null
                errorMessage = "Ошибка: ${e.localizedMessage ?: "неизвестная"}"
            } finally {
                isLoading = false // выключаем индикатор в любом случае
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()                          // сдвигаем контент, когда открыта клавиатура
            .verticalScroll(rememberScrollState()) // экран можно прокручивать на малых дисплеях
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Заголовок ---
        Text(
            text = "Прогноз погоды",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Введите город и узнайте погоду прямо сейчас",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        // --- Карточка с вводом города и кнопкой ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("Город") },
                    placeholder = { Text("Например: Москва") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    // Кнопка «Поиск» на экранной клавиатуре
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { loadWeather() })
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { loadWeather() },
                    enabled = city.isNotBlank() && !isLoading, // блокируем пустой ввод и повторные нажатия
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (isLoading) {
                        // Пока грузим — показываем крутилку вместо текста
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text("Узнать погоду", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- Блок ошибки (виден только если errorMessage != null) ---
        errorMessage?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
            ) {
                Text(
                    text = "⚠️ $message",
                    color = Color(0xFFB71C1C),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // --- Карточка с результатом (видна только если данные получены) ---
        weather?.let { info ->
            WeatherCard(info)
        }
    }
}

/* ============================================================
 *  5. КАРТОЧКА С РЕЗУЛЬТАТОМ
 * ============================================================ */

@Composable
fun WeatherCard(info: WeatherInfo) {
    val (emoji, description) = describeWeather(info.weatherCode)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Город и страна
            Text(
                text = if (info.country.isNotBlank()) "${info.cityName}, ${info.country}" else info.cityName,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            // Большая иконка погоды
            Text(text = emoji, fontSize = 72.sp, modifier = Modifier.padding(vertical = 8.dp))

            // Температура
            Text(
                text = "${info.temperature.roundToInt()}°C",
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Описание («Ясно», «Дождь» и т.д.)
            Text(
                text = description,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Нижняя строка с тремя дополнительными показателями
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF1F5FF), RoundedCornerShape(20.dp))
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InfoItem(label = "Ощущается", value = "${info.feelsLike.roundToInt()}°C")
                InfoItem(label = "Влажность", value = "${info.humidity}%")
                InfoItem(label = "Ветер", value = "${info.windSpeed.roundToInt()} м/с")
            }
        }
    }
}

/** Маленький блок «значение + подпись» для нижней строки карточки. */
@Composable
fun InfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.outline
        )
    }
}