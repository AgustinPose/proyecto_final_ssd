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
        try (java.io.InputStream input = Files.newInputStream(file)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                byte[] block = java.util.Arrays.copyOf(buffer, count);
                send(new Protocol.FileChunk(block));
            }
        }
        send(new Protocol.TransferComplete());
        Protocol.Ack ack = expect(Protocol.Ack.class);
        System.out.println(ack.text);
    }

    public void download(String relative, Path localFile) throws IOException, ClassNotFoundException {
        send(new Protocol.DownloadRequest(relative));
        Protocol.DownloadInfo info = expect(Protocol.DownloadInfo.class);
        Files.createDirectories(localFile.getParent());
        Path temporary = Files.createTempFile(localFile.getParent(), "download-", ".part");
        boolean completed = false;
        long received = 0;
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
}
