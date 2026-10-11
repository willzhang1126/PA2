import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * {@code cat}: prints the contents of one or more files to standard out, in order.
 *
 * <p>On a missing file or directory, print the error and keep going with the rest —
 * {@link #getFileInput} already throws a ready-to-print {@link IllegalArgumentException}.
 */
public class Cat extends ShellCommand {

    public Cat(String[] args) {
        super(args);
    }

    public static void main(String[] args) throws Exception {
        ShellCommand.start(Cat.class, args);
    }

    @Override
    protected void runCommand() throws IOException {
        if (cmdArgs.length == 0) {
            String line;
            while ((line = readLineRaw(System.in)) != null) {
                System.out.print(line);
                // A partial line means readLineRaw already consumed EOF or Ctrl-D.
                if (!line.endsWith("\n")) {
                    break;
                }
            }
            return;
        }

        for (String arg : cmdArgs) {
            try (InputStream input = getFileInput(Path.of(arg))) {
                input.transferTo(System.out);
            } catch (IllegalArgumentException | IOException e) {
                System.err.println(e.getMessage());
            }
        }
    }
}
