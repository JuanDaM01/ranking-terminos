package co.edu.uniquindio.bibliometria.corpus;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Extrae el texto plano de un PDF con Apache PDFBox. Devuelve dos textos: el de las primeras
 * páginas, donde está el abstract, y el del artículo completo, que se usa como vocabulario para
 * decidir cómo unir las palabras partidas al final de línea.
 */
public final class LectorPdf {

    private static final int PAGINAS_DEL_ABSTRACT = 3;

    public record TextoPdf(String primerasPaginas, String completo) {
    }

    private LectorPdf() {
    }

    public static TextoPdf leer(Path pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper inicio = new PDFTextStripper();
            inicio.setStartPage(1);
            inicio.setEndPage(Math.min(PAGINAS_DEL_ABSTRACT, documento.getNumberOfPages()));
            String primerasPaginas = inicio.getText(documento);
            String completo = new PDFTextStripper().getText(documento);
            return new TextoPdf(primerasPaginas, completo);
        }
    }
}
