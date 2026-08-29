# Proyecto: API de cartelera para widget Android

## 1. Objetivo

Crear un backend pequeño en **Python + FastAPI** que consulte información de cines y la exponga en un JSON sencillo para un **widget de Android**.

Cines objetivo iniciales:

1. **Cinemark — Gran Plaza del Sol, Soledad**
2. **Cine Colombia — Parque Alegra**

Información deseada:

- Películas actualmente en cartelera.
- Horarios de cada función.
- Formato/pantalla cuando esté disponible (2D, 3D, XD, etc.).
- Idioma cuando esté disponible.
- Cantidad de asientos disponibles.
- `SessionId`/identificador de función cuando sea útil para enlazar a la compra.
- Fecha consultada.

La prioridad es obtener los datos de forma robusta y sencilla. No se debe hacer scraping HTML si existe una API utilizada por la propia página que ya entrega los datos estructurados.

---

# 2. Arquitectura general

```text
                         ┌─────────────────────┐
                         │   Android Widget    │
                         │                     │
                         │ Películas           │
                         │ Horarios            │
                         │ Asientos disponibles│
                         └──────────┬──────────┘
                                    │ HTTPS
                                    ▼
                         ┌─────────────────────┐
                         │       FastAPI       │
                         │                     │
                         │ GET /api/cinemark   │
                         │ GET /api/cineco     │
                         └──────────┬──────────┘
                                    │
                         ┌──────────┴──────────┐
                         ▼                     ▼
                 ┌────────────────┐    ┌─────────────────┐
                 │ Cinemark API   │    │ Cine Colombia   │
                 │ directa        │    │ API / Selenium  │
                 └───────┬────────┘    └────────┬────────┘
                         │                      │
                         ▼                      ▼
                      JSON                  JSON / DOM
                         │                      │
                         └──────────┬───────────┘
                                    ▼
                              Normalización
                                    │
                                    ▼
                             Cache temporal
                                    │
                                    ▼
                               FastAPI
```

## Principio importante

El widget NO debe hacer scraping.

El backend se encarga de consultar las fuentes, normalizar la información y mantener una caché.

Esto evita:

- scraping desde Android;
- consumo innecesario de batería;
- depender de ejecución en segundo plano del widget;
- repetir scraping cada vez que el widget se actualiza;
- exponer detalles internos de las APIs de los cines al cliente Android.

---

# 3. Stack propuesto

## Backend

- Python 3.x
- FastAPI
- Uvicorn
- `requests` o `httpx`
- BeautifulSoup solo si finalmente hace falta HTML
- Selenium solamente como último recurso para Cine Colombia
- Caché simple inicialmente (memoria/JSON/SQLite según necesidad)

## Android

Fuera del backend, pero previsto:

- Kotlin
- Jetpack Glance
- WorkManager
- HTTP hacia FastAPI

## Deploy

Primera opción:

- Render Free

El backend debe ser suficientemente pequeño para poder ejecutarse como un servicio web.

Si Selenium termina siendo necesario para Cine Colombia, evaluar recursos del servicio porque Chrome headless consume bastante más RAM/CPU que una API HTTP normal.

---

# 4. Cinemark

## Página objetivo

```text
https://www.cinemark.com.co/ciudad/soledad/gran-plaza-del-sol
```

Inicialmente se pensó en hacer scraping HTML, pero se descubrió que la página utiliza una API estructurada.

## API encontrada

```text
https://api.cinemark-core.com/vista/country/co/theater/gran-plaza-del-sol
```

Parámetros observados:

```text
date=2026-08-16
companyId=5db771be04daec00076df3f5
midnightSessionStart=23:10
midnightSessionEnd=03:00
```

Petición equivalente:

```text
GET https://api.cinemark-core.com/vista/country/co/theater/gran-plaza-del-sol
    ?date=YYYY-MM-DD
    &companyId=5db771be04daec00076df3f5
    &midnightSessionStart=23:10
    &midnightSessionEnd=03:00
```

## Autenticación de Cinemark

