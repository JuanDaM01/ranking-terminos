package co.edu.uniquindio.bibliometria;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import co.edu.uniquindio.bibliometria.corpus.CargadorCorpus;
import co.edu.uniquindio.bibliometria.corpus.CargadorCorpus.Documento;
import co.edu.uniquindio.bibliometria.corpus.DelimitadorAbstract;
import co.edu.uniquindio.bibliometria.medicion.Corridas;
import co.edu.uniquindio.bibliometria.medicion.ExperimentoEscalabilidad;
import co.edu.uniquindio.bibliometria.medicion.MedidorTiempos;
import co.edu.uniquindio.bibliometria.medicion.MedidorTiempos.Medicion;
import co.edu.uniquindio.bibliometria.modelo.Termino;
import co.edu.uniquindio.bibliometria.ordenamiento.AlgoritmoOrdenamiento;
import co.edu.uniquindio.bibliometria.ordenamiento.BucketSort;
import co.edu.uniquindio.bibliometria.ordenamiento.CountingSort;
import co.edu.uniquindio.bibliometria.ordenamiento.RadixSort;
import co.edu.uniquindio.bibliometria.reporte.Reporte;
import co.edu.uniquindio.bibliometria.texto.ContadorFrecuencias;
import co.edu.uniquindio.bibliometria.texto.Preprocesador;

/**
 * Ranking de términos del corpus mediante ordenamientos no comparativos.
 *
 * Uso: java -jar ranking-terminos.jar [carpetaCorpus] [--corridas N] [--escalabilidad]
 *  - sin opciones: una ejecución (tiempos de esa ejecución, top 15 y validación).
 *  - --corridas N: repite la medición en N JVM nuevas y muestra la mediana (Tabla 4 del documento).
 *  - --escalabilidad: experimento complementario; con --corridas se repite 3 veces y se toma la mediana.
 * La carpeta del corpus (por defecto "corpus") debe contener la subcarpeta "pdfs".
 */
public final class App {

    public static final int TOP = 15;
    public static final int CALENTAMIENTO = 5_000;
    public static final int REPETICIONES = 1_000;
    public static final int CORRIDAS_ESCALABILIDAD = 3;

    public static final List<AlgoritmoOrdenamiento> ALGORITMOS =
            List.of(new CountingSort(), new RadixSort(), new BucketSort());

    /** Corpus ya preprocesado: abstracts, tokens por documento y lista de frecuencias. */
    public record Corpus(List<Documento> documentos, List<List<String>> tokens, Termino[] frecuencias) {
        public int totalTokens() {
            return tokens.stream().mapToInt(List::size).sum();
        }

