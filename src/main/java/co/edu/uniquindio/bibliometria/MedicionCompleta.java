package co.edu.uniquindio.bibliometria;

/**
 * Atajo para ejecutar desde el IDE la medición con la metodología del documento:
 * 10 corridas en JVM nuevas (Tabla 4) y 3 corridas del experimento de escalabilidad (Figuras 1 y 2).
 * Equivale a: java -jar ranking-terminos.jar corpus --corridas 10 --escalabilidad
 */
public final class MedicionCompleta {

    private MedicionCompleta() {
    }

    public static void main(String[] args) throws Exception {
        App.main(new String[] {"corpus", "--corridas", "10", "--escalabilidad"});
    }
}
