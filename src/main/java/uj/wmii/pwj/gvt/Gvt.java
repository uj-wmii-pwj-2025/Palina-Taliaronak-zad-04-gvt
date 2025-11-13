package uj.wmii.pwj.gvt;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class Gvt {

    private final ExitHandler exitHandler;
    private final Path gvtDir;
    private final Path versionsDir;
    private final Path currentVersionFile;
    private final Path versionInfoDir;

    public Gvt(ExitHandler exitHandler) {
        this.exitHandler = exitHandler;
        this.gvtDir = Paths.get(".gvt");
        this.versionsDir = gvtDir.resolve("versions");
        this.currentVersionFile = gvtDir.resolve("current_version");
        this.versionInfoDir = gvtDir.resolve("version_info");
    }

    public static void main(String... args) {
        Gvt gvt = new Gvt(new ExitHandler());
        gvt.mainInternal(args);
    }

    public void mainInternal(String... args) {
        if (args.length == 0) {
            exitHandler.exit(1, "Please specify command.");
            return;
        }

        String command = args[0];
        String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);

        try {
            switch (command) {
                case "init":
                    init(commandArgs);
                    break;
                case "add":
                    add(commandArgs);
                    break;
                case "detach":
                    detach(commandArgs);
                    break;
                case "commit":
                    commit(commandArgs);
                    break;
                case "checkout":
                    checkout(commandArgs);
                    break;
                case "history":
                    history(commandArgs);
                    break;
                case "version":
                    version(commandArgs);
                    break;
                default:
                    exitHandler.exit(1, "Unknown command " + command + ".");
            }
        } catch (Exception e) {
            handleUnderlyingSystemProblem(e);
        }
    }

    private void init(String[] args) {
        if (Files.exists(gvtDir)) {
            exitHandler.exit(10, "Current directory is already initialized.");
            return;
        }

        try {
            Files.createDirectories(versionsDir.resolve("0"));
            setCurrentVersion(0);
            saveVersionInfo(0, "GVT initialized.", new HashSet<>());
            exitHandler.exit(0, "Current directory initialized successfully.");
        } catch (IOException e) {
            handleUnderlyingSystemProblem(e);
        }
    }

    private void add(String[] args) {
        if (!isInitialized()) return;

        if (args.length == 0) {
            exitHandler.exit(20, "Please specify file to add.");
            return;
        }

        String fileName = args[0];
        String userMessage = extractUserMessage(args);

        try {
            Path file = Paths.get(fileName);
            if (!Files.exists(file)) {
                exitHandler.exit(21, "File not found. File: " + fileName);
                return;
            }

            int currentVersion = getCurrentVersion();
            VersionInfo currentInfo = loadVersionInfo(currentVersion);

            if (currentInfo.containsFile(fileName)) {
                exitHandler.exit(0, "File already added. File: " + fileName);
                return;
            }

            int newVersion = currentVersion + 1;
            String message = userMessage != null ? userMessage : "File added successfully. File: " + fileName;

            copyVersionFiles(currentVersion, newVersion);

            Path targetFile = versionsDir.resolve(String.valueOf(newVersion)).resolve(fileName);
            Files.createDirectories(targetFile.getParent());
            Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);

            Set<String> newFiles = new HashSet<>(currentInfo.getFiles());
            newFiles.add(fileName);
            saveVersionInfo(newVersion, message, newFiles);
            setCurrentVersion(newVersion);

            exitHandler.exit(0, "File added successfully. File: " + fileName);
        } catch (IOException e) {
            handleFileOperationError(e, "File cannot be added. See ERR for details. File: " + fileName, 22);
        }
    }

    private void detach(String[] args) {
        if (!isInitialized()) return;

        if (args.length == 0) {
            exitHandler.exit(30, "Please specify file to detach.");
            return;
        }

        String fileName = args[0];
        String userMessage = extractUserMessage(args);

        try {
            int currentVersion = getCurrentVersion();
            VersionInfo currentInfo = loadVersionInfo(currentVersion);

            if (!currentInfo.containsFile(fileName)) {
                exitHandler.exit(0, "File is not added to gvt. File: " + fileName);
                return;
            }

            int newVersion = currentVersion + 1;
            String message = userMessage != null ? userMessage : "File detached successfully. File: " + fileName;

            copyVersionFiles(currentVersion, newVersion, fileName);

            Set<String> newFiles = new HashSet<>(currentInfo.getFiles());
            newFiles.remove(fileName);
            saveVersionInfo(newVersion, message, newFiles);
            setCurrentVersion(newVersion);

            exitHandler.exit(0, "File detached successfully. File: " + fileName);
        } catch (IOException e) {
            handleFileOperationError(e, "File cannot be detached, see ERR for details. File: " + fileName, 31);
        }
    }

    private void commit(String[] args) {
        if (!isInitialized()) return;

        if (args.length == 0) {
            exitHandler.exit(50, "Please specify file to commit.");
            return;
        }

        String fileName = args[0];
        String userMessage = extractUserMessage(args);

        try {
            int currentVersion = getCurrentVersion();
            VersionInfo currentInfo = loadVersionInfo(currentVersion);

            if (!currentInfo.containsFile(fileName)) {
                exitHandler.exit(0, "File is not added to gvt. File: " + fileName);
                return;
            }

            Path file = Paths.get(fileName);
            if (!Files.exists(file)) {
                exitHandler.exit(51, "File not found. File: " + fileName);
                return;
            }

            int newVersion = currentVersion + 1;
            String message = userMessage != null ? userMessage : "File committed successfully. File: " + fileName;

            copyVersionFiles(currentVersion, newVersion);

            Path targetFile = versionsDir.resolve(String.valueOf(newVersion)).resolve(fileName);
            Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);

            saveVersionInfo(newVersion, message, currentInfo.getFiles());
            setCurrentVersion(newVersion);

            exitHandler.exit(0, "File committed successfully. File: " + fileName);
        } catch (IOException e) {
            handleFileOperationError(e, "File cannot be committed, see ERR for details. File: " + fileName, 52);
        }
    }

    private void checkout(String[] args) {
        if (!isInitialized()) return;

        if (args.length == 0) {
            exitHandler.exit(60, "Invalid version number: null");
            return;
        }

        try {
            int version = Integer.parseInt(args[0]);
            if (!versionExists(version)) {
                exitHandler.exit(60, "Invalid version number: " + version);
                return;
            }

            VersionInfo versionInfo = loadVersionInfo(version);

            for (String fileName : versionInfo.getFiles()) {
                Path sourceFile = versionsDir.resolve(String.valueOf(version)).resolve(fileName);
                Path targetFile = Paths.get(fileName);
                Files.createDirectories(targetFile.getParent());
                if (Files.exists(sourceFile)) {
                    Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            exitHandler.exit(0, "Checkout successful for version: " + version);
        } catch (NumberFormatException e) {
            exitHandler.exit(60, "Invalid version number: " + args[0]);
        } catch (IOException e) {
            handleUnderlyingSystemProblem(e);
        }
    }

    private void history(String[] args) {
        if (!isInitialized()) return;

        try {
            List<VersionInfo> allVersions = getAllVersions();
            List<VersionInfo> versionsToShow = allVersions;

            if (args.length >= 2 && "-last".equals(args[0])) {
                try {
                    int lastN = Integer.parseInt(args[1]);
                    int startIndex = Math.max(0, allVersions.size() - lastN);
                    versionsToShow = allVersions.subList(startIndex, allVersions.size());
                } catch (NumberFormatException e) {
                }
            }

            Collections.reverse(versionsToShow);

            StringBuilder sb = new StringBuilder();
            for (VersionInfo version : versionsToShow) {
                String firstLine = version.getMessage().split("\n")[0];
                sb.append(version.getNumber()).append(": ").append(firstLine).append("\n");
            }

            if (sb.length() > 0) {
                sb.setLength(sb.length() - 1);
            }

            exitHandler.exit(0, sb.toString());
        } catch (IOException e) {
            handleUnderlyingSystemProblem(e);
        }
    }

    private void version(String[] args) {
        if (!isInitialized()) return;

        try {
            int versionNumber;
            if (args.length == 0) {
                versionNumber = getCurrentVersion();
            } else {
                try {
                    versionNumber = Integer.parseInt(args[0]);
                    if (!versionExists(versionNumber)) {
                        exitHandler.exit(60, "Invalid version number: " + versionNumber + ".");
                        return;
                    }
                } catch (NumberFormatException e) {
                    exitHandler.exit(60, "Invalid version number: " + args[0] + ".");
                    return;
                }
            }

            VersionInfo versionInfo = loadVersionInfo(versionNumber);
            exitHandler.exit(0, "Version: " + versionNumber + "\n" + versionInfo.getMessage());
        } catch (IOException e) {
            handleUnderlyingSystemProblem(e);
        }
    }

    private boolean isInitialized() {
        if (!Files.exists(gvtDir)) {
            exitHandler.exit(-2, "Current directory is not initialized. Please use init command to initialize.");
            return false;
        }
        return true;
    }

    private String extractUserMessage(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("-m".equals(args[i]) && i + 1 < args.length) {
                return args[i + 1].replace("\"", "");
            }
        }
        return null;
    }

    private void handleUnderlyingSystemProblem(Exception e) {
        e.printStackTrace(System.err);
        exitHandler.exit(-3, "Underlying system problem. See ERR for details.");
    }

    private void handleFileOperationError(IOException e, String message, int errorCode) {
        e.printStackTrace(System.err);
        exitHandler.exit(errorCode, message);
    }

    private int getCurrentVersion() throws IOException {
        if (Files.exists(currentVersionFile)) {
            return Integer.parseInt(Files.readString(currentVersionFile).trim());
        }
        return 0;
    }

    private void setCurrentVersion(int version) throws IOException {
        Files.writeString(currentVersionFile, String.valueOf(version),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private boolean versionExists(int version) {
        return Files.exists(versionsDir.resolve(String.valueOf(version)));
    }

    private VersionInfo loadVersionInfo(int version) throws IOException {
        Path infoFile = versionInfoDir.resolve(version + ".info");
        if (Files.exists(infoFile)) {
            try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(infoFile))) {
                return (VersionInfo) ois.readObject();
            } catch (ClassNotFoundException e) {
            }
        }
        if (version == 0) {
            return new VersionInfo(0, "GVT initialized.", new HashSet<>());
        }
        return new VersionInfo(version, "", new HashSet<>());
    }

    private void saveVersionInfo(int version, String message, Set<String> files) throws IOException {
        Files.createDirectories(versionInfoDir);
        Path infoFile = versionInfoDir.resolve(version + ".info");
        try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(infoFile))) {
            oos.writeObject(new VersionInfo(version, message, files));
        }
    }

    private void copyVersionFiles(int fromVersion, int toVersion) throws IOException {
        copyVersionFiles(fromVersion, toVersion, null);
    }

    private void copyVersionFiles(int fromVersion, int toVersion, String excludeFile) throws IOException {
        Path sourceDir = versionsDir.resolve(String.valueOf(fromVersion));
        Path targetDir = versionsDir.resolve(String.valueOf(toVersion));

        if (Files.exists(sourceDir)) {
            Files.createDirectories(targetDir);
            try (var paths = Files.walk(sourceDir)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> excludeFile == null || !path.getFileName().toString().equals(excludeFile))
                        .forEach(source -> {
                            try {
                                Path relative = sourceDir.relativize(source);
                                Path target = targetDir.resolve(relative);
                                Files.createDirectories(target.getParent());
                                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        }
    }

    private List<VersionInfo> getAllVersions() throws IOException {
        List<VersionInfo> versions = new ArrayList<>();
        if (Files.exists(versionInfoDir)) {
            try (var files = Files.list(versionInfoDir)) {
                List<Path> infoFiles = files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".info"))
                        .collect(Collectors.toList());

                for (Path infoFile : infoFiles) {
                    String fileName = infoFile.getFileName().toString();
                    int versionNumber = Integer.parseInt(fileName.substring(0, fileName.length() - 5));
                    versions.add(loadVersionInfo(versionNumber));
                }
            }
        }
        versions.sort(Comparator.comparingInt(VersionInfo::getNumber));
        return versions;
    }

    private static class VersionInfo implements Serializable {
        private final int number;
        private final String message;
        private final Set<String> files;

        public VersionInfo(int number, String message, Set<String> files) {
            this.number = number;
            this.message = message;
            this.files = new HashSet<>(files);
        }

        public int getNumber() { return number; }
        public String getMessage() { return message; }
        public Set<String> getFiles() { return files; }

        public boolean containsFile(String fileName) {
            return files.contains(fileName);
        }
    }
}
