import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Servidor TCP concurrente: acepta clientes y delega cada conexión a ServerTask. */
public class SyncServer {
    private final int port;
    private final Path remoteDirectory;

    public SyncServer(int port, Path remoteDirectory) throws IOException {
        this.port = port;
        this.remoteDirectory = remoteDirectory.toAbsolutePath().normalize();
        FileUtils.createDirectoryIfNeeded(this.remoteDirectory);
    }

    public void runServer() throws IOException {
        ExecutorService executor = Executors.newCachedThreadPool();
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Servidor escuchando en el puerto " + port);
            System.out.println("Carpeta remota: " + remoteDirectory);
            while (true) {
                Socket socket = server.accept();
                executor.execute(new ServerTask(socket, remoteDirectory));
            }
        } finally {
            executor.shutdown();
        }
    }

    public static void main(String[] args) throws IOException {
        int port = args.length >= 1 ? Integer.parseInt(args[0]) : 8080;
        Path directory = Paths.get(args.length >= 2 ? args[1] : "servidor_files");
        new SyncServer(port, directory).runServer();
    }
}
