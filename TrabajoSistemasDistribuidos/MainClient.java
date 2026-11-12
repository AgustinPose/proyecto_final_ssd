import java.nio.file.Path;
import java.nio.file.Paths;

/** Punto de entrada del cliente. Argumentos: host puerto carpetaLocal [muestrasCristian]. */
public class MainClient {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Uso: java MainClient <host> <puerto> <carpetaLocal> [muestrasCristian]");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        Path local = Paths.get(args[2]);
        int samples = args.length >= 4 ? Integer.parseInt(args[3]) : 5;
        try (SyncClient client = new SyncClient(host, port)) {
            new SyncEngine(local, client, samples).synchronize();
        } catch (Exception e) {
            System.err.println("No se pudo completar la sincronización: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
