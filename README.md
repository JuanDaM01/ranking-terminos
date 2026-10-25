# Ranking de términos con ordenamientos no comparativos

Seguimiento — Análisis de algoritmos en el contexto de la bibliometría (Universidad del Quindío).

A partir del abstract de los 20 artículos del corpus, el programa genera el listado de términos
más frecuentes usando **Counting Sort**, **Radix Sort** y **Bucket Sort**, mide sus tiempos de
ejecución en milisegundos y muestra el top 15 obtenido con cada algoritmo.

## Requisitos

- Java 17 o superior (probado con JDK 21)
- Visual Studio Code con el *Extension Pack for Java*, o Maven en la terminal

Los 20 PDF ya están en `corpus/pdfs/` y los abstracts extraídos de ellos en `corpus/abstracts/`.

## Ejecutar en Visual Studio Code

1. Abrir la carpeta `ranking-terminos` (la que contiene `pom.xml`) con *Archivo > Abrir carpeta*.
2. Instalar el *Extension Pack for Java* si VS Code lo sugiere.
3. Esperar a que termine la importación del proyecto Maven (abajo a la derecha). La primera vez
   descarga PDFBox, así que necesita internet.
4. Abrir la clase que se quiera ejecutar en `src/main/java/co/edu/uniquindio/bibliometria/` y
   pulsar **Run** encima del método `main`. La salida aparece en la terminal de VS Code.

| Clase | Qué hace | Tiempo aprox. |
|---|---|---|
| `App.java` | Tabla 1, tiempos de una corrida, Tabla 5 y validación | 5 s |
| `MedicionCompleta.java` | Lo anterior con la metodología del documento: 10 corridas en JVM nuevas (mediana, Tabla 4) y 3 corridas del experimento de escalabilidad (Figuras 1 y 2) | 1 min |

## Ejecutar en la terminal (Maven)

```
mvn package
java -jar target/ranking-terminos.jar                                  # una corrida
java -jar target/ranking-terminos.jar corpus --corridas 10 --escalabilidad  # medición completa
```

## Qué debe coincidir con el documento

- **Exactamente igual en cualquier equipo:** la Tabla 1 (4.007 palabras, 2.407 términos, n = 950,
  k = 86, 575 términos únicos), la Tabla 5 (top 15 de los tres algoritmos), el empate de los cuatro
  términos con 15 apariciones y la validación ("mismo ranking completo: SÍ").
- **Depende del equipo:** los milisegundos de la Tabla 4 y de las Figuras 1 y 2. Cambian con el
  procesador y un poco entre corridas; lo que se mantiene es el orden de magnitud y que Counting Sort
  es el más rápido. La clase `MedicionCompleta` calcula esas cifras igual que en el
  documento y las guarda en `resultados/tabla4_tiempos.csv`, `resultados/escalabilidad_n_mediana.csv`
  y `resultados/escalabilidad_k_mediana.csv`.

## Abstracts

El programa extrae el abstract de cada PDF la primera vez y lo guarda en
`corpus/abstracts/<archivo>.txt`; en las ejecuciones siguientes lee ese `.txt`. Los 20 `.txt`
incluidos son exactamente los que genera el programa: no tienen ninguna corrección manual. Para
comprobarlo, borre los `.txt` y ejecute `App.java`; los vuelve a crear iguales a partir de los PDF
(la sección "Corpus" de la salida dirá "extraído del PDF").

La extracción (`corpus/DelimitadorAbstract.java`) resuelve sola los casos difíciles del corpus:

- **Inicio y fin del abstract:** empieza en "Abstract" (o "A B S T R A C T" en Elsevier) y termina en
  el primer marcador que le sigue (Keywords, Index Terms, Introduction, ©...). El artículo 9 no
  tiene ningún marcador: ahí el fin se detecta por el cambio de diseño de la página (el abstract va
  a todo el ancho y el cuerpo empieza a dos columnas, con líneas más cortas).
- **Palabras partidas al final de línea:** se usa el texto completo del artículo como vocabulario.
  Con guion, "tech-/nologies" se une, pero "instance-/based" (artículo 8) conserva el guion porque
  esa forma aparece en otra parte del artículo, y "AI-/driven" también, porque la izquierda es una
  sigla. Sin guion visible (PDFBox 3 descarta el guion blando), "pro/grams" se une solo si
  "programs" existe en el artículo y "pro" y "grams" no son, a la vez, palabras del artículo.

En la salida, la sección "Corpus" marca los abstracts vacíos, muy cortos o sin final.

## Salida (`resultados/`)

| Archivo | Contenido |
|---|---|
| `tiempos.csv` | Tiempo en frío y mediana/promedio/mínimo tras calentamiento, por algoritmo (ms) |
| `top15.csv` | Top 15 obtenido por cada algoritmo, lado a lado |
| `ranking_completo.csv` | Lista completa de términos ordenada por frecuencia |
| `corpus.csv` | Palabras del abstract de cada artículo |
| `escalabilidad_n.csv`, `escalabilidad_k.csv` | Solo con `--escalabilidad` (una corrida) |
| `tabla4_tiempos.csv` | Solo con `--corridas N`: mediana de las N corridas (Tabla 4) |
| `escalabilidad_n_mediana.csv`, `escalabilidad_k_mediana.csv` | Con `--corridas N --escalabilidad`: mediana de 3 corridas (Figuras 1 y 2) |
| `corridas/` | Archivos de cada corrida individual |

La carpeta `resultados_medicion/` tiene las corridas usadas en el documento de análisis:
10 ejecuciones del programa (`tiempos_1.csv` … `tiempos_10.csv`, con su salida de consola) y
3 ejecuciones del experimento de escalabilidad.

## Estructura

```
src/main/java/co/edu/uniquindio/bibliometria/
├── App.java                      Flujo principal
├── corpus/                       Lectura de PDF (PDFBox) y delimitación del abstract
├── texto/                        Preprocesamiento y conteo de frecuencias
├── ordenamiento/                 CountingSort, RadixSort (10 colas enlazadas), BucketSort
├── medicion/                     Medición de tiempos, corridas repetidas y escalabilidad
└── reporte/                      Consola y CSV
src/main/resources/stopwords/     Stopwords en inglés (263) y español (92)
src/test/java/                    Pruebas (mvn test)
```

## Decisiones de diseño

- **Preprocesamiento:** minúsculas, eliminación de tildes y ligaduras tipográficas (ﬁ → fi),
  unión de palabras partidas al final de línea, tokenización por caracteres que no son letras
  (se descartan números y signos), eliminación de stopwords y de tokens de una letra. No se
  aplica stemming, para que el ranking muestre palabras reales.
- **Clave de ordenamiento:** la frecuencia (entero). Los tres algoritmos la usan como índice,
  sin comparar términos entre sí, y ordenan de mayor a menor.
- **Estabilidad:** la lista de frecuencias queda en orden de primera aparición en los artículos
  1 a 20 y los tres algoritmos son estables, así que los empates se resuelven igual en los tres.
- **Medición:** `System.nanoTime()`. Se reporta la primera ejecución (en frío) y la mediana de
  1.000 ejecuciones tras 5.000 rondas de calentamiento (en caliente), porque la JVM compila el
  código (JIT) durante las primeras ejecuciones. Las ejecuciones se intercalan entre los tres
  algoritmos para que el recolector de basura los afecte por igual.
