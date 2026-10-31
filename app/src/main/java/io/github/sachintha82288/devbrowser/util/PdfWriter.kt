package io.github.sachintha82288.devbrowser.util

import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Stage A: minimal single-page PDF writer.
 *
 * The captured page is embedded as one JPEG using the `DCTDecode` filter, so no
 * third-party PDF library is needed. The page box keeps the screenshot's aspect
 * ratio with a 595 pt width (A4 width).
 */
object PdfWriter {

    fun fromJpeg(jpeg: ByteArray, widthPx: Int, heightPx: Int): ByteArray {
        require(widthPx > 0 && heightPx > 0) { "invalid image size" }
        val pageWidth = 595.0
        val pageHeight = pageWidth * heightPx / widthPx
        val height = String.format(Locale.US, "%.2f", pageHeight)

        val out = ByteArrayOutputStream()
        val offsets = ArrayList<Int>()

        fun write(text: String) = out.write(text.toByteArray(Charsets.ISO_8859_1))
        fun objStart(index: Int) {
            offsets.add(out.size())
            write("$index 0 obj\n")
        }

        write("%PDF-1.4\n")
        // Binary comment marks the file as binary for naive parsers.
        out.write(byteArrayOf(0x25, 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 0x0A))

        objStart(1)
        write("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        objStart(2)
        write("<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")

        objStart(3)
        write(
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${String.format(Locale.US, "%.2f", pageWidth)} $height] " +
                "/Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>\nendobj\n",
        )

        objStart(4)
        write(
            "<< /Type /XObject /Subtype /Image /Width $widthPx /Height $heightPx " +
                "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode " +
                "/Length ${jpeg.size} >>\nstream\n",
        )
        out.write(jpeg)
        write("\nendstream\nendobj\n")

        val content = "q ${String.format(Locale.US, "%.2f", pageWidth)} 0 0 $height 0 0 cm /Im0 Do Q\n"
        objStart(5)
        write("<< /Length ${content.length} >>\nstream\n$content\nendstream\nendobj\n")

        val xrefOffset = out.size()
        write("xref\n0 6\n0000000000 65535 f \n")
        offsets.forEach { offset ->
            write(String.format(Locale.US, "%010d 00000 n \n", offset))
        }
        write("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        return out.toByteArray()
    }
}
