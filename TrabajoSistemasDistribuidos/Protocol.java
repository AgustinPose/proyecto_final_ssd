import java.io.Serializable;
import java.util.ArrayList;

/** Mensajes intercambiados entre cliente y servidor. Todos son serializables. */
public final class Protocol {
    private Protocol() { }

    public abstract static class Message implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    public static final class FileInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String path;
        public final long size;
        public final long modified;

        public FileInfo(String path, long size, long modified) {
            this.path = path;
            this.size = size;
            this.modified = modified;
        }
    }

    public static final class ListRequest extends Message { private static final long serialVersionUID = 1L; }
    public static final class ListResponse extends Message {
        private static final long serialVersionUID = 1L;
        public final ArrayList<FileInfo> files;
        public ListResponse(ArrayList<FileInfo> files) { this.files = files; }
    }
    public static final class HashRequest extends Message {
        private static final long serialVersionUID = 1L;
        public final String path;
        public HashRequest(String path) { this.path = path; }
    }
    public static final class HashResponse extends Message {
        private static final long serialVersionUID = 1L;
        public final String hash;
        public HashResponse(String hash) { this.hash = hash; }
    }
    public static final class UploadRequest extends Message {
        private static final long serialVersionUID = 1L;
        public final String path;
        public final long size;
        public final long modified;
        public UploadRequest(String path, long size, long modified) {
            this.path = path; this.size = size; this.modified = modified;
        }
    }
    public static final class DownloadRequest extends Message {
        private static final long serialVersionUID = 1L;
        public final String path;
        public DownloadRequest(String path) { this.path = path; }
    }
    public static final class DownloadInfo extends Message {
        private static final long serialVersionUID = 1L;
        public final long size;
        public final long modified;
        public DownloadInfo(long size, long modified) { this.size = size; this.modified = modified; }
    }
    public static final class FileChunk extends Message {
        private static final long serialVersionUID = 1L;
        public final byte[] data;
        public FileChunk(byte[] data) { this.data = data; }
    }
    public static final class TransferComplete extends Message { private static final long serialVersionUID = 1L; }
    public static final class Ack extends Message {
        private static final long serialVersionUID = 1L;
        public final String text;
        public Ack(String text) { this.text = text; }
    }
    public static final class ErrorMessage extends Message {
        private static final long serialVersionUID = 1L;
        public final String text;
        public ErrorMessage(String text) { this.text = text; }
    }
    public static final class TimeRequest extends Message { private static final long serialVersionUID = 1L; }
    public static final class TimeResponse extends Message {
        private static final long serialVersionUID = 1L;
        public final long time;
        public TimeResponse(long time) { this.time = time; }
    }
}
