import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Operaciones locales con ficheros y directorios, basadas en java.nio. */
public final class FileUtils {
    public static final int BUFFER_SIZE = 64 * 1024;
    private FileUtils() { }

    public static void createDirectoryIfNeeded(Path directory) throws IOException {
        Files.createDirectories(directory);
    }

    /** Devuelve los archivos regulares de un árbol, usando rutas relativas con '/'. */
    public static List<Protocol.FileInfo> listFiles(Path root) throws IOException {
        List<Protocol.FileInfo> result = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                String relative = root.relativize(path).toString().replace('\\', '/');
                try {
                    result.add(new Protocol.FileInfo(relative, Files.size(path),
                            Files.getLastModifiedTime(path).toMillis()));
                } catch (IOException e) {
                    throw new FileOperationException(e);
                }
            });
        } catch (FileOperationException e) {
            throw e.ioException;
        }
        return result;
    }

    /** Hash SHA-256 calculado por bloques, sin cargar el fichero completo en memoria. */
    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            try (InputStream in = Files.newInputStream(file)) {
                int read;
                while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }

    /** Resuelve una ruta relativa y evita salir de la carpeta raíz (path traversal). */
    public static Path safeResolve(Path root, String relative) throws IOException {
        Path name = Paths.get(relative);
        if (name.isAbsolute()) throw new IOException("No se permiten rutas absolutas: " + relative);
        Path resolved = root.resolve(name).normalize();
        if (!resolved.startsWith(root.normalize())) throw new IOException("Ruta fuera de la carpeta: " + relative);
        return resolved;
    }

    public static void setModified(Path file, long millis) throws IOException {
        Files.setLastModifiedTime(file, FileTime.fromMillis(millis));
    }

    /** Auxiliar para generar conjuntos de nombres, como en la práctica DirOps. */
    public static Set<String> names(List<Protocol.FileInfo> files) {
        Set<String> result = new HashSet<>();
        for (Protocol.FileInfo info : files) result.add(info.path);
        return result;
    }

    private static final class FileOperationException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final IOException ioException;
        private FileOperationException(IOException cause) { super(cause); this.ioException = cause; }
    }
}