Al abrir el endpoint directamente en el navegador apareció:

```text
No connectapitoken
```

La petición que hace la página contiene:

```http
connectapitoken: web-co-token
```

También se observaron:

```http
Accept: application/json, text/plain, */*
Origin: https://www.cinemark.com.co
Referer: https://www.cinemark.com.co/
```

No parece necesario copiar cookies del navegador.

El token observado es:

```text
web-co-token
```

Debe tratarse como un identificador/token utilizado por el frontend público, no como una credencial de usuario.

## Ejemplo de petición Python

```python
import requests

url = "https://api.cinemark-core.com/vista/country/co/theater/gran-plaza-del-sol"

params = {
    "date": "2026-08-16",
    "companyId": "5db771be04daec00076df3f5",
    "midnightSessionStart": "23:10",
    "midnightSessionEnd": "03:00",
}

headers = {
    "connectapitoken": "web-co-token",
    "Accept": "application/json, text/plain, */*",
    "Origin": "https://www.cinemark.com.co",
    "Referer": "https://www.cinemark.com.co/",
}

response = requests.get(
    url,
    params=params,
    headers=headers,
)

response.raise_for_status()
data = response.json()
```

Antes de implementar definitivamente, verificar que esta petición siga funcionando sin cookies/sesión.

---

# 5. Estructura del JSON de Cinemark

La respuesta contiene:

```text
Movies[]
    Name
    SlugName
    Duration
    Rating
    CorporateFilmId
    Format[]
        LangTypes[]
        ScreenTypes[]
        SeatTypes[]
        Sessions[]
            SessionId
            IsVisible
            Showtime
            SeatsAvailable
            IsMidnightSession
            IsAllocatedSeating
    CoverImageUrl
    GenreName
```

## Campos importantes

Película:

```json
{
  "Name": "Spiderman Nuevo Dia",
  "SlugName": "spiderman-nuevo-dia",
  "Duration": "145",
  "Rating": "12-A",
  "CorporateFilmId": "109320"
}
```

Formato:

```json
{
  "LangTypes": ["DOB"],
  "ScreenTypes": ["2D", "XD"],
  "SeatTypes": ["GENERAL"]
}
```

Función:

```json
{
  "SessionId": "108808",
  "IsVisible": true,
  "Showtime": "15:15:00",
  "SeatsAvailable": 319,
  "IsMidnightSession": false,
  "IsAllocatedSeating": true
}
```

El JSON real observado confirma que `SeatsAvailable` ya proporciona directamente el número de asientos disponibles.

Por ejemplo:

- Spider-Man 15:15 → 319
- Spider-Man 18:30 → 292
- Spider-Man 22:00 → 338

No hace falta consultar inicialmente el mapa individual de asientos para saber cuántos quedan.

---

# 6. Importante: Format y Sessions

No asumir:

```python
movie["Sessions"]
```

porque las sesiones están dentro de `Format`.

La estructura correcta es:

```python
for movie in data["Movies"]:
    for fmt in movie["Format"]:
        for session in fmt["Sessions"]:
            ...
```

Cada `Format` puede representar combinaciones diferentes de:

- idioma;
- tipo de pantalla;
- tipo de asiento.

Ejemplo observado para Spider-Man:

```text
DOB
 ├── 3D
 └── XD
```

y otra variante:

```text
DOB
 ├── 2D
 └── XD
```

Por tanto, la normalización debe conservar esta información.

---

# 7. Filtrado de Cinemark

Usar:

```python
if not session["IsVisible"]:
    continue
```

Las funciones con `IsVisible = false` aparecen en la respuesta pero no deberían mostrarse al usuario.

Ejemplo observado:

```text
La Odisea
14:00 → IsVisible false
17:45 → IsVisible true
21:45 → IsVisible true
```

También se puede conservar `IsAllocatedSeating` para saber si la función utiliza asientos asignados.

---

# 8. Modelo de salida normalizado

Se recomienda que FastAPI NO devuelva el JSON original de Cinemark.

