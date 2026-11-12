import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Cliente TCP. Mantiene una conexión y ofrece operaciones del protocolo. */
public class SyncClient implements AutoCloseable {
    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;

    public SyncClient(String host, int port) throws IOException {
        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
        System.out.println("Conectado a " + host + ":" + port);
    }

    public java.util.List<Protocol.FileInfo> getRemoteFiles() throws IOException, ClassNotFoundException {
        send(new Protocol.ListRequest());
        return expect(Protocol.ListResponse.class).files;
    }

    public String getRemoteHash(String path) throws IOException, ClassNotFoundException {
        send(new Protocol.HashRequest(path));
        return expect(Protocol.HashResponse.class).hash;
    }

    public void upload(Path file, String relative, long remoteModified) throws IOException, ClassNotFoundException {
        long size = Files.size(file);
        send(new Protocol.UploadRequest(relative, size, remoteModified));
        byte[] buffer = new byte[FileUtils.BUFFER_SIZE];
        ProgressBar progress = new ProgressBar("Subiendo " + relative, size);
        try (java.io.InputStream input = Files.newInputStream(file)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                byte[] block = java.util.Arrays.copyOf(buffer, count);
                send(new Protocol.FileChunk(block));
                progress.advance(count);
            }
        }
        send(new Protocol.TransferComplete());
        Protocol.Ack ack = expect(Protocol.Ack.class);
        progress.finish();
        System.out.println(ack.text);
    }

    public void download(String relative, Path localFile) throws IOException, ClassNotFoundException {
        send(new Protocol.DownloadRequest(relative));
        Protocol.DownloadInfo info = expect(Protocol.DownloadInfo.class);
        Files.createDirectories(localFile.getParent());
        Path temporary = Files.createTempFile(localFile.getParent(), "download-", ".part");
        boolean completed = false;
        long received = 0;
        ProgressBar progress = new ProgressBar("Descargando " + relative, info.size);
        try {
            while (true) {
                Object item = in.readObject();
                if (item instanceof Protocol.TransferComplete) break;
                if (item instanceof Protocol.ErrorMessage) throw new IOException(((Protocol.ErrorMessage) item).text);
                if (!(item instanceof Protocol.FileChunk)) throw new IOException("Bloque recibido no válido");
                byte[] data = ((Protocol.FileChunk) item).data;
                received += data.length;
                if (received > info.size) throw new IOException("Tamaño recibido superior al esperado");
                Files.write(temporary, data, StandardOpenOption.APPEND);
                progress.advance(data.length);
            }
            if (received != info.size) throw new IOException("Descarga incompleta: " + received + "/" + info.size);
            try {
                Files.move(temporary, localFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, localFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            FileUtils.setModified(localFile, info.modified);
            completed = true;
            progress.finish();
            System.out.println("Archivo descargado: " + relative);
        } finally {
            if (!completed) Files.deleteIfExists(temporary);
        }
    }

    /** Algoritmo de Cristian: elige la muestra con menor RTT. Devuelve desfase servidor-cliente. */
    public long calculateClockOffset(int samples) throws IOException, ClassNotFoundException {
        long bestRtt = Long.MAX_VALUE;
        long bestOffset = 0;
        for (int i = 0; i < samples; i++) {
            long t0 = System.currentTimeMillis();
            send(new Protocol.TimeRequest());
            long serverTime = expect(Protocol.TimeResponse.class).time;
            long t1 = System.currentTimeMillis();
            long rtt = t1 - t0;
            long estimatedServerAtT1 = serverTime + rtt / 2;
            long offset = estimatedServerAtT1 - t1;
            if (rtt < bestRtt) { bestRtt = rtt; bestOffset = offset; }
        }
        System.out.println("Desfase estimado servidor-cliente: " + bestOffset + " ms (RTT mínimo " + bestRtt + " ms)");
        return bestOffset;
    }

    private void send(Protocol.Message message) throws IOException {
        out.writeObject(message);
        out.flush();
        out.reset();
    }

    private <T> T expect(Class<T> type) throws IOException, ClassNotFoundException {
        Object response = in.readObject();
        if (response instanceof Protocol.ErrorMessage)
            throw new IOException(((Protocol.ErrorMessage) response).text);
        if (!type.isInstance(response)) throw new IOException("Respuesta inesperada: " + response.getClass().getName());
        return type.cast(response);
    }

    @Override
    public void close() throws IOException {
        try { in.close(); } finally {
            try { out.close(); } finally { socket.close(); }
        }
    }

    /** Muestra el progreso sin inundar la terminal con una línea por bloque. */
    private static final class ProgressBar {
        private static final int WIDTH = 30;
        private static final long RENDER_INTERVAL_NANOS = 100_000_000L;

        private final String label;
        private final long total;
        private long transferred;
        private long lastRender;

        private ProgressBar(String label, long total) {
            this.label = label;
            this.total = total;
        }

        private void advance(long bytes) {
            transferred += bytes;
            long now = System.nanoTime();
            if (now - lastRender >= RENDER_INTERVAL_NANOS || transferred == total) {
                render();
                lastRender = now;
            }
        }

        private void finish() {
            if (transferred != total || total == 0) render();
            System.out.println();
        }

        private void render() {
            int percent = total == 0 ? 100 : (int) Math.min(100, transferred * 100 / total);
            int filled = percent * WIDTH / 100;
            String bar = "#".repeat(filled) + "-".repeat(WIDTH - filled);
            System.out.printf("\r%s: [%s] %3d%% (%s / %s)", label, bar, percent,
                    formatBytes(transferred), formatBytes(total));
            System.out.flush();
        }

        private static String formatBytes(long bytes) {
            String[] units = {"B", "KB", "MB", "GB", "TB"};
            double value = bytes;
            int unit = 0;
            while (value >= 1024 && unit < units.length - 1) {
                value /= 1024;
                unit++;
            }
            return unit == 0 ? bytes + " " + units[unit] : String.format("%.2f %s", value, units[unit]);
        }
    }
}
