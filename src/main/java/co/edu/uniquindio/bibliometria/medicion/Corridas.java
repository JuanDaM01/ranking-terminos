package co.edu.uniquindio.bibliometria.medicion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import co.edu.uniquindio.bibliometria.App;

/**
 * Repite la medición en varias JVM nuevas, como se hizo para el documento: cada
 * corrida es un proceso
 * Java independiente (así el tiempo "en frío" es realmente la primera
 * ejecución) y al final se toma
 * la mediana entre corridas.
 */
public final class Corridas {

    private static final Locale ES = Locale.forLanguageTag("es-CO");

    private Corridas() {
    }

    /**
     * Tabla 4: mediana de N corridas del tiempo en frío y del tiempo en caliente.
     */
    public static void medirTiempos(Path carpetaCorpus, Path carpetaResultados, int corridas)
            throws IOException, InterruptedException {
        Path carpeta = carpetaResultados.resolve("corridas");
        Files.createDirectories(carpeta);
        System.out.println("=== Medición de tiempos en " + corridas + " corridas (una JVM nueva por corrida) ===");
        Map<String, List<double[]>> porAlgoritmo = new LinkedHashMap<>(); // algoritmo -> [frío, caliente] por corrida
        int n = 0;
        int k = 0;
        for (int i = 1; i <= corridas; i++) {
            Path csv = carpeta.resolve("tiempos_" + i + ".csv");
            ejecutarHijo(carpetaCorpus, "--interno-tiempos", csv.toString());
            for (String[] fila : leerCsv(csv)) {
                porAlgoritmo.computeIfAbsent(fila[0], a -> new ArrayList<>())
                        .add(new double[] { Double.parseDouble(fila[3]), Double.parseDouble(fila[4]) });
                n = Integer.parseInt(fila[1]);
                k = Integer.parseInt(fila[2]);
            }
            System.out.printf("  Corrida %d de %d lista%n", i, corridas);
        }

        double referencia = mediana(porAlgoritmo.values().iterator().next().stream().mapToDouble(v -> v[1]).toArray());
        List<String> csv = new ArrayList<>();
        csv.add("algoritmo,n,k,frio_ms_mediana,caliente_ms_mediana,caliente_ms_minimo,caliente_ms_maximo,relativo_counting");
        System.out.printf("%nTabla 4. Tiempos sobre la lista de frecuencias del corpus (n = %d, k = %d)%n", n, k);
        System.out.printf("%-15s %13s %17s %28s %12s%n", "Algoritmo", "En frío (ms)", "En caliente (ms)",
                "Rango en caliente (ms)", "Relativo");
        for (Map.Entry<String, List<double[]>> e : porAlgoritmo.entrySet()) {
            double[] frio = e.getValue().stream().mapToDouble(v -> v[0]).toArray();
            double[] caliente = e.getValue().stream().mapToDouble(v -> v[1]).toArray();
            double medFrio = mediana(frio);
            double medCaliente = mediana(caliente);
            double min = Arrays.stream(caliente).min().orElse(0);
            double max = Arrays.stream(caliente).max().orElse(0);
            System.out.printf(ES, "%-15s %13.3f %17.4f %15.4f - %-10.4f %10.1f x%n",
                    e.getKey(), medFrio, medCaliente, min, max, medCaliente / referencia);
            csv.add(String.format(Locale.ROOT, "%s,%d,%d,%.6f,%.6f,%.6f,%.6f,%.3f",
                    e.getKey(), n, k, medFrio, medCaliente, min, max, medCaliente / referencia));
        }
        System.out.printf(
                "(mediana de %d corridas; en caliente = mediana de %d ordenamientos tras %d rondas de calentamiento)%n%n",
                corridas, App.REPETICIONES, App.CALENTAMIENTO);
        escribir(carpetaResultados.resolve("tabla4_tiempos.csv"), csv);
    }

