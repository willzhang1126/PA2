import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * {@code grep <pattern> [<file>...]}: prints every line, from the given files or from standard
 * input, that matches the {@link java.util.regex.Pattern regular expression} {@code pattern}.
 *
 * <p>With more than one file, each matching line is prefixed with {@code "<filename>:"}.
 */
public class Grep extends ShellCommand {

    public Grep(String[] args) {
        super(args);
    }

    public static void main(String[] args) {
        ShellCommand.start(Grep.class, args);
    }

    @Override
    protected void runCommand() {
        if (cmdArgs.length == 0) {
            System.err.printf("Usage: grep <pattern> [<file>...]%n");
            return;
        }

        Pattern pattern;
        try {
            pattern = Pattern.compile(cmdArgs[0]);
        } catch (PatternSyntaxException e) {
            System.err.println(cmdName + ": " + e.getMessage());
            return;
        }
        boolean prefixFiles = cmdArgs.length > 2;

        if (cmdArgs.length == 1) {
            try {
                String line;
                while ((line = readLineRaw(System.in)) != null) {
                    boolean endOfInput = !line.endsWith("\n");
                    line = line.replaceFirst("\\r?\\n$", "");
                    if (pattern.matcher(line).find()) {
                        System.out.println(line);
                    }
                    if (endOfInput) {
                        break;
                    }
                }
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
            return;
        }

        for (int i = 1; i < cmdArgs.length; i++) {
            String arg = cmdArgs[i];
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(getFileInput(Path.of(arg)), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (pattern.matcher(line).find()) {
                        if (prefixFiles) {
                            System.out.println(arg + ":" + line);
                        } else {
                            System.out.println(line);
                        }
                    }
                }
            } catch (IllegalArgumentException | IOException e) {
                System.err.println(e.getMessage());
            }
        }
    }
}
