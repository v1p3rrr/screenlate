import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Renders lines of text into a phone-sized PNG for overlay and OCR checks, shaped by Java2D: scripts whose marks stack
 * or reorder (Thai, Khmer) come out as a phone draws them, which `text-image.py` (Pillow without a shaping library)
 * does not do.
 *
 * <p>Usage: {@code FONT=<font file> [FALLBACK=<font file>] [SIZE=72] java scripts/TextImage.java OUT.png "line" ...}
 *
 * <p>An argument {@code @FILE} stands for the lines of a UTF-8 file: the Windows launcher passes arguments through the
 * ANSI code page, which turns scripts outside it into question marks.
 *
 * <p>Characters FONT lacks (digits and Latin punctuation in script-only fonts such as Noto Sans Thai) are drawn with
 * FALLBACK; a combining mark stays with the run of its letter. Lines start 60 px from the left, the first baseline at
 * 260 px, one line every 1.9 font sizes.
 */
public class TextImage {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 2856;

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || System.getenv("FONT") == null) {
            System.err.println("Usage: FONT=<font file> [FALLBACK=<font file>] [SIZE=72] java scripts/TextImage.java OUT.png \"line\" ...");
            System.exit(1);
        }
        float size = Float.parseFloat(System.getenv().getOrDefault("SIZE", "72"));
        Font font = Font.createFont(Font.TRUETYPE_FONT, new File(System.getenv("FONT"))).deriveFont(size);
        String fallbackPath = System.getenv("FALLBACK");
        Font fallback = fallbackPath == null ? font : Font.createFont(Font.TRUETYPE_FONT, new File(fallbackPath)).deriveFont(size);

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(new Color(0x20, 0x20, 0x20));
        int y = 260;
        for (String line : lines(Arrays.copyOfRange(args, 1, args.length))) {
            float x = 60;
            int start = 0;
            while (start < line.length()) {
                boolean own = drawnWith(font, line.codePointAt(start), true);
                int end = start + Character.charCount(line.codePointAt(start));
                while (end < line.length() && drawnWith(font, line.codePointAt(end), own) == own) {
                    end += Character.charCount(line.codePointAt(end));
                }
                String run = line.substring(start, end);
                Font runFont = own ? font : fallback;
                g.setFont(runFont);
                g.drawString(run, x, y);
                x += (float) runFont.getStringBounds(run, g.getFontRenderContext()).getWidth();
                start = end;
            }
            y += Math.round(size * 1.9f);
        }
        g.dispose();
        ImageIO.write(image, "png", new File(args[0]));
    }

    private static List<String> lines(String[] args) throws Exception {
        List<String> lines = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("@")) {
                lines.addAll(Files.readAllLines(Path.of(arg.substring(1)), StandardCharsets.UTF_8));
            } else {
                lines.add(arg);
            }
        }
        return lines;
    }

    /** Whether [codePoint] goes into a run of [font]; marks and spaces stay in the current run ([current]). */
    private static boolean drawnWith(Font font, int codePoint, boolean current) {
        int type = Character.getType(codePoint);
        if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.COMBINING_SPACING_MARK
            || codePoint == ' ') {
            return current;
        }
        return font.canDisplay(codePoint);
    }
}
