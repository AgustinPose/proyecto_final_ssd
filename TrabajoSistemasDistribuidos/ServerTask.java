import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Atiende una conexión de cliente; sigue el patrón Runnable de EchoTask. */
public class ServerTask implements Runnable {
    private final Socket socket;
    private final Path remoteDirectory;

    public ServerTask(Socket socket, Path remoteDirectory) {
        this.socket = socket;
        this.remoteDirectory = remoteDirectory;
    }

    @Override
    public void run() {
        try (Socket client = socket;
             ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(client.getInputStream())) {
            out.flush();
            System.out.println("Cliente conectado: " + client.getRemoteSocketAddress());
            while (true) {
                Object object = in.readObject();
                if (!(object instanceof Protocol.Message)) throw new IOException("Mensaje no válido");
                Protocol.Message message = (Protocol.Message) object;
                if (message instanceof Protocol.ListRequest) sendList(out);
                else if (message instanceof Protocol.HashRequest) sendHash((Protocol.HashRequest) message, out);
                else if (message instanceof Protocol.UploadRequest) receiveFile((Protocol.UploadRequest) message, in, out);
                else if (message instanceof Protocol.DownloadRequest) sendFile((Protocol.DownloadRequest) message, out);
                else if (message instanceof Protocol.TimeRequest) send(out, new Protocol.TimeResponse(System.currentTimeMillis()));
                else send(out, new Protocol.ErrorMessage("Petición desconocida"));
            }
        } catch (java.io.EOFException e) {
            System.out.println("Cliente desconectado.");
        } catch (Exception e) {
            System.err.println("Error atendiendo cliente: " + e.getMessage());
        }
    }

    private void sendList(ObjectOutputStream out) throws IOException {
        send(out, new Protocol.ListResponse(new java.util.ArrayList<>(FileUtils.listFiles(remoteDirectory))));
    }

    private void sendHash(Protocol.HashRequest request, ObjectOutputStream out) throws IOException {
        Path file = FileUtils.safeResolve(remoteDirectory, request.path);
        if (!Files.isRegularFile(file)) throw new IOException("No existe el archivo remoto: " + request.path);
        send(out, new Protocol.HashResponse(FileUtils.sha256(file)));
    }

    private void receiveFile(Protocol.UploadRequest request, ObjectInputStream in,
                             ObjectOutputStream out) throws IOException, ClassNotFoundException {
        Path target = FileUtils.safeResolve(remoteDirectory, request.path);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "upload-", ".part");
        boolean completed = false;
        long received = 0;
        try {
            while (true) {
                Object item = in.readObject();
                if (item instanceof Protocol.TransferComplete) break;
                if (!(item instanceof Protocol.FileChunk)) throw new IOException("Bloque de archivo no válido");
                byte[] data = ((Protocol.FileChunk) item).data;
                received += data.length;
                if (received > request.size) throw new IOException("Se han recibido más bytes de los esperados");
                Files.write(temporary, data, StandardOpenOption.APPEND);
            }
            if (received != request.size) throw new IOException("Transferencia incompleta: " + received + "/" + request.size);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            FileUtils.setModified(target, request.modified);
            completed = true;
            send(out, new Protocol.Ack("Archivo recibido: " + request.path));
        } finally {
            if (!completed) Files.deleteIfExists(temporary);
        }
    }

    private void sendFile(Protocol.DownloadRequest request, ObjectOutputStream out)
            throws IOException {
        Path file = FileUtils.safeResolve(remoteDirectory, request.path);
        if (!Files.isRegularFile(file)) {
            send(out, new Protocol.ErrorMessage("No existe el archivo: " + request.path));
            return;
        }
        send(out, new Protocol.DownloadInfo(Files.size(file), Files.getLastModifiedTime(file).toMillis()));
        byte[] buffer = new byte[FileUtils.BUFFER_SIZE];
        try (java.io.InputStream input = Files.newInputStream(file)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                byte[] block = java.util.Arrays.copyOf(buffer, count);
                send(out, new Protocol.FileChunk(block));
            }
        }
        send(out, new Protocol.TransferComplete());
    }

    private static void send(ObjectOutputStream out, Protocol.Message message) throws IOException {
        out.writeObject(message);
        out.flush();
        out.reset(); // Evita que ObjectOutputStream retenga referencias a todos los bloques.
    }
}
