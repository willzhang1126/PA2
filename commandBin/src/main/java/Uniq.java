import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * {@code uniq [-c] [<file>]}: collapses runs of <b>adjacent</b> duplicate lines into a single
 * line — non-adjacent duplicates are left alone, same as real {@code uniq} (pair with
 * {@code sort} first to dedupe an entire file). With {@code -c}, each output line is prefixed
 * with the number of times it occurred in its run.
 *
 * <p>Unlike the other commands, {@code uniq} takes at most <b>one</b> file argument — that's a
 * real constraint of the actual Unix command, not a simplification for this project.
 */
public class Uniq extends ShellCommand {

    public Uniq(String[] args) {
        super(args);
    }

    public static void main(String[] args) {
        ShellCommand.start(Uniq.class, args);
    }

    @Override
    protected void runCommand() throws IOException {
        boolean counted = cmdArgs.length > 0 && cmdArgs[0].equals("-c");
        int firstFile = counted ? 1 : 0;
        if (cmdArgs.length - firstFile > 1) {
            System.err.println("Usage: uniq [-c] [<file>]");
            return;
        }
        if (firstFile == cmdArgs.length) {
            printUnique(System.in, counted);
        } else {
            try (InputStream input = getFileInput(Paths.get(cmdArgs[firstFile]))) {
                printUnique(input, counted);
            } catch (IOException | IllegalArgumentException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    private void printUnique(InputStream input, boolean counted) throws IOException {
        String previous = null;
        int count = 0;
        String line;
        while ((line = readLineRaw(input)) != null) {
            if (line.equals(previous)) {
                count++;
            } else {
                if (previous != null) printRun(previous, count, counted);
                previous = line;
                count = 1;
            }
        }
        if (previous != null) printRun(previous, count, counted);
    }

    private void printRun(String line, int count, boolean counted) {
        if (counted) System.out.print(count + " ");
        if (line.endsWith("\n")) {
            int end = line.length() - 1;
            if (end > 0 && line.charAt(end - 1) == '\r') end--;
            System.out.println(line.substring(0, end));
        } else {
            System.out.print(line);
        }
    }
}
