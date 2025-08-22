package edu.escuelaing.arem.ASE.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;


public class HttpServerTest {

    private HttpServer server;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    public void setUp() {
        server = new HttpServer();
        outputStream = new ByteArrayOutputStream();
    }

    @Test
    public void testParseRequestGET() throws IOException {
        String request = "GET /api/songs HTTP/1.1\nHost: localhost\n\n";
        BufferedReader reader = new BufferedReader(new StringReader(request));

        HttpServer.Request parsed = HttpServer.parseRequest(reader);

        assertAll(
                () -> assertEquals("GET", parsed.getMethod()),
                () -> assertEquals("/api/songs", parsed.getPath())
        );
    }

    @Test
    public void testParseRequestPOST() throws IOException {
        String request = "POST /api/songs/add?title=Test&artist=Test HTTP/1.1\nHost: localhost\n\n";
        BufferedReader reader = new BufferedReader(new StringReader(request));

        HttpServer.Request parsed = HttpServer.parseRequest(reader);

        assertAll(
                () -> assertEquals("POST", parsed.getMethod()),
                () -> assertEquals("/api/songs/add?title=Test&artist=Test", parsed.getPath())
        );
    }

    @Test
    public void testHandleApiRequestGetSongs() throws IOException {
        HttpServer.Request request = new HttpServer.Request("GET", "/api/songs");
        PrintWriter out = new PrintWriter(outputStream);

        HttpServer.handleApiRequest(outputStream, request);
        String response = outputStream.toString();

        assertAll(
                () -> assertTrue(response.contains("HTTP/1.1 200 OK")),
                () -> assertTrue(response.contains("Content-Type: application/json")),
                () -> assertTrue(response.contains("Bohemian Rhapsody")),
                () -> assertTrue(response.contains("Queen"))
        );
    }

    @Test
    public void testHandleApiRequestAddSong() throws IOException {
        HttpServer.Request request = new HttpServer.Request("POST", "/api/songs/add?title=NewSong&artist=NewArtist");
        PrintWriter out = new PrintWriter(outputStream);

        HttpServer.handleApiRequest(outputStream, request);
        String response = outputStream.toString();

        assertAll(
                () -> assertTrue(response.contains("HTTP/1.1 200 OK")),
                () -> assertTrue(response.contains("Canción agregada"))
        );
    }

    @Test
    public void testHandleApiRequestInvalidEndpoint() throws IOException {
        HttpServer.Request request = new HttpServer.Request("GET", "/api/invalid");
        PrintWriter out = new PrintWriter(outputStream);

        HttpServer.handleApiRequest(outputStream, request);
        String response = outputStream.toString();

        assertAll(
                () -> assertTrue(response.contains("HTTP/1.1 404 Not Found")),
                () -> assertTrue(response.contains("Endpoint no encontrado"))
        );
    }

    @Test
    public void testServeStaticFileExisting() throws IOException {
        // Crear archivo de prueba temporal
        String testContent = "<html><body>Test</body></html>";
        Path testFile = Paths.get("src/main/resources/test.html");
        Files.write(testFile, testContent.getBytes());

        HttpServer.serveStaticFile(outputStream, "/test.html");
        String response = outputStream.toString();

        assertAll(
                () -> assertTrue(response.contains("HTTP/1.1 200 OK")),
                () -> assertTrue(response.contains("text/html")),
                () -> assertTrue(response.contains(testContent))
        );

        // Limpiar
        Files.deleteIfExists(testFile);
    }

    @Test
    public void testServeStaticFileNotFound() throws IOException {
        HttpServer.serveStaticFile(outputStream, "/nonexistent.html");
        String response = outputStream.toString();

        assertAll(
                () -> assertTrue(response.contains("HTTP/1.1 404 Not Found")),
                () -> assertTrue(response.contains("text/html"))
        );
    }



}