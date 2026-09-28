import app.inkbench.studio.GatewayClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class ModelEndpointsHttpTest {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static JSONObject model(String id) { return new JSONObject().put("id", id); }
    static String catalog(String field, Object... entries) {
        JSONArray list = new JSONArray();
        for (Object entry : entries) list.put(entry);
        return new JSONObject().put(field, list).toString();
    }
    static String error(String message) {
        return new JSONObject().put("error", new JSONObject().put("message", message)).toString();
    }
    static class Endpoint implements AutoCloseable {
        final HttpServer server;
        final String key;
        final List<String> paths = Collections.synchronizedList(new ArrayList<String>());
        volatile String catalog;
        volatile int status = 200;
        volatile String lastModel = "";
        volatile String handlerFailure = "";
        volatile boolean relativeImage = true;
        Endpoint(String key, String catalog) throws Exception {
            this.key = key;
            this.catalog = catalog;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                try {
                    String path = exchange.getRequestURI().getPath();
                    paths.add(path);
                    int code = 200;
                    String response;
                    if (!("Bearer " + key).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                        code = 401; response = error("fixture: wrong key");
                    } else if (path.equals("/v1/models") || path.equals("/proxy/v1/models")) {
                        if (!"GET".equals(exchange.getRequestMethod())) handlerFailure = "catalog method";
                        code = status; response = this.catalog;
                    } else if (path.equals("/v1/chat/completions") || path.equals("/proxy/v1/chat/completions")) {
                        if (!"POST".equals(exchange.getRequestMethod())) handlerFailure = "chat method";
                        JSONObject body = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                        lastModel = body.getString("model");
                        response = new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("message",
                                new JSONObject().put("content", "fixture prompt")))).toString();
                    } else if (path.equals("/v1/images/generations") || path.equals("/proxy/v1/images/generations")) {
                        if (!"POST".equals(exchange.getRequestMethod())) handlerFailure = "image method";
                        JSONObject body = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                        lastModel = body.getString("model");
                        if (body.has("user") || body.has("accountId")) handlerFailure = "invented account selector";
                        response = new JSONObject().put("data", new JSONArray().put(new JSONObject().put("url",
                                relativeImage ? "/picture" : base() + "/picture"))).toString();
                    } else if (path.equals("/picture")) {
                        response = "fixture-image-bytes";
                    } else {
                        code = 404; response = error("fixture: wrong path " + path);
                    }
                    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(code, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (Throwable e) {
                    handlerFailure = e.toString();
                } finally { exchange.close(); }
            });
            server.start();
        }
        String base() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        GatewayClient client(String suffix) { return new GatewayClient(base() + suffix, key, 30); }
        public void close() { server.stop(0); }
    }
    public static void main(String[] args) throws Exception {
        String both = new JSONObject(catalog("data", model("text-model"), model("gpt-image-2")))
                .put("models", new JSONArray().put(model("gpt-image-2"))).toString();
        String[] suffixes = {"", "/", "/v1", "/v1/", "/v1/models", "/v1/images/generations/",
                "/v1/images/edits", "/v1/chat/completions", "/proxy", "/proxy/v1/",
                "/proxy/v1/images/generations", "/proxy/v1/models/"};
        try (Endpoint image = new Endpoint("fixture-image-key", both);
             Endpoint text = new Endpoint("fixture-text-key", catalog("models", model("text-model")))) {
            for (String suffix : suffixes) {
                check(image.client(suffix).listImageModels().equals(Collections.singletonList("gpt-image-2")), "image listing: " + suffix);
                check(text.client(suffix).listModels().equals(Collections.singletonList("text-model")), "text listing: " + suffix);
                check("fixture prompt".equals(text.client(suffix).enhancePrompt("apple", "", "text-model")), "text request: " + suffix);
                check("fixture prompt".equals(text.client(suffix).suggestIdeas("apple", "", "text-model", 75)), "suggest ideas request: " + suffix);
                check(image.client(suffix).generate("apple", "1024x1024", "standard", "gpt-image-2").size() == 1, "image request: " + suffix);
                check("text-model".equals(text.lastModel) && "gpt-image-2".equals(image.lastModel), "models crossed");
            }
            ExecutorService threads = Executors.newFixedThreadPool(2);
            try {
                Future<List<String>> images = threads.submit(() -> image.client("/v1").listImageModels());
                Future<List<String>> texts = threads.submit(() -> text.client("/v1").listModels());
                check(images.get().equals(Collections.singletonList("gpt-image-2")), "parallel image catalog");
                check(texts.get().equals(Collections.singletonList("text-model")), "parallel text catalog");
            } finally { threads.shutdownNow(); }
            image.relativeImage = false;
            check(image.client("/v1").generate("apple", "1024x1024", "standard", "manual-image-id").size() == 1, "absolute image URL");
            check("manual-image-id".equals(image.lastModel), "manual image model replaced");
            text.client("/v1").enhancePrompt("apple", "", "manual-text-id");
            check("manual-text-id".equals(text.lastModel), "manual text model replaced");
            String[] catalogs = {
                catalog("data", model("gpt-image-2")),
                catalog("models", new JSONObject().put("slug", "gpt-image-2")),
                new JSONObject(catalog("data")).put("models", new JSONArray().put(model("").put("slug", "gpt-image-2"))).toString(),
                catalog("models", "gpt-image-2", "gpt-image-2", JSONObject.NULL, 12),
                new JSONArray().put("gpt-image-2").put(model("gpt-image-2")).toString(),
                catalog("data", new JSONObject().put("id", JSONObject.NULL).put("slug", "gpt-image-2"))
            };
            for (String response : catalogs) {
                image.catalog = response;
                check(image.client("/v1").listImageModels().equals(Collections.singletonList("gpt-image-2")), "catalog shape");
            }
            image.catalog = catalog("data",
                    model("renderer-one").put("type", "image_generation"),
                    model("renderer-two").put("capability", "image-generation"),
                    model("renderer-three").put("capabilities", new JSONObject().put("image_generation", true)),
                    model("renderer-four").put("capabilities", new JSONArray().put("text_to_image")),
                    model("vision-model").put("capabilities", new JSONObject().put("vision", true)),
                    model("grok-imagine"));
            check(image.client("").listImageModels().equals(Arrays.asList("renderer-one", "renderer-two", "renderer-three", "renderer-four", "grok-imagine")), "image capabilities and vision exclusion");
            check(image.client("").listModels().equals(Collections.singletonList("vision-model")), "vision is not image generation");
            for (String empty : new String[]{catalog("data"), catalog("data", model("text-model")), catalog("models", JSONObject.NULL, 42)}) {
                image.catalog = empty;
                check(image.client("").listImageModels().isEmpty(), "invented image model");
            }
            for (String invalid : new String[]{"<html>Login</html>", "{}", new JSONObject().put("data", JSONObject.NULL).toString()}) {
                image.catalog = invalid;
                try { image.client("").listImageModels(); throw new AssertionError("invalid catalog accepted"); }
                catch (GatewayClient.ApiException e) { check("invalid_models".equals(e.type), "invalid catalog classification"); }
            }
            for (int status : new int[]{401, 403, 404, 405, 429, 502}) {
                image.status = status; image.catalog = error("fixture failure");
                int before = image.paths.size();
                try { image.client("").listImageModels(); throw new AssertionError("HTTP failure hidden"); }
                catch (GatewayClient.ApiException e) { check(e.status == status, "lost HTTP status"); }
                check(image.paths.size() == before + 1, "model refresh retried");
            }
            image.status = 200; image.catalog = both;
            try { new GatewayClient(image.base(), "fixture-text-key", 30).listImageModels(); throw new AssertionError("wrong key accepted"); }
            catch (GatewayClient.ApiException e) { check(e.status == 401, "separate keys"); }
            for (String invalidSuffix : new String[]{"/v1?key=fixture", "/v1#fragment"}) {
                int before = image.paths.size();
                try { image.client(invalidSuffix).listModels(); throw new AssertionError("invalid address accepted"); }
                catch (GatewayClient.ApiException e) { check("config".equals(e.type), "address validation"); }
                check(image.paths.size() == before, "invalid address sent");
            }
            GatewayClient cancelled = image.client(""); cancelled.cancel();
            int before = image.paths.size();
            try { cancelled.listImageModels(); throw new AssertionError("cancel ignored"); }
            catch (GatewayClient.ApiException e) { check(e.status == 499, "lost cancellation"); }
            check(image.paths.size() == before, "cancelled request sent");
            check(image.paths.stream().noneMatch(p -> p.contains("/v1/v1")), "duplicate v1");
            check(image.paths.stream().noneMatch(p -> p.endsWith("chat/completions")), "text calls reached image gateway");
            check(text.paths.stream().noneMatch(p -> p.endsWith("images/generations")), "image calls reached text gateway");
            check(image.handlerFailure.isEmpty() && text.handlerFailure.isEmpty(), "server assertion: " + image.handlerFailure + text.handlerFailure);
        }
        System.out.println("PASS: independent gateways/keys/models; 12 URL forms; relative/absolute downloads; catalog formats, capabilities, empty/error responses and cancellation");
    }
}
