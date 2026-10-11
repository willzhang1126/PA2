import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * {@code ls [<path>...]}: lists files and directories. With no arguments, lists the current
 * directory.
 *
 * <p>Files print first (sorted by name), then directories (sorted by name), blank-line separated.
 * A directory's contents get a {@code "<dir>:"} header only when more than one argument was given.
 */
public class Ls extends ShellCommand {

    public Ls(String[] args) {
        super(args);
    }

    public static void main(String[] args) {
        ShellCommand.start(Ls.class, args);
    }

    @Override
    protected void runCommand() throws IOException {
        String[] targets = cmdArgs.length == 0 ? new String[]{"."} : cmdArgs;

        java.util.List<Path> files = new java.util.ArrayList<>();
        java.util.List<Path> dirs = new java.util.ArrayList<>();

        for (String arg : targets) {
            try {
                Path target = Path.of(arg);
                checkExists(target);
                if (Files.isDirectory(target)) {
                    dirs.add(target);
                } else {
                    files.add(target);
                }
            } catch (IllegalArgumentException e) {
                System.err.println(e.getMessage());
            }
        }

        files.sort(Comparator.comparing(Path::toString));
        dirs.sort(Comparator.comparing(Path::toString));

        if (targets.length == 1 && dirs.size() == 1 && files.isEmpty()) {
            printDirectoryEntries(dirs.get(0));
            return;
        }

        for (Path file : files) {
            System.out.println(file);
        }

        if (!files.isEmpty() && !dirs.isEmpty()) {
            System.out.println();
        }

        for (int i = 0; i < dirs.size(); i++) {
            if (i > 0) {
                System.out.println();
            }
            Path dir = dirs.get(i);
            System.out.println(dir + ":");
            printDirectoryEntries(dir);
        }
    }

    private static void printDirectoryEntries(Path dir) {
        try (var children = Files.list(dir)) {
            List<Path> entries = children
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();

            for (Path entry : entries) {
                System.out.println(entry.getFileName());
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

}
