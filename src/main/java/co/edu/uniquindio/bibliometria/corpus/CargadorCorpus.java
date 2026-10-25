package co.edu.uniquindio.bibliometria.corpus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Carga el abstract de cada PDF del corpus. El abstract extraído se guarda en
 * {@code abstracts/<archivo>.txt}; si ese archivo ya existe se usa tal cual, de modo que
 * cualquier abstract mal delimitado puede corregirse a mano y volver a ejecutar.
 */
public final class CargadorCorpus {

    public record Documento(String archivo, String abstractTexto, boolean extraidoAhora) {
        public int palabras() {
            return abstractTexto.isBlank() ? 0 : abstractTexto.split("\\s+").length;
        }
    }

    private static final Pattern NUMERO_INICIAL = Pattern.compile("^(\\d{1,9})");

    private CargadorCorpus() {
    }

    /** Número con el que empieza el nombre del archivo ("12. A meta..." → 12), para el orden 1..20. */
    private static int numeroInicial(Path archivo) {
        Matcher numero = NUMERO_INICIAL.matcher(archivo.getFileName().toString());
        return numero.find() ? Integer.parseInt(numero.group(1)) : Integer.MAX_VALUE;
    }

    public static List<Documento> cargar(Path carpetaPdfs, Path carpetaAbstracts) throws IOException {
        Files.createDirectories(carpetaAbstracts);
        List<Path> pdfs;
        try (Stream<Path> archivos = Files.list(carpetaPdfs)) {
            pdfs = archivos.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .sorted(Comparator.comparingInt(CargadorCorpus::numeroInicial)
                            .thenComparing(p -> p.getFileName().toString()))
                    .toList();
        }

        List<Documento> documentos = new ArrayList<>();
        for (Path pdf : pdfs) {
            String nombre = pdf.getFileName().toString();
            Path txt = carpetaAbstracts.resolve(nombre.substring(0, nombre.length() - 4) + ".txt");
            if (Files.exists(txt)) {
                documentos.add(new Documento(nombre, Files.readString(txt, StandardCharsets.UTF_8).strip(), false));
            } else {
                LectorPdf.TextoPdf texto = LectorPdf.leer(pdf);
                String abstractTexto = DelimitadorAbstract.extraer(texto.primerasPaginas(), texto.completo());
                Files.writeString(txt, abstractTexto, StandardCharsets.UTF_8);
                documentos.add(new Documento(nombre, abstractTexto, true));
            }
        }
        return documentos;
    }
}