        public int k() {
            return AlgoritmoOrdenamiento.frecuenciaMaxima(frecuencias)
                    - AlgoritmoOrdenamiento.frecuenciaMinima(frecuencias) + 1;
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        List<String> argumentos = Arrays.asList(args);
        Path carpetaCorpus = Path.of(args.length > 0 && !args[0].startsWith("--") ? args[0] : "corpus");
        Path carpetaResultados = Path.of("resultados");

        // Modos internos: los usa --corridas para medir en una JVM nueva.
        int interno = argumentos.indexOf("--interno-tiempos");
        if (interno >= 0) {
            Corpus corpus = prepararCorpus(carpetaCorpus);
            Reporte.guardarTiempos(Path.of(args[interno + 1]),
                    MedidorTiempos.medir(ALGORITMOS, corpus.frecuencias(), CALENTAMIENTO, REPETICIONES));
            return;
        }
        interno = argumentos.indexOf("--interno-escalabilidad");
        if (interno >= 0) {
            Corpus corpus = prepararCorpus(carpetaCorpus);
            // Mismas condiciones que en una ejecución normal: primero la medición sobre el corpus (calienta la JVM).
            MedidorTiempos.medir(ALGORITMOS, corpus.frecuencias(), CALENTAMIENTO, REPETICIONES);
            Path destino = Path.of(args[interno + 1]);
            Reporte.guardarTiempos(destino.resolve("escalabilidad_n.csv"),
                    ExperimentoEscalabilidad.variarN(corpus.frecuencias(), ALGORITMOS));
            Reporte.guardarTiempos(destino.resolve("escalabilidad_k.csv"), ExperimentoEscalabilidad.variarK(ALGORITMOS));
            return;
        }

        if (!Files.isDirectory(carpetaCorpus.resolve("pdfs"))) {
            System.out.println("No existe la carpeta " + carpetaCorpus.resolve("pdfs").toAbsolutePath()
                    + "\nCrea la carpeta y copia allí los 20 artículos en PDF.");
            return;
        }

        // 1. Corpus, preprocesamiento y lista de frecuencias (Tabla 1).
        Corpus corpus = prepararCorpus(carpetaCorpus);
        if (corpus.frecuencias().length == 0) {
            System.out.println("Los abstracts están vacíos; revisa la carpeta corpus/abstracts.");
            return;
        }
        Reporte.imprimirCorpus(corpus.documentos(), DelimitadorAbstract.MAX_PALABRAS);
        Reporte.imprimirTabla1(corpus.documentos(), corpus.totalTokens(), corpus.frecuencias(), corpus.k());

        // 2. Medición de tiempos (Tabla 4). Va antes de cualquier otro ordenamiento para que el tiempo
        //    "en frío" sea de verdad la primera ejecución de cada algoritmo.
        int corridas = argumentos.contains("--corridas")
                ? Integer.parseInt(args[argumentos.indexOf("--corridas") + 1]) : 0;
        if (corridas > 0) {
            Corridas.medirTiempos(carpetaCorpus, carpetaResultados, corridas);
        } else {
            List<Medicion> mediciones = MedidorTiempos.medir(ALGORITMOS, corpus.frecuencias(), CALENTAMIENTO, REPETICIONES);
            Reporte.imprimirTiempos(mediciones);
            Reporte.guardarTiempos(carpetaResultados.resolve("tiempos.csv"), mediciones);
        }

        // 3. Ordenamiento con los tres algoritmos y validación (Tabla 5).
        List<Termino[]> ordenados = new ArrayList<>();
        List<String> nombres = new ArrayList<>();
        for (AlgoritmoOrdenamiento algoritmo : ALGORITMOS) {
            ordenados.add(algoritmo.ordenar(corpus.frecuencias()));
            nombres.add(algoritmo.nombre());
        }
        Reporte.imprimirTop(nombres, ordenados, TOP);
        Reporte.imprimirEmpate(ordenados.get(0), TOP);
        boolean iguales = ordenados.stream().allMatch(o -> Arrays.equals(o, ordenados.get(0)));
        System.out.println("Los tres algoritmos producen exactamente el mismo ranking completo: " + (iguales ? "SÍ" : "NO"));
        System.out.println("El ranking está en orden descendente de frecuencia: "
                + (esDescendente(ordenados.get(0)) ? "SÍ" : "NO"));
        System.out.println();

        Reporte.guardarCorpus(carpetaResultados.resolve("corpus.csv"), corpus.documentos());
        Reporte.guardarTop(carpetaResultados.resolve("top15.csv"), nombres, ordenados, TOP);
        Reporte.guardarRanking(carpetaResultados.resolve("ranking_completo.csv"), ordenados.get(0));

        // 4. Experimento complementario de escalabilidad (Figuras 1 y 2).
        if (argumentos.contains("--escalabilidad")) {
            if (corridas > 0) {
                Corridas.medirEscalabilidad(carpetaCorpus, carpetaResultados, CORRIDAS_ESCALABILIDAD);
            } else {
                System.out.println("Ejecutando experimento de escalabilidad (una corrida)...");
                Reporte.guardarTiempos(carpetaResultados.resolve("escalabilidad_n.csv"),
                        ExperimentoEscalabilidad.variarN(corpus.frecuencias(), ALGORITMOS));
                Reporte.guardarTiempos(carpetaResultados.resolve("escalabilidad_k.csv"),
                        ExperimentoEscalabilidad.variarK(ALGORITMOS));
            }
        }
        System.out.println("Resultados guardados en " + carpetaResultados.toAbsolutePath());
    }

    public static Corpus prepararCorpus(Path carpetaCorpus) throws IOException {
        List<Documento> documentos = CargadorCorpus.cargar(carpetaCorpus.resolve("pdfs"), carpetaCorpus.resolve("abstracts"));
        Preprocesador preprocesador = new Preprocesador();
        List<List<String>> tokens = documentos.stream()
                .map(d -> preprocesador.tokenizar(d.abstractTexto()))
                .toList();
        return new Corpus(documentos, tokens, ContadorFrecuencias.contar(tokens));
    }

    private static boolean esDescendente(Termino[] lista) {
        for (int i = 1; i < lista.length; i++) {
            if (lista[i - 1].frecuencia() < lista[i].frecuencia()) {
                return false;
            }
        }
        return true;
    }
}
