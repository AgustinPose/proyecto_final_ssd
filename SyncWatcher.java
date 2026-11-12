import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

/** Monitoriza recursivamente la carpeta local y lanza una sincronización tras cada cambio. */
public final class SyncWatcher implements AutoCloseable {
    private static final long DEBOUNCE_MILLIS = 500;

    private final Path root;
    private final WatchService watchService;
    private final Map<WatchKey, Path> directories = new HashMap<>();

    public SyncWatcher(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
        this.watchService = this.root.getFileSystem().newWatchService();
        registerTree(this.root);
    }

    /** Bloquea el proceso y vuelve a sincronizar cuando cambia un archivo o directorio local. */
    public void watch(Runnable synchronize) throws InterruptedException, IOException {
        System.out.println("Monitorizando " + root + ". Pulsa Ctrl+C para detener el cliente.");
        while (true) {
            WatchKey key = watchService.take();
            Path directory = directories.get(key);
            boolean changed = false;

            if (directory != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        changed = true;
                        continue;
                    }

                    Path name = (Path) event.context();
                    Path changedPath = directory.resolve(name);
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changedPath)) {
                        registerTree(changedPath);
                    }
                    changed = true;
                }
            }

            if (!key.reset()) directories.remove(key);
            if (changed) {
                Thread.sleep(DEBOUNCE_MILLIS);
                System.out.println("Cambio detectado. Sincronizando...");
                synchronize.run();
            }
        }
    }

    private void registerTree(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                WatchKey key = directory.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                directories.put(key, directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Override
    public void close() throws IOException {
        watchService.close();
    }
}
