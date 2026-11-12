import java.nio.file.Path;
import java.nio.file.Paths;

/** Punto de entrada del cliente. Argumentos: host puerto carpetaLocal [muestrasCristian]. */
public class MainClient {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Uso: java MainClient <host> <puerto> <carpetaLocal> [muestrasCristian] [--watch]");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        Path local = Paths.get(args[2]);
        int samples = 5;
        boolean watch = false;
        for (int i = 3; i < args.length; i++) {
            if ("--watch".equals(args[i])) watch = true;
            else samples = Integer.parseInt(args[i]);
        }

        final int clockSamples = samples;
        Runnable synchronize = () -> {
            try (SyncClient client = new SyncClient(host, port)) {
                new SyncEngine(local, client, clockSamples).synchronize();
            } catch (Exception e) {
                System.err.println("No se pudo completar la sincronización: " + e.getMessage());
                e.printStackTrace();
            }
        };

        synchronize.run();
        if (watch) {
            try (SyncWatcher watcher = new SyncWatcher(local)) {
                watcher.watch(synchronize);
            } catch (Exception e) {
                System.err.println("Error monitorizando la carpeta: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
