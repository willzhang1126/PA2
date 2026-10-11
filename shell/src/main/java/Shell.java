import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A simple shell that mimics some basic features of bash. Java has no real fork/exec, so each
 * command runs as a separate forked {@code java} process (see {@link ProcessBuilder}) that locates
 * and loads the real command class at runtime via a {@link URLClassLoader} and
 * <a href="https://docs.oracle.com/javase/tutorial/reflect/">reflection</a> — {@code shell} has no
 * compile-time dependency on {@code commandBin}. See {@code README.md} for the full picture;
 * {@link #executeCommand} is where it comes together.
 */
public class Shell {
    public static final String COMMAND_PROMPT = "> ";
    private static final Path PATH = getPath();
    private final List<Pipeline> jobs = new ArrayList<>();
    /**
     * Represents the current working directory of the shell
     * (since the actual current working directory of a JVM cannot be changed).
     * This is the directory that command processes will be run in.
     */
    private Path cwd = Paths.get(System.getProperty("user.dir"));

    /**
     * Returns the path of the {@code Shell.class} file, via {@code getProtectionDomain()
     * .getCodeSource().getLocation()} and a
     * <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URI.html">URI</a>
     * conversion ({@link Paths#get(java.net.URI)} needs a {@code URI}, not a {@link URL}).
     */
    public static Path getCurrentClassPath() {
        try {
            return Paths.get(Shell.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Absolute path of {@code commandBin}'s compiled classes, computed relative to
     * {@link #getCurrentClassPath} so it isn't hardcoded to one machine's checkout location.
     */
    public static Path getPath() {
        return getCurrentClassPath().resolve("../../../commandBin/target/classes").toAbsolutePath().normalize();
    }

    public static void main(String[] args) {
        Shell shell = new Shell();
        if (args.length > 0) {
            shell.executeCommand(args[0]);
        } else {
            shell.runRepl();
        }
    }

    /**
     * Searches the PATH directory for a .class file corresponding to the given command.
     * Class names are converted from PascalCase to kebab-case before attempting to match.
     *
     * @param command the command name
     * @return the matched class name
     * @throws Exception if a match class cannot be found
     */
    private static String findCommandClass(String command) throws Exception {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command not found");
        }

        try (var entries = Files.list(PATH)) {
            for (Path entry : entries.toList()) {
                if (!Files.isRegularFile(entry) || !entry.getFileName().toString().endsWith(".class")) {
                    continue;
                }
                String className = entry.getFileName().toString().replace(".class", "");
                if (command.equals(classNameToCommandName(className))) {
                    return className;
                }
            }
        }

        throw new IllegalArgumentException(command + ": command not found");
    }

    /**
     * Converts a PascalCase class name to kebab-case (e.g. {@code "WordCount"} to
     * {@code "word-count"}), splitting on lower-to-upper boundaries via
     * <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html">lookaround regex</a>
     * so acronym runs stay together.
     *
     * @param className the java class name
     * @return the kebab-case equivalent command name
     */
    public static String classNameToCommandName(String className) {
        // Split on uppercase letter boundaries, handling acronyms safely
        String[] words = className.split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
        // Lowercase and join with hyphens
        return Arrays.stream(words)
                .map(String::toLowerCase)
                .collect(Collectors.joining("-"));
    }

    public Path getCwd() {
        return cwd;
    }

    public void setCwd(Path path) {
        cwd = path;
    }

    /**
     * Runs the Read-Eval-Print Loop of the Shell. The command "exit" ends the loop.
     */
    public void runRepl() {
        try {
            while (true) {
                System.out.print(COMMAND_PROMPT);
                System.out.flush();
                // Avoid reading ahead: commands may need to read from System.in themselves.
                ByteArrayOutputStream line = new ByteArrayOutputStream();
                int next;
                while ((next = System.in.read()) != -1 && next != 4 && next != '\n') {
                    line.write(next);
                }
                if ((next == -1 || next == 4) && line.size() == 0) {
                    break;
                }
                String command = line.toString(StandardCharsets.UTF_8).strip();
                if (command.isBlank()) {
                    continue;
                }
                if ("exit".equals(command)) {
                    break;
                }
                processInput(command);
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }


    /**
     * Takes the input string and parses it to execute either a shell builtin or a pipeline of separate processes.
     *
     * @param input the command to execute
     * @return null if the input was a builtin command or if there was an error,
     * otherwise the pipeline that was executed (for testing purposes)
     */
    Pipeline processInput(String input) {
        if (input == null || input.isBlank()) return null;
        try {
            input = input.strip();
            if (tryExecuteBuiltin(input)) return null;
            Pipeline pipeline = new Pipeline(input);
            pipeline.executePipeline();
            if (pipeline.isBackground()) jobs.add(pipeline);
            return pipeline;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println(e.getMessage());
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        return null;
    }

    /**
     * Executes the main method of the given command, passing along any additional args.
     * Should only be called when {@code Shell.main()} is called with non-zero number of args.
     * Does nothing if the given command is blank (only whitespace).
     *
     * <p><b>Given to you as a worked example</b> — this is where the class Javadoc's three ideas
     * (URI, ClassLoader, reflection) actually get used; you don't need to write this kind of code
     * yourself, but understanding it will help you debug {@link #findCommandClass} and, later,
     * extend the shell:
     * <ol>
     *     <li>{@link #findCommandClass} resolves the command name to a class name.</li>
     *     <li>{@link #PATH} (the directory holding {@code commandBin}'s compiled classes) is
     *     turned into a {@link URL}.</li>
     *     <li>A {@link URLClassLoader} opened on that URL loads the class — this is what makes
     *     the class reachable at all, since {@code commandBin} isn't on this module's classpath.</li>
     *     <li>{@code loadedClass.getMethod("main", String[].class)} then
     *     {@code .invoke(null, (Object) commandArgs)} calls that class's {@code main} via
     *     reflection — the same cast trick as {@code ShellCommand.start()}.</li>
     *     <li>Because this only ever runs inside a freshly-forked child JVM whose whole purpose
     *     was to run this one command, it reports its own exit status
     *     ({@code System.exit(0)}/{@code System.exit(1)}) like any Unix process would.</li>
     * </ol>
     * See {@code README.md} for a plainer walkthrough of the four ideas.
     *
     * @param command the command to execute
     */
    void executeCommand(String command) {
        if (command.isBlank()) {
            return;
        }
        String[] commandArgs = command.split("\\s+");
        String commandName = commandArgs[0];
        try {
            // Get Class name for the command
            String commandClass = findCommandClass(commandName);
            // Create URL pointing to the classpath location
            File file = new File(String.valueOf(PATH));
            URL url = file.toURI().toURL();

            // Define the isolated ClassLoader
            try (URLClassLoader loader = new URLClassLoader(new URL[]{url}, Thread.currentThread().getContextClassLoader())) {
                // Load the target class
                Class<?> loadedClass = Class.forName(commandClass, true, loader);
                java.lang.reflect.Method mainMethod = loadedClass.getMethod("main", String[].class);
                mainMethod.invoke(null, (Object) commandArgs);
            }
            System.exit(0);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Runs {@code input} as a builtin ({@code cd}, {@code kill}, {@code jobs}) if it is one.
     * Builtins run in the main shell process, not forked, since they mutate the shell's own state
     * ({@code cwd}, {@code jobs}).
     *
     * @param input the command string
     * @return true if the command was a builtin, false otherwise
     */
    private boolean tryExecuteBuiltin(String input) {
        String[] args = input.strip().split("\\s+");
        switch (args[0]) {
            case "cd":
                if (args.length != 2) System.err.println("Usage: cd <dir>");
                else changeDirectory(args[1]);
                return true;
            case "jobs":
                listJobs();
                return true;
            case "kill":
                List<Integer> ids = new ArrayList<>();
                try {
                    if (args.length == 1) throw new NumberFormatException();
                    for (int i = 1; i < args.length; i++) {
                        int id = Integer.parseInt(args[i]);
                        if (id <= 0) throw new NumberFormatException();
                        ids.add(id);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("kill: arguments must be job IDs");
                    return true;
                }
                killJobs(ids);
                return true;
            default:
                return false;
        }
    }

    /**
     * Builds the {@code ProcessBuilder} for
     * {@code java -classpath <Shell's own classes> Shell "<command>"} — the fork workaround
     * described in the class Javadoc. Sets the child's working directory to {@link #cwd}, and
     * redirects output to a file if {@code command} ends in {@code > <file>}.
     *
     * @param command the string passed to the new {@code Shell} process as an argument
     *                (after removing the redirect if there is one)
     * @return the {@code ProcessBuilder} for the "forked" process
     * @throws IllegalArgumentException if a redirect file is given and it is a directory
     */
    ProcessBuilder buildForkedShell(String command) throws IllegalArgumentException {
        String[] parts = command.split(">", -1);
        if (parts.length > 2 || (parts.length == 2 && parts[1].isBlank())) {
            throw new IllegalArgumentException("Invalid output redirection");
        }
        ProcessBuilder pb = new ProcessBuilder("java", "-classpath",
                getCurrentClassPath().toString(), "Shell", parts[0].strip());
        pb.directory(cwd.toFile());
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        if (parts.length == 2) {
            String filename = parts[1].strip();
            Path target = cwd.resolve(filename).normalize();
            if (Files.isDirectory(target)) {
                throw new IllegalArgumentException(filename + ": Is a directory");
            }
            pb.redirectOutput(target.toFile());
        }
        return pb;
    }

    /**
     * Changes the shell's working directory by updating the value of the {@code cwd} field.
     *
     * @param pathString the absolute or relative path string to change to
     */
    void changeDirectory(String pathString) {
        Path target = cwd.resolve(pathString).toAbsolutePath().normalize();
        if (!Files.exists(target)) {
            System.err.println("cd: " + pathString + ": No such file or directory");
        } else if (!Files.isDirectory(target)) {
            System.err.println("cd: " + pathString + ": Not a directory");
        } else {
            cwd = target;
        }
    }

    /**
     * Prints background jobs and their status to standard out.
     * A job should only be removed from the list if it was shown in a previous call with the "Done" or "Terminated" status.
     * Job numbering does not restart unless the job list becomes empty.
     */
    void listJobs() {
        for (int i = 0; i < jobs.size(); i++) {
            Pipeline job = jobs.get(i);
            if (job == null) continue;
            if (job.completionShown) {
                jobs.set(i, null);
                continue;
            }
            System.out.printf("%-10s%s%n", "[" + (i + 1) + "]", job);
            if (job.isDone() || job.isTerminated()) job.completionShown = true;
        }
        if (jobs.stream().allMatch(job -> job == null)) jobs.clear();
    }

    /**
     * Kills the given jobs. The ids must be existing job ids.
     *
     * @param ids the ids of the jobs to kill
     */
    void killJobs(List<Integer> ids) {
        for (int id : ids) {
            if (id <= 0 || id > jobs.size() || jobs.get(id - 1) == null) {
                System.err.println("kill: " + id + ": no such job");
            } else {
                jobs.get(id - 1).killPipeline();
            }
        }
    }

    /**
     * Represents a pipeline: a list of commands that run as separate processes, with their inputs and outputs linked
     * by pipes.
     */
    public class Pipeline {
        private final String pipelineCommand;
        private final List<ProcessBuilder> pbs;
        private final boolean isBackground;
        private List<Process> processes = null;
        private boolean isDone = false;
        private boolean terminated = false;
        private boolean completionShown = false;

        /**
         * Splits {@code input} on {@code |}, builds a forked process for each subcommand via
         * {@link #buildForkedShell}, and wires the first process's stdin and (unless already
         * redirected to a file) the last process's stdout to this shell's own. A trailing
         * {@code &} marks the pipeline as a background job.
         *
         * @param input the string command for the pipeline
         */
        public Pipeline(String input) throws IllegalArgumentException {
            pipelineCommand = input.strip();
            isBackground = pipelineCommand.endsWith("&");
            String command = isBackground
                    ? pipelineCommand.substring(0, pipelineCommand.length() - 1).strip()
                    : pipelineCommand;
            if (command.isBlank()) throw new IllegalArgumentException("Missing command");
            pbs = new ArrayList<>();
            for (String subcommand : command.split("\\|", -1)) {
                if (subcommand.isBlank()) throw new IllegalArgumentException("Missing pipeline command");
                pbs.add(buildForkedShell(subcommand.strip()));
            }
            pbs.getFirst().redirectInput(ProcessBuilder.Redirect.INHERIT);
            ProcessBuilder last = pbs.getLast();
            if (last.redirectOutput() == ProcessBuilder.Redirect.PIPE) {
                last.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }
        }

        /**
         * Starts every process in the pipeline, connected by real OS pipes via
         * <a href="https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ProcessBuilder.html#startPipeline(java.util.List)">ProcessBuilder#startPipeline</a>,
         * and waits for them to finish unless this is a background job.
         *
         * @return the list of processes in the pipeline
         */
        public List<Process> executePipeline() throws Exception {
            processes = ProcessBuilder.startPipeline(pbs);
            if (!isBackground) waitFor();
            return processes;
        }

        /**
         * Waits for all processes in the pipeline to complete.
         * Does nothing if {@code executePipeline()} has not been called.
         */
        void waitFor() throws InterruptedException {
            if (processes == null) return;
            for (Process process : processes) process.waitFor();
            isDone = true;
        }

        /**
         * Terminates all processes in the pipeline. {@link Process#destroy()} only requests
         * termination asynchronously, so this also waits for each process to actually exit
         * before returning.
         */
        public void killPipeline() {
            terminated = true;
            if (processes == null) return;
            for (Process process : processes) process.destroy();
            boolean interrupted = false;
            for (Process process : processes) {
                while (true) {
                    try {
                        process.waitFor();
                        break;
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
            }
            isDone = true;
            if (interrupted) Thread.currentThread().interrupt();
        }

        /**
         * Checks if all processes in the pipeline have finished execution.
         *
         * @return false if any processes are still alive, otherwise true
         */
        public boolean isDone() {
            if (processes == null) return false;
            if (!isDone) isDone = processes.stream().noneMatch(Process::isAlive);
            return isDone;
        }

        /**
         * Checks if the pipeline command is a background job.
         *
         * @return true if pipeline runs in the background, false if runs in the foreground
         */
        public boolean isBackground() {
            return isBackground;
        }

        /**
         * Checks if the pipeline command was terminated with a call to {@code killPipeline}.
         *
         * @return true if {@code killPipeline} was called, otherwise false
         */
        public boolean isTerminated() {
            return terminated;
        }

        public String getPipelineCommand() {
            return pipelineCommand;
        }

        public List<ProcessBuilder> getPbs() {
            return pbs;
        }

        public List<Process> getProcesses() {
            return processes;
        }

        /** This job's {@code jobs} status: {@code "Terminated"}, {@code "Done"}, or {@code "Running"}. */
        public String getStatus() {
            return terminated ? "Terminated" : (isDone() ? "Done" : "Running");
        }

        @Override
        public String toString() {
            return String.format("%-20s %s", getStatus(), pipelineCommand);
        }
    }
}
