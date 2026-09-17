package Timeout.travel_tackle.tour;

import Timeout.travel_tackle.tour.client.TourApiClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TourApiClientTests {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void callsKorServiceEndpointAndParsesJsonResponse() throws IOException {
        AtomicReference<String> requestedUri = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> respond(exchange, requestedUri));
        server.start();

        // 다국어 서비스 지원 이후 base-url은 서비스 세그먼트 없이 /B551011까지만 —
        // 클라이언트가 요청마다 "/{service}/{endpoint}"를 붙인다 (기본 KorService2)
        String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/B551011";
        TourApiClient client = new TourApiClient(baseUrl, "test-service-key", "TravelTackle");

        TourApiClient.TourApiResult result =
                client.getAreaContents("1", "23", "12", 1, 20, "A");

        assertEquals(1, result.totalCount());
        assertEquals("경복궁", result.items().getFirst().path("title").asText());
        assertTrue(requestedUri.get().startsWith(
                "/B551011/KorService2/areaBasedList2?"));
        assertTrue(requestedUri.get().contains("serviceKey=test-service-key"));
        assertTrue(requestedUri.get().contains("areaCode=1"));
    }

    private void respond(HttpExchange exchange, AtomicReference<String> requestedUri) throws IOException {
        requestedUri.set(exchange.getRequestURI().toString());
        byte[] response = """
                {
                  "response": {
                    "header": {"resultCode": "0000", "resultMsg": "OK"},
                    "body": {
                      "items": {"item": [{"contentid": "125266", "title": "경복궁"}]},
                      "pageNo": 1,
                      "numOfRows": 20,
                      "totalCount": 1
                    }
                  }
                }
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