Crear un modelo común para ambos cines.

Ejemplo:

```json
{
  "cinema": "Cinemark",
  "location": "Gran Plaza del Sol",
  "date": "2026-08-16",
  "movies": [
    {
      "title": "Spiderman Nuevo Dia",
      "rating": "12-A",
      "duration_minutes": 145,
      "genre": "Acción",
      "cover_image": "...",
      "showtimes": [
        {
          "session_id": "108808",
          "time": "15:15",
          "language": "DOB",
          "screen_types": ["2D", "XD"],
          "seats_available": 319,
          "allocated_seating": true
        }
      ]
    }
  ]
}
```

Esto permite que Android no tenga que conocer la estructura interna de Cinemark.

---

# 9. Fecha

No dejar la fecha fija.

El backend debería usar la fecha actual por defecto:

```python
from datetime import date

today = date.today().isoformat()
```

Idealmente:

```text
GET /api/cinemark
```

consulta hoy.

Y:

```text
GET /api/cinemark?date=2026-08-17
```

permite consultar una fecha específica.

Verificar la zona horaria del servidor. Para este proyecto la referencia deseada es Colombia (`America/Bogota`).

---

# 10. Cine Colombia

## Página objetivo

```text
https://www.cinecolombia.com/cinemas/parque-alegra/
```

La página es dinámica y se observó que devuelve/carga contenido mediante JavaScript.

Se encontró una API interna/externa:

```text
https://digital-api.cinecolombia.com/ocapi/v1/showtimes/by-business-date/first?siteIds=7337
```

`siteIds=7337` corresponde al sitio investigado para Parque Alegra.

## Petición observada

La petición utiliza:

```http
Authorization: Bearer <JWT>
```

También contiene:

```http
correlationid: ...
Origin: https://www.cinecolombia.com
Referer: https://www.cinecolombia.com/
Accept: application/json
```

Las cookies observadas incluyen mecanismos de Cloudflare.

NO almacenar en el proyecto:

- el JWT capturado;
- cookies de sesión;
- `cf_clearance`;
- `__cf_bm`;
- otros tokens temporales.

El JWT observado tenía un `exp`, por lo que es temporal.

---

# 11. Información descubierta sobre el token de Cine Colombia

El JWT observado contiene, entre otros datos:

```text
iss = https://auth.movieexchange.com/
token_usage = access_token
```

Esto sugiere que Cine Colombia utiliza un sistema de autenticación de MovieXchange.

Todavía NO está confirmado cómo obtiene el frontend el token.

No asumir que el token es estático.

---

# 12. API interna de Cine Colombia

Al inspeccionar el JavaScript del frontend se encontró:

```javascript
class BaseApi {
    constructor() {
        this.OmniaApi = '/api/omnia/v1';
        this.InitialData = this.OmniaApi + '/initialData';
        this.Page = this.OmniaApi + '/page';
        this.PageList = this.OmniaApi + '/pageList';
        this.Extensions = this.OmniaApi + '/extensions';
        this.FilmExtension = this.OmniaApi + '/extensions/film';
        this.ConcessionEnabled = this.OmniaApi + '/extensions/ticketing/concession-enabled';
        this.Configuration = this.OmniaApi + '/configuration';
        this.TrailerWatched = (filmHoCode) => this.OmniaApi + `/trailerwatched/${filmHoCode}`;
        this.OmniaConnectApi = '/api/omnia/connect/v1';
        this.Genres = this.OmniaConnectApi + '/filmGenres';
        this.Cinemas = this.OmniaConnectApi + '/cinemas';
        this.Attributes = (name) => this.OmniaConnectApi + '/' + name;
        this.Search = (query) => this.OmniaApi + '/search?searchTerm=' + query;
    }
}
```

También:

```javascript
this.GET = async (url, dontDeserialise = false, signal) => {
    const response = await fetch(url, { signal });
    ...
}
```

Este detalle es muy importante.

El frontend tiene endpoints propios bajo:

