TRABAJO DE SISTEMAS Y SERVICIOS DISTRIBUIDOS - SINCRONIZACIÓN DE ARCHIVOS

Requisitos: Java 17 o superior.

COMPILAR
  javac *.java

ARRANCAR EL SERVIDOR (en el ordenador que almacenará la copia remota)
  java SyncServer 8080 carpeta_remota

ARRANCAR EL CLIENTE (en el ordenador del usuario)
  java MainClient DIRECCION_IP_SERVIDOR 8080 carpeta_local 5

MONITORIZAR CONTINUAMENTE LA CARPETA LOCAL (opcional)
  java MainClient DIRECCION_IP_SERVIDOR 8080 carpeta_local 5 --watch
  El cliente realiza una sincronización inicial y después vuelve a sincronizar
  automáticamente cuando detecta cambios locales. Para detenerlo, pulsar Ctrl+C.

El último argumento es el número de muestras para el algoritmo de Cristian.

PRUEBA LOCAL
  Terminal 1: java SyncServer 8080 carpeta_remota
  Terminal 2: java MainClient localhost 8080 carpeta_local 5

La carpeta remota se crea automáticamente si no existe. La carpeta local también.
Los archivos se transfieren en bloques de 64 KiB. Se calcula SHA-256 para detectar
cambios y se compara la fecha de modificación ajustada con el desfase horario.
El servidor no borra archivos cuando desaparecen de la carpeta local, de acuerdo
con la especificación del trabajo.

NOTA: Para ejecutar entre dos ordenadores, permitir conexiones TCP entrantes al
puerto elegido en el firewall del equipo servidor y utilizar su IP o nombre DNS.
