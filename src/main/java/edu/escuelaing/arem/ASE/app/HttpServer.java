package edu.escuelaing.arem.ASE.app;

import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Clase de un servidor http para manejar peticiones, en este caso para un pagina de canciones
 */
public class HttpServer {
    private static final Map<String, String> CONTENT_TYPES = new HashMap<>();
    private static final List<Song> songs = new ArrayList<>();
    private static boolean running = true;

    static {

        CONTENT_TYPES.put("html", "text/html");
        CONTENT_TYPES.put("js", "text/javascript");
        CONTENT_TYPES.put("css", "text/css");
        CONTENT_TYPES.put("jpg", "image/jpeg");
        CONTENT_TYPES.put("png", "image/png");
        CONTENT_TYPES.put("gif", "image/gif");
        CONTENT_TYPES.put("json", "application/json");

        // Canciones de ejemplo
        songs.add(new Song("Bohemian Rhapsody", "Queen"));
        songs.add(new Song("Imagine", "John Lennon"));
        songs.add(new Song("Hotel California", "Eagles"));
    }

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = startServer(35000);
        while (running) {
            handleClientConnection(serverSocket);
        }
        serverSocket.close();
    }

    private static ServerSocket startServer(int port) throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Servidor de música iniciado en http://localhost:" + port);
        return serverSocket;
    }

    private static void handleClientConnection(ServerSocket serverSocket) {
        try (Socket clientSocket = serverSocket.accept();
             BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             OutputStream outputStream = clientSocket.getOutputStream()) {

            Request request = parseRequest(in);
            if (request != null) {
                processRequest(outputStream, request);
            }
        } catch (IOException e) {
            System.err.println("Error en conexión con cliente: " + e.getMessage());
        }
    }

    static Request parseRequest(BufferedReader in) throws IOException {
        String inputLine;
        String method = "";
        String path = "";

        while ((inputLine = in.readLine()) != null) {
            if (inputLine.startsWith("GET") || inputLine.startsWith("POST")) {
                String[] requestParts = inputLine.split(" ");
                method = requestParts[0];
                path = requestParts[1];
            }
            if (!in.ready()) break;
        }

        return !method.isEmpty() ? new Request(method, path) : null;
    }

    private static void processRequest(OutputStream outputStream, Request request) throws IOException {
        if (request.getPath().startsWith("/api/songs")) {
            handleApiRequest(outputStream, request);
        } else {
            serveStaticFile(outputStream, request.getPath());
        }
    }

    /**
     * Maneja las solicitudes a la API de canciones.
     *
     * @param outputStream Flujo de salida para la respuesta
     * @param request Solicitud a procesar
     * @throws IOException Si hay error de E/S
     */
    static void handleApiRequest(OutputStream outputStream, Request request) throws IOException {
        PrintWriter out = new PrintWriter(outputStream, true);

        try {
            if ("GET".equals(request.getMethod()) && "/api/songs".equals(request.getPath())) {
                sendJsonResponse(out, 200, formatSongsJson());
            }
            else if ("POST".equals(request.getMethod()) && request.getPath().startsWith("/api/songs/add")) {
                Song newSong = parseNewSong(request.getPath());
                songs.add(newSong);
                sendJsonResponse(out, 200, "{\"status\":\"success\", \"message\":\"Canción agregada\"}");
            }
            else {
                sendJsonResponse(out, 404, "{\"error\":\"Endpoint no encontrado\"}");
            }
        } catch (Exception e) {
            sendJsonResponse(out, 500, "{\"error\":\"Error en el servidor: " + e.getMessage() + "\"}");
        }
    }

    /**
     * Extrae los parámetros de título y artista de la URL.
     *
     * @param path Ruta de la solicitud con parámetros
     * @return Nueva instancia de Song
     * @throws UnsupportedEncodingException Si hay error al decodificar
     */
    private static Song parseNewSong(String path) throws UnsupportedEncodingException {
        String query = path.split("\\?")[1];
        String[] params = query.split("&");
        String title = "";
        String artist = "";

        for (String param : params) {
            String[] keyValue = param.split("=");
            String decodedValue = URLDecoder.decode(keyValue[1], "UTF-8");
            if ("title".equals(keyValue[0])) {
                title = decodedValue;
            } else if ("artist".equals(keyValue[0])) {
                artist = decodedValue;
            }
        }

        return new Song(title, artist);
    }

    private static String formatSongsJson() {
        StringBuilder json = new StringBuilder("{\"songs\":[");
        for (int i = 0; i < songs.size(); i++) {
            if (i > 0) json.append(",");
            json.append(songs.get(i).toString());
        }
        json.append("]}");
        return json.toString();
    }

    private static void sendJsonResponse(PrintWriter out, int statusCode, String jsonBody) {
        out.println("HTTP/1.1 " + statusCode + " " + getStatusMessage(statusCode));
        out.println("Content-Type: application/json");
        out.println();
        out.println(jsonBody);
    }

    private static String getStatusMessage(int statusCode) {
        switch (statusCode) {
            case 200: return "OK";
            case 404: return "Not Found";
            case 500: return "Internal Server Error";
            default: return "Unknown Status";
        }
    }

    /**
     * Sirve archivos estáticos desde el directorio resources.
     *
     * @param outputStream Flujo de salida para la respuesta
     * @param requestPath Ruta solicitada por el cliente
     * @throws IOException Si hay error de E/S
     */
    static void serveStaticFile(OutputStream outputStream, String requestPath) throws IOException {
        String path = requestPath.equals("/") ? "/index.html" : requestPath;
        Path filePath = Paths.get("src/main/resources" + path);

        try (PrintWriter out = new PrintWriter(outputStream, true)) {
            if (Files.exists(filePath)) {
                String extension = path.substring(path.lastIndexOf(".") + 1);
                String contentType = CONTENT_TYPES.getOrDefault(extension, "text/plain");
                byte[] fileContent = Files.readAllBytes(filePath);

                out.println("HTTP/1.1 200 OK");
                out.println("Content-Type: " + contentType);
                out.println("Content-Length: " + fileContent.length);
                out.println();
                out.flush();

                outputStream.write(fileContent);
            } else {
                out.println("HTTP/1.1 404 Not Found");
                out.println("Content-Type: text/html");
                out.println();
                out.println("<h1>404 Not Found</h1>");
            }
        }
    }

    /**
     * Clase interna para manejar datos de la solicitud
     */
    static class Request {
        private final String method;
        private final String path;

        public Request(String method, String path) {
            this.method = method;
            this.path = path;
        }

        public String getMethod() {
            return method;
        }

        public String getPath() {
            return path;
        }
    }
}