```text
https://www.cinecolombia.com/api/omnia/v1/...
```

y esos `fetch()` no añaden explícitamente un Bearer token en este `BaseApi`.

Por tanto, antes de implementar acceso directo a `digital-api`, investigar si el backend `/api/omnia` actúa como proxy y puede entregar los datos sin que el scraper tenga que gestionar el JWT.

---

# 13. Endpoints internos de Cine Colombia a investigar

Prioridad:

```text
/api/omnia/v1/initialData
/api/omnia/v1/page
/api/omnia/v1/pageList
/api/omnia/v1/configuration
/api/omnia/v1/extensions
/api/omnia/v1/extensions/film
/api/omnia/connect/v1/filmGenres
/api/omnia/connect/v1/cinemas
```

Especialmente:

```text
/api/omnia/v1/initialData
```

y:

```text
/api/omnia/v1/page?friendly=/cinemas/parque-alegra/
```

Probar primero desde el navegador y después desde Python.

Ejemplo:

```python
import requests

url = "https://www.cinecolombia.com/api/omnia/v1/initialData"

response = requests.get(url)
print(response.status_code)
print(response.text)
```

Si alguno devuelve los datos necesarios, preferir esta solución a Selenium.

---

# 14. Selenium como plan B

Si no se puede consumir la API interna de Cine Colombia sin reproducir autenticación/Cloudflare, utilizar Selenium.

Arquitectura:

```text
FastAPI
   │
   ▼
Selenium + Chrome headless
   │
   ▼
Cine Colombia
   │
   ├── JavaScript
   ├── autenticación
   └── contenido dinámico
   │
   ▼
DOM renderizado
   │
   ▼
Extracción
   │
   ▼
JSON normalizado
```

Ejemplo mínimo:

```python
from selenium import webdriver
from selenium.webdriver.chrome.options import Options

options = Options()
options.add_argument("--headless")
options.add_argument("--no-sandbox")
options.add_argument("--disable-dev-shm-usage")

driver = webdriver.Chrome(options=options)

driver.get(
    "https://www.cinecolombia.com/cinemas/parque-alegra/"
)

print(driver.title)
print(driver.page_source)

driver.quit()
```

Para contenido dinámico utilizar esperas explícitas, no depender de `sleep()`.

Ejemplo:

```python
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

wait = WebDriverWait(driver, 15)

element = wait.until(
    EC.presence_of_element_located(
        (By.CSS_SELECTOR, "selector")
    )
)
```

---

# 15. Selenium y Render

Selenium + Chrome headless consume significativamente más recursos que un cliente HTTP.

Por eso:

NO hacer:

```text
Android widget
     ↓
FastAPI
     ↓
iniciar Chrome
     ↓
scrapear
     ↓
respuesta
```

en cada request.

Hacer:

```text
             cada 10-15 minutos
                    │
                    ▼
             Selenium/HTTP
                    │
                    ▼
                cache
                    │
                    ▼
                FastAPI
                    │
                    ▼
             Android widget
```

El widget solamente consulta la caché.

---

# 16. Caché

Se recomienda implementar una caché desde el principio.

Ejemplo conceptual:

```text
cache/
├── cinemark_2026-08-16.json
└── cinecolombia_2026-08-16.json
```

Pero para el primer MVP puede ser incluso un diccionario en memoria.

Mejor solución inicial:

```python
cache = {
    "cinemark": {
        "timestamp": ...,
        "data": ...
    },
    "cinecolombia": {
        "timestamp": ...,
        "data": ...
    }
}
```

TTL sugerido:

```text
5-15 minutos
```

Para cartelera/asientos, 10 minutos es un buen punto de partida.

---

# 17. API del backend

Propuesta:

```text
GET /api/cinemark
GET /api/cinemark?date=YYYY-MM-DD

GET /api/cinecolombia
GET /api/cinecolombia?date=YYYY-MM-DD

GET /api/cinemas
GET /health
```

`/api/cinemas` podría devolver:

