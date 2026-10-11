import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * {@code head [-n <count>] [<file>...]}: prints the first {@code count} lines (default {@code 10})
 * of each file.
 *
 * <p>With more than one file, each file's output is preceded by an {@code "==> <file> <=="}
 * header line, with a blank line separating consecutive files' output (no header for a single
 * file or standard input).
 */
public class Head extends ShellCommand {
    private static final int DEFAULT_COUNT = 10;

    public Head(String[] args) {
        super(args);
    }

    public static void main(String[] args) {
        ShellCommand.start(Head.class, args);
    }

    @Override
    protected void runCommand() throws IOException {
        int count = DEFAULT_COUNT;
        int firstFile = 0;
        if (cmdArgs.length > 0 && cmdArgs[0].equals("-n")) {
            try {
                if (cmdArgs.length < 2) throw new NumberFormatException();
                count = Integer.parseInt(cmdArgs[1]);
                if (count < 0) throw new NumberFormatException();
                firstFile = 2;
            } catch (NumberFormatException e) {
                System.err.println("Usage: head [-n <count>] [<file>...]");
                return;
            }
        }
        if (firstFile == cmdArgs.length) {
            printHead(System.in, count);
            return;
        }
        boolean multiple = cmdArgs.length - firstFile > 1;
        boolean printed = false;
        for (int i = firstFile; i < cmdArgs.length; i++) {
            Path file = Paths.get(cmdArgs[i]);
            try (InputStream input = getFileInput(file)) {
                if (multiple) {
                    if (printed) System.out.println();
                    System.out.println("==> " + file + " <==");
                }
                printHead(input, count);
                printed = true;
            } catch (IOException | IllegalArgumentException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    private void printHead(InputStream input, int count) throws IOException {
        for (int i = 0; i < count; i++) {
            String line = readLineRaw(input);
            if (line == null) break;
            if (line.endsWith("\n")) {
                int end = line.length() - 1;
                if (end > 0 && line.charAt(end - 1) == '\r') end--;
                System.out.println(line.substring(0, end));
            } else {
                System.out.print(line);
            }
        }
    }
}