    /**
     * Figuras 1 y 2: mediana de varias corridas del experimento de escalabilidad.
     */
    public static void medirEscalabilidad(Path carpetaCorpus, Path carpetaResultados, int corridas)
            throws IOException, InterruptedException {
        System.out.println("=== Experimento de escalabilidad en " + corridas + " corridas ===");
        Path carpeta = carpetaResultados.resolve("corridas");
        for (int i = 1; i <= corridas; i++) {
            Path destino = carpeta.resolve("escalabilidad_" + i);
            Files.createDirectories(destino);
            ejecutarHijo(carpetaCorpus, "--interno-escalabilidad", destino.toString());
            System.out.printf("  Corrida %d de %d lista%n", i, corridas);
        }
        resumirEscalabilidad(carpeta, carpetaResultados, corridas, "escalabilidad_n.csv",
                "Figura 1. Nanosegundos por término según n (k <= 86)");
        resumirEscalabilidad(carpeta, carpetaResultados, corridas, "escalabilidad_k.csv",
                "Figura 2. Tiempo en ms según k (n = 100.000)");
    }

    private static void resumirEscalabilidad(Path carpeta, Path carpetaResultados, int corridas, String archivo,
            String titulo) throws IOException {
        Map<String, List<Double>> valores = new LinkedHashMap<>(); // "algoritmo,n,k" -> medianas de cada corrida
        for (int i = 1; i <= corridas; i++) {
            for (String[] fila : leerCsv(carpeta.resolve("escalabilidad_" + i).resolve(archivo))) {
                valores.computeIfAbsent(fila[0] + "," + fila[1] + "," + fila[2], c -> new ArrayList<>())
                        .add(Double.parseDouble(fila[4]));
            }
        }
        List<String> csv = new ArrayList<>();
        csv.add("algoritmo,n,k,mediana_ms,ns_por_termino");
        System.out.println();
        System.out.println(titulo);
        System.out.printf("%-15s %12s %12s %14s %16s%n", "Algoritmo", "n", "k", "Tiempo (ms)", "ns por término");
        for (Map.Entry<String, List<Double>> e : valores.entrySet()) {
            String[] clave = e.getKey().split(",");
            int n = Integer.parseInt(clave[1]);
            double ms = mediana(e.getValue().stream().mapToDouble(Double::doubleValue).toArray());
            double ns = ms * 1e6 / n;
            System.out.printf(ES, "%-15s %,12d %,12d %14.4f %16.1f%n", clave[0], n, Integer.parseInt(clave[2]), ms, ns);
            csv.add(String.format(Locale.ROOT, "%s,%d,%s,%.6f,%.3f", clave[0], n, clave[2], ms, ns));
        }
        escribir(carpetaResultados.resolve(archivo.replace(".csv", "_mediana.csv")), csv);
    }

    private static void ejecutarHijo(Path carpetaCorpus, String modo, String destino)
            throws IOException, InterruptedException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> comando = List.of(java, "-Dfile.encoding=UTF-8", "-cp", System.getProperty("java.class.path"),
                App.class.getName(), carpetaCorpus.toString(), modo, destino);
        Process proceso = new ProcessBuilder(comando)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        int codigo = proceso.waitFor();
        if (codigo != 0) {
            throw new IllegalStateException("La corrida terminó con código " + codigo);
        }
    }

    private static List<String[]> leerCsv(Path archivo) throws IOException {
        List<String> lineas = Files.readAllLines(archivo, StandardCharsets.UTF_8);
        return lineas.subList(1, lineas.size()).stream().filter(l -> !l.isBlank()).map(l -> l.split(",")).toList();
    }

    static double mediana(double[] valores) {
        double[] v = valores.clone();
        Arrays.sort(v);
        int m = v.length / 2;
        return v.length % 2 == 1 ? v[m] : (v[m - 1] + v[m]) / 2;
    }

    private static void escribir(Path archivo, List<String> lineas) throws IOException {
        Files.createDirectories(archivo.toAbsolutePath().getParent());
        Files.write(archivo, lineas, StandardCharsets.UTF_8);
    }
}
