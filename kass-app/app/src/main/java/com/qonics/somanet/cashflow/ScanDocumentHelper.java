package com.qonics.somanet.cashflow;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Document cleanup + export helpers (Bitmap APIs only; no extra Gradle deps).
 */
public final class ScanDocumentHelper {

    private ScanDocumentHelper() {
    }

    /** Contrast + grayscale cleanup for scanned receipts/invoices. */
    public static Bitmap cleanScan(Bitmap source) {
        if (source == null) return null;
        Bitmap out = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        ColorMatrix gray = new ColorMatrix();
        gray.setSaturation(0f);
        ColorMatrix contrast = new ColorMatrix(new float[]{
                1.35f, 0, 0, 0, -28f,
                0, 1.35f, 0, 0, -28f,
                0, 0, 1.35f, 0, -28f,
                0, 0, 0, 1, 0
        });
        gray.postConcat(contrast);
        paint.setColorFilter(new ColorMatrixColorFilter(gray));
        canvas.drawBitmap(source, 0, 0, paint);
        return out;
    }

    public static byte[] compressJpeg(Bitmap bitmap, int quality) {
        if (bitmap == null) return null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int q = Math.max(40, Math.min(95, quality));
        bitmap.compress(Bitmap.CompressFormat.JPEG, q, bos);
        return bos.toByteArray();
    }

    /**
     * Write JPEG bytes, or a minimal single-page PDF wrapping the JPEG.
     * Falls back to .jpg if PDF assembly fails.
     *
     * @return output file (may change extension on PDF fallback)
     */
    public static File writeDocument(File destDir, String baseName, Bitmap bitmap,
                                     boolean clean, boolean asPdf) throws IOException {
        if (destDir == null) throw new IOException("Missing output directory");
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("Cannot create output directory");
        }
        Bitmap work = bitmap;
        Bitmap cleaned = null;
        if (clean) {
            cleaned = cleanScan(bitmap);
            if (cleaned != null) work = cleaned;
        }
        byte[] jpeg = compressJpeg(work, 85);
        if (cleaned != null && cleaned != bitmap) cleaned.recycle();
        if (jpeg == null || jpeg.length == 0) throw new IOException("JPEG encode failed");

        if (asPdf) {
            File pdfFile = new File(destDir, sanitize(baseName) + ".pdf");
            try {
                writeSimpleJpegPdf(pdfFile, jpeg, work.getWidth(), work.getHeight());
                return pdfFile;
            } catch (Exception e) {
                // Fall through to JPEG
            }
        }
        File jpgFile = new File(destDir, sanitize(baseName) + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(jpgFile)) {
            fos.write(jpeg);
            fos.flush();
        }
        return jpgFile;
    }

    public static String toBase64(byte[] data) {
        if (data == null) return "";
        return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
    }

    public static byte[] fileToBytes(File file) throws IOException {
        java.io.FileInputStream fis = new java.io.FileInputStream(file);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = fis.read(buf)) >= 0) {
            bos.write(buf, 0, n);
        }
        fis.close();
        return bos.toByteArray();
    }

    private static String sanitize(String name) {
        if (name == null || name.trim().isEmpty()) return "scan";
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /**
     * Minimal PDF 1.4 with one embedded JPEG image filling the page.
     */
    private static void writeSimpleJpegPdf(File out, byte[] jpeg, int imgW, int imgH) throws IOException {
        float pageW = 595f;
        float pageH = 842f;
        float scale = Math.min(pageW / Math.max(1, imgW), pageH / Math.max(1, imgH));
        float drawW = imgW * scale;
        float drawH = imgH * scale;
        float x = (pageW - drawW) / 2f;
        float y = (pageH - drawH) / 2f;

        String content = String.format(Locale.US,
                "q\n%.2f 0 0 %.2f %.2f %.2f cm\n/Im0 Do\nQ\n",
                drawW, drawH, x, y);
        byte[] contentBytes = content.getBytes(StandardCharsets.US_ASCII);

        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        write(pdf, "%PDF-1.4\n");
        int[] offsets = new int[6];

        offsets[1] = pdf.size();
        write(pdf, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        offsets[2] = pdf.size();
        write(pdf, "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");

        offsets[3] = pdf.size();
        write(pdf, String.format(Locale.US,
                "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %.2f %.2f] "
                        + "/Contents 4 0 R /Resources << /XObject << /Im0 5 0 R >> >> >>\nendobj\n",
                pageW, pageH));

        offsets[4] = pdf.size();
        write(pdf, "4 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n");
        pdf.write(contentBytes);
        write(pdf, "\nendstream\nendobj\n");

        offsets[5] = pdf.size();
        write(pdf, String.format(Locale.US,
                "5 0 obj\n<< /Type /XObject /Subtype /Image /Width %d /Height %d "
                        + "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length %d >>\nstream\n",
                imgW, imgH, jpeg.length));
        pdf.write(jpeg);
        write(pdf, "\nendstream\nendobj\n");

        int xref = pdf.size();
        write(pdf, "xref\n0 6\n");
        write(pdf, "0000000000 65535 f \n");
        for (int i = 1; i <= 5; i++) {
            write(pdf, String.format(Locale.US, "%010d 00000 n \n", offsets[i]));
        }
        write(pdf, "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");

        try (FileOutputStream fos = new FileOutputStream(out)) {
            pdf.writeTo(fos);
            fos.flush();
        }
    }

    private static void write(ByteArrayOutputStream bos, String s) throws IOException {
        bos.write(s.getBytes(StandardCharsets.US_ASCII));
    }
}
