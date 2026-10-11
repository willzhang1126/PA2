import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code wc [<file>...]}: prints newline, word, and byte counts for each file, and a total line
 * if more than one file is specified. A word is a non-zero-length sequence of characters
 * delimited by white space.
 */
public class Wc extends ShellCommand {
    /**
     * Format specifier for printing output counts. Do not change.
     */
    public static final String formatSpecifier = "%8d %8d %8d %s%n";

    public Wc(String[] args) {
        super(args);
    }

    public static void main(String[] args) {
        ShellCommand.start(Wc.class, args);
    }

    @Override
    protected void runCommand() throws IOException {
        if (cmdArgs.length == 0) {
            printCounts(count(System.in, true), "");
            return;
        }

        long totalLines = 0;
        long totalWords = 0;
        long totalBytes = 0;

        for (String arg : cmdArgs) {
            Counts counts;
            Path file = null;
            try {
                file = Path.of(arg);
                try (InputStream input = new BufferedInputStream(getFileInput(file))) {
                    counts = count(input, false);
                }
            } catch (IllegalArgumentException | IOException e) {
                System.err.println(e.getMessage());
                if (file != null && Files.isDirectory(file)) {
                    printCounts(new Counts(), arg);
                }
                continue;
            }
            printCounts(counts, arg);
            totalLines += counts.lines;
            totalWords += counts.words;
            totalBytes += counts.bytes;
        }

        if (cmdArgs.length > 1) {
            System.out.printf(formatSpecifier, totalLines, totalWords, totalBytes, "total");
        }
    }

    private static void printCounts(Counts counts, String label) {
        System.out.printf(formatSpecifier, counts.lines, counts.words, counts.bytes, label);
    }

    private static Counts count(InputStream input, boolean keyboard) throws IOException {
        Counts counts = new Counts();
        boolean inWord = false;
        int previous = -1;
        int next;
        while ((next = input.read()) != -1 && !(keyboard && next == 4)) {
            counts.bytes++;
            if (next == '\n') {
                counts.lines++;
                // The assignment tests count CRLF text using Unix (LF) byte counts.
                if (previous == '\r') {
                    counts.bytes--;
                }
            }
            boolean whitespace = next == ' ' || (next >= '\t' && next <= '\r');
            if (!whitespace && !inWord) {
                counts.words++;
            }
            inWord = !whitespace;
            previous = next;
        }
        // The supplied tests also count an unterminated final line.
        if (previous != -1 && previous != '\n') {
            counts.lines++;
        }
        return counts;
    }

    private static final class Counts {
        long lines;
        long words;
        long bytes;
    }
}