```json
{
  "cinemas": [
    {
      "id": "cinemark-gran-plaza-del-sol",
      "name": "Cinemark",
      "location": "Gran Plaza del Sol"
    },
    {
      "id": "cinecolombia-parque-alegra",
      "name": "Cine Colombia",
      "location": "Parque Alegra"
    }
  ]
}
```

---

# 18. Posible endpoint unificado

También puede existir:

```text
GET /api/movies
```

que devuelva:

```json
{
  "date": "2026-08-16",
  "cinemas": [
    {
      "name": "Cinemark",
      "location": "Gran Plaza del Sol",
      "movies": [...]
    },
    {
      "name": "Cine Colombia",
      "location": "Parque Alegra",
      "movies": [...]
    }
  ]
}
```

Esto sería ideal para Android.

---

# 19. Estado actual del proyecto

## Cinemark

Estado: **prácticamente resuelto**.

Confirmado:

- API encontrada.
- Endpoint de Gran Plaza del Sol.
- `companyId`.
- `connectapitoken: web-co-token`.
- Películas.
- Horarios.
- Formatos.
- Idiomas.
- `SessionId`.
- `SeatsAvailable`.
- `IsVisible`.

No hace falta Selenium para Cinemark.

## Cine Colombia

Estado: **API encontrada, autenticación pendiente de resolver**.

Confirmado:

- `digital-api.cinecolombia.com`.
- Endpoint de showtimes.
- `siteIds=7337`.
- Bearer JWT temporal.
- JWT emitido por/relacionado con `auth.movieexchange.com`.
- API interna `/api/omnia/v1`.
- Código frontend `BaseApi`.
- Posibilidad de usar Selenium como fallback.

Pendiente:

1. Determinar si `/api/omnia/v1/...` entrega los datos sin autenticación compleja.
2. Determinar cómo obtiene el frontend el JWT.
3. Encontrar endpoint de asientos de Cine Colombia.
4. Si API directa no es viable, implementar Selenium.

---

# 20. Asientos

## Cinemark

Ya tenemos:

```json
"SeatsAvailable": 319
```

Por lo tanto:

```text
número de asientos disponibles
```

está resuelto.

No implementar mapa individual de asientos todavía.

## Cine Colombia

Todavía no está confirmado.

Investigar:

```text
showtimes
sessions
seats
seatmap
order
booking
```

En la investigación se observó que existen rutas de Cine Colombia relacionadas con selección de asientos bajo dominios de `multiplex.cinecolombia.com`, por lo que podría existir una API/flujo de reserva asociado.

No asumir una estructura hasta inspeccionar una función real.

---

# 21. Android Widget

La aplicación Android no debe conocer las APIs de los cines.

Debe consumir solamente:

```text
https://<backend>/api/movies
```

Ejemplo:

```json
{
  "date": "2026-08-16",
  "cinemas": [
    {
      "name": "Cinemark",
      "location": "Gran Plaza del Sol",
      "movies": [
        {
          "title": "Spiderman Nuevo Dia",
          "showtimes": [
            {
              "time": "15:15",
              "screen_types": ["2D", "XD"],
              "seats_available": 319
            }
          ]
        }
      ]
    }
  ]
}
```

Android puede actualizar el widget con WorkManager.

El widget debería tener como mínimo:

```text
🎬 Cinemark
Gran Plaza del Sol

Spiderman Nuevo Dia
15:15  2D  🟢 319
18:30  3D  🟢 292
22:00  2D  🟢 338
```

---

# 22. Manejo de errores

El backend no debe devolver error total si un cine falla.

Ejemplo:

```json
{
  "date": "2026-08-16",
  "cinemas": [
    {
      "name": "Cinemark",
      "status": "ok",
      "movies": [...]
    },
    {
      "name": "Cine Colombia",
      "status": "error",
      "error": "source_unavailable",
      "movies": []
    }
  ]
}
```

Esto permite que el widget siga mostrando Cinemark aunque Cine Colombia tenga problemas.

---

# 23. Seguridad y buenas prácticas

No guardar en Git:

