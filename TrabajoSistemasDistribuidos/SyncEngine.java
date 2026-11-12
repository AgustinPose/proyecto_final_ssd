import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compara las carpetas y decide qué archivos se suben o se descargan. */
public class SyncEngine {
    private final Path localDirectory;
    private final SyncClient client;
    private final int clockSamples;

    public SyncEngine(Path localDirectory, SyncClient client, int clockSamples) throws IOException {
        this.localDirectory = localDirectory.toAbsolutePath().normalize();
        this.client = client;
        this.clockSamples = Math.max(1, clockSamples);
        FileUtils.createDirectoryIfNeeded(this.localDirectory);
    }

    public void synchronize() throws IOException, ClassNotFoundException {
        System.out.println("\n--- INICIO DE SINCRONIZACIÓN ---");
        long offset = client.calculateClockOffset(clockSamples);

        List<Protocol.FileInfo> localFiles = FileUtils.listFiles(localDirectory);
        List<Protocol.FileInfo> remoteFiles = client.getRemoteFiles();
        Map<String, Protocol.FileInfo> local = toMap(localFiles);
        Map<String, Protocol.FileInfo> remote = toMap(remoteFiles);
        Set<String> localNames = new HashSet<>(local.keySet());
        Set<String> remoteNames = new HashSet<>(remote.keySet());

        // Diferencias de conjuntos, igual que en la práctica DirOps con HashSet.
        Set<String> onlyLocal = new HashSet<>(localNames);
        onlyLocal.removeAll(remoteNames);
        Set<String> onlyRemote = new HashSet<>(remoteNames);
        onlyRemote.removeAll(localNames);
        Set<String> common = new HashSet<>(localNames);
        common.retainAll(remoteNames);

        for (String name : onlyLocal) upload(name, local.get(name), offset);
        for (String name : onlyRemote) download(name, offset);

        for (String name : common) {
            Protocol.FileInfo localInfo = local.get(name);
            Protocol.FileInfo remoteInfo = remote.get(name);
            String localHash = FileUtils.sha256(FileUtils.safeResolve(localDirectory, name));
            String remoteHash = client.getRemoteHash(name);
            if (localHash.equals(remoteHash)) {
                System.out.println("Sin cambios: " + name);
            } else {
                long adjustedLocalTime = localInfo.modified + offset;
                if (adjustedLocalTime > remoteInfo.modified) upload(name, localInfo, offset);
                else download(name, offset);
            }
        }
        System.out.println("--- SINCRONIZACIÓN FINALIZADA ---\n");
    }

    private void upload(String name, Protocol.FileInfo info, long offset) throws IOException, ClassNotFoundException {
        Path file = FileUtils.safeResolve(localDirectory, name);
        client.upload(file, name, info.modified + offset);
    }

    private void download(String name, long offset) throws IOException, ClassNotFoundException {
        Path file = FileUtils.safeResolve(localDirectory, name);
        client.download(name, file);
        // La descarga fija primero la fecha del servidor; la convertimos al reloj local.
        FileUtils.setModified(file, Files.getLastModifiedTime(file).toMillis() - offset);
    }

    private static Map<String, Protocol.FileInfo> toMap(List<Protocol.FileInfo> files) {
        Map<String, Protocol.FileInfo> result = new HashMap<>();
        for (Protocol.FileInfo file : files) result.put(file.path, file);
        return result;
    }
}
