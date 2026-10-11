import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code sort [-r] [<file>...]}: reads every line from all given files (or standard input, if
 * none given), concatenated in argument order, and prints them sorted lexicographically —
 * ascending by default, descending with {@code -r}.
 *
 * <p>Unlike {@code head}/{@code tail}, files are merged into one sorted stream rather than
 * processed (and headed) independently — that's how real {@code sort} behaves too.
 */
public class Sort extends ShellCommand {

    public Sort(String[] args) {
        super(args);
    }

    public static void main(String[] args) {
        ShellCommand.start(Sort.class, args);
    }

    @Override
    protected void runCommand() throws IOException {
        boolean reverse = cmdArgs.length > 0 && cmdArgs[0].equals("-r");
        int firstFile = reverse ? 1 : 0;
        List<String> lines = new ArrayList<>();
        if (firstFile == cmdArgs.length) {
            readLines(System.in, lines);
        } else {
            for (int i = firstFile; i < cmdArgs.length; i++) {
                try (InputStream input = getFileInput(Paths.get(cmdArgs[i]))) {
                    readLines(input, lines);
                } catch (IOException | IllegalArgumentException e) {
                    System.err.println(e.getMessage());
                }
            }
        }
        Collections.sort(lines);
        if (reverse) Collections.reverse(lines);
        for (String line : lines) {
            if (line.endsWith("\n")) {
                int end = line.length() - 1;
                if (end > 0 && line.charAt(end - 1) == '\r') end--;
                System.out.println(line.substring(0, end));
            } else {
                System.out.print(line);
            }
        }
    }

    private void readLines(InputStream input, List<String> lines) throws IOException {
        String line;
        while ((line = readLineRaw(input)) != null) lines.add(line);
    }
}