- Bearer JWT de Cine Colombia.
- cookies del navegador.
- Cloudflare clearance.
- credenciales personales.

Utilizar variables de entorno si alguna credencial futura resulta necesaria:

```text
CINECO_API_TOKEN=
```

No almacenar cookies personales del navegador en el backend.

No asumir que los tokens observados son permanentes.

---

# 24. Estructura de proyecto propuesta

```text
cinema-widget-backend/
│
├── app/
│   ├── main.py
│   │
│   ├── api/
│   │   └── routes.py
│   │
│   ├── scrapers/
│   │   ├── cinemark.py
│   │   └── cinecolombia.py
│   │
│   ├── services/
│   │   ├── cache.py
│   │   └── normalizer.py
│   │
│   ├── models/
│   │   └── cinema.py
│   │
│   └── config.py
│
├── requirements.txt
├── Dockerfile
├── .env.example
├── .gitignore
└── README.md
```

Si Cine Colombia termina usando Selenium:

```text
app/
└── scrapers/
    ├── cinemark.py
    └── cinecolombia.py
```

`cinecolombia.py` puede tener internamente:

```python
fetch_api()
```

y como fallback:

```python
fetch_selenium()
```

---

# 25. Orden recomendado de implementación

## Fase 1 — Cinemark

1. Crear FastAPI.
2. Implementar cliente Cinemark.
3. Usar fecha dinámica.
4. Filtrar `IsVisible`.
5. Normalizar `Format` + `Sessions`.
6. Exponer `/api/cinemark`.
7. Probar localmente.

## Fase 2 — Caché

1. Implementar TTL.
2. Evitar llamadas repetidas.
3. Añadir `/health`.

## Fase 3 — Cine Colombia

1. Investigar `/api/omnia/v1/initialData`.
2. Investigar `/api/omnia/v1/page`.
3. Investigar `/api/omnia/v1/configuration`.
4. Intentar obtener showtimes sin JWT.
5. Si no es posible, investigar flujo de obtención del JWT.
6. Si la autenticación/Cloudflare complica demasiado la solución, implementar Selenium.

## Fase 4 — Normalización

Crear un modelo común para ambos cines.

## Fase 5 — Endpoint unificado

```text
GET /api/movies
```

## Fase 6 — Android

1. Crear aplicación Kotlin.
2. Crear widget con Jetpack Glance.
3. Consumir `/api/movies`.
4. Configurar WorkManager.
5. Mostrar horarios/asientos.
6. Permitir tocar una función para abrir la compra.

## Fase 7 — Deploy

1. Deploy FastAPI en Render.
2. Configurar variables de entorno.
3. Verificar que el servicio funcione.
4. Conectar Android.

---

# 26. MVP deseado

El primer MVP no necesita:

- base de datos;
- login;
- cuentas de usuario;
- mapas de asientos;
- notificaciones;
- scraping HTML;
- Selenium para Cinemark.

Debe limitarse a:

```text
FastAPI
   │
   ├── Cinemark API
   │
   └── Cine Colombia API/Selenium
          │
          ▼
       normalización
          │
          ▼
        caché
          │
          ▼
      /api/movies
```

Con resultado:

```text
Cine
 └── Película
      └── Funciones
           ├── hora
           ├── formato
           ├── idioma
           └── asientos disponibles
```

---

# 27. Objetivo final

El usuario debe poder mirar el widget y saber rápidamente:

```text
🎬 HOY — 16 AGO

CINEMARK · GRAN PLAZA
──────────────────────
Spiderman Nuevo Dia
15:15 · 2D · 319 libres
18:30 · 3D · 292 libres
22:00 · 2D · 338 libres

La Odisea
17:45 · 2D · 147 libres
21:45 · 2D · 160 libres


CINE COLOMBIA · PARQUE ALEGRA
──────────────────────────────
[Pendiente de integración]
```

El widget debe ser solamente una presentación de los datos; toda la lógica de scraping, autenticación, normalización y caché debe permanecer en el backend.
