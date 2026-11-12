# Memoria del trabajo: aplicación de almacenamiento remoto y sincronización de archivos

> Documento base para completar con los nombres, correos, capturas y pruebas realizadas por el grupo. No entregues resultados que no hayas reproducido en tu propio entorno.

## 1. Objetivo

Se ha desarrollado una aplicación cliente-servidor en Java para mantener una copia remota de los archivos de una carpeta local. El cliente compara el contenido de ambas carpetas y sube o descarga las versiones necesarias. De acuerdo con la especificación, la aplicación no elimina archivos del servidor cuando desaparecen de la carpeta local.

## 2. Arquitectura

La aplicación utiliza TCP Sockets. El servidor permanece escuchando en un puerto y crea una tarea `ServerTask` para atender cada conexión mediante un `ExecutorService`. El cliente mantiene una conexión y envía objetos de mensaje serializables definidos en `Protocol`.

Las transferencias se realizan en bloques de 64 KiB para no cargar archivos completos en memoria. Los mensajes de control contienen la operación y sus metadatos; los bloques transportan los bytes del archivo.

## 3. Clases desarrolladas

### Comunicación
- `Protocol`: define los mensajes serializables de petición y respuesta, así como `FileInfo`.
- `SyncClient`: abre el socket, intercambia mensajes, sube y descarga bloques y consulta la hora del servidor.
- `ServerTask`: implementa `Runnable` y procesa las peticiones de una conexión.
- `SyncServer`: abre el `ServerSocket`, acepta clientes y delega su atención en un pool de hilos.

### Gestión de archivos y sincronización
- `FileUtils`: recorre directorios, crea carpetas, resuelve rutas seguras y calcula hashes SHA-256 leyendo por bloques.
- `SyncEngine`: obtiene los listados, calcula los conjuntos de archivos locales, remotos y comunes, compara hashes y decide qué versión conservar.
- `MainClient`: interpreta los parámetros y arranca la sincronización.
- `SyncServer` también dispone de `main` para arrancar el servidor.

## 4. Protocolo de comunicación

1. `ListRequest` / `ListResponse`: obtener la lista remota.
2. `HashRequest` / `HashResponse`: comparar el contenido de un archivo.
3. `UploadRequest`, seguido de `FileChunk` y `TransferComplete`: subir un archivo.
4. `DownloadRequest`, seguido de `DownloadInfo`, `FileChunk` y `TransferComplete`: descargar un archivo.
5. `TimeRequest` / `TimeResponse`: estimar el desfase de reloj mediante el algoritmo de Cristian.
6. `Ack` y `ErrorMessage`: confirmar operaciones o comunicar errores.

## 5. Algoritmo de sincronización

Primero se consulta la hora del servidor varias veces. Se conserva la muestra con menor RTT y se estima el desfase servidor-cliente. Después se obtiene el listado local y remoto.

Se calculan tres conjuntos:
- Solo local: se sube cada archivo.
- Solo remoto: se descarga cada archivo.
- Comunes: se calcula SHA-256 en ambos extremos. Si coincide, no se transfiere. Si difiere, se comparan las fechas de modificación, ajustando la fecha local con el desfase estimado. Se sube la versión local si es más reciente; en caso contrario, se descarga la remota.

## 6. Transferencia de archivos

Los archivos se transfieren en bloques de 64 KiB. El receptor escribe cada bloque en un archivo temporal. Solo cuando recibe `TransferComplete` y verifica que el número de bytes coincide con el tamaño anunciado, mueve el temporal a su destino. Así se evita dejar como definitivo un archivo parcialmente recibido.

## 7. Instrucciones de ejecución

Requisito: Java 17 o superior.

Compilar:

```bash
javac *.java
```

Arrancar servidor:

```bash
java SyncServer 8080 carpeta_remota
```

Arrancar cliente:

```bash
java MainClient IP_DEL_SERVIDOR 8080 carpeta_local 5
```

Para probar en el mismo ordenador, sustituir la IP por `localhost`. Para probar desde otro ordenador, abrir el puerto TCP seleccionado en el firewall del servidor y utilizar su IP o nombre DNS.

## 8. Pruebas

Registrar aquí las pruebas repetidas por el grupo, incluyendo fecha, sistema operativo, tamaño de archivos y resultado:

| Prueba | Resultado que se debe comprobar |
|---|---|
| Archivo que solo existe localmente | Aparece en la carpeta remota |
| Archivo que solo existe remotamente | Se descarga a la carpeta local |
| Archivos idénticos | No se vuelve a transferir |
| Archivo local modificado | Se sube si su fecha ajustada es posterior |
| Archivo remoto modificado | Se descarga si su fecha es posterior |
| Archivo binario | Los bytes recibidos coinciden con los originales |
| Archivo grande (más de 500 MB) | Se transfiere sin cargarlo entero en memoria |
| Dos clientes simultáneos | El servidor acepta ambas conexiones |
| Desfase horario | Se registra el desfase estimado y se comprueba el algoritmo de Cristian |

En una prueba local automatizada de desarrollo se verificó la subida de un archivo de texto, la subida de un archivo binario de 1 MiB, la descarga de un archivo remoto y una segunda ejecución sin transferencias porque los hashes coincidían. Esta prueba local no sustituye las pruebas entre dos ordenadores ni la prueba de archivos de más de 500 MB.

## 9. Limitaciones y trabajo adicional

- La monitorización continua de la carpeta mediante `WatchService` no está implementada; el cliente sincroniza al ejecutarse.
- Se sincronizan archivos regulares y se crean las carpetas padre necesarias. No se preservan directorios vacíos.
- El algoritmo de Cristian está integrado, pero debe probarse con retardo de red (`tc netem`) para documentar su comportamiento.
- La resolución de conflictos se basa en fecha de modificación; si ambas versiones tienen la misma fecha ajustada y hashes diferentes, se descarga la copia remota.

## 10. Conclusiones

La aplicación integra sockets TCP, serialización de mensajes, concurrencia, operaciones NIO, hashes y transferencia por bloques. La arquitectura separa la gestión de archivos, la comunicación y las decisiones de sincronización, facilitando su prueba y ampliación.
