import app.inkbench.studio.GatewayClient;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises the production client against a local HTTP server; no provider calls. */
public class GatewayHttpTest {
 static final List<JSONObject> bodies = Collections.synchronizedList(new ArrayList<JSONObject>());
 static volatile int failureStatus;
 static volatile String failureMessage;
 static volatile String successBody;
 static volatile boolean alwaysFail;
 static volatile String modelCatalog = "";
 static final AtomicInteger requests = new AtomicInteger();
 static String base;
 static void reset(int code, String message) {
  bodies.clear(); requests.set(0); failureStatus=code; failureMessage=message; alwaysFail=false;
  successBody="{\"data\":[{\"url\":\""+base+"/picture\"}]}";
 }
 static void check(boolean ok,String reason) { if(!ok) throw new AssertionError(reason); }
 static GatewayClient client(){return new GatewayClient(base,"test-only-key",30);}
 public static void main(String[] args) throws Exception {
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  base="http://127.0.0.1:"+server.getAddress().getPort();
  server.createContext("/v1/images/generations", exchange -> {
   try {
    String request=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
    bodies.add(new JSONObject(request));
    int attempt=requests.incrementAndGet();
    int code=(attempt==1 || alwaysFail)?failureStatus:200;
    String response=code>=400?new JSONObject().put("error",new JSONObject()
      .put("type","upstream_error").put("message",failureMessage)).toString():successBody;
    byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type","application/json");
    exchange.sendResponseHeaders(code,bytes.length);
    exchange.getResponseBody().write(bytes);
   } finally { exchange.close(); }
  });
  server.createContext("/picture",exchange -> {
   byte[] bytes={1,2,3}; exchange.sendResponseHeaders(200,bytes.length);
   exchange.getResponseBody().write(bytes); exchange.close();
  });
  server.createContext("/v1/models",exchange -> {
   String response=modelCatalog;
   byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
   exchange.getResponseHeaders().set("Content-Type","application/json");
   exchange.sendResponseHeaders(200,bytes.length);
   exchange.getResponseBody().write(bytes); exchange.close();
  });
  server.start();
  modelCatalog="{\"object\":\"list\",\"models\":[{\"id\":\"gpt-5.6\"},{\"id\":\"gpt-image-2\"}],\"data\":[{\"id\":\"gpt-5.6\"},{\"id\":\"gpt-image-2\"}]}";
  try {
   List<String> textModels=client().listModels();
   List<String> imageModels=client().listImageModels();
   check(textModels.size()==1 && textModels.contains("gpt-5.6"),"M365 text catalog parsing");
   check(imageModels.size()==1 && imageModels.contains("gpt-image-2"),"M365 image catalog parsing");
   modelCatalog="{\"data\":[{\"id\":\"gpt-5.6-reasoning\"}]}";
   check(client().listImageModels().isEmpty(),"text-only catalog must not invent image models");
   modelCatalog="{\"models\":[{\"slug\":\"gpt-image-2\"}]}";
   check(client().listImageModels().equals(Collections.singletonList("gpt-image-2")),"models slug field parsing");
   modelCatalog="{\"data\":[]}";
   check(client().listImageModels().isEmpty(),"empty catalog must not invent models");
   for(int code:new int[]{400,422,502}) {
    reset(code,"Unsupported parameter: aspect_ratio");
    GatewayClient c=client();
    c.generate("one apple","1024x1536","high","custom-model");
    check(requests.get()==2,"parameter rejection repeated: "+code);
    check("1024x1536".equals(bodies.get(0).getString("size")),"quality changed ratio");
    check(!bodies.get(1).has("size"),"fallback kept size");
    for(String key:new String[]{"prompt","model","n","response_format"})
     check(bodies.get(0).get(key).equals(bodies.get(1).get(key)),"fallback changed "+key);
    c.generate("another apple","1024x1536","high","custom-model");
    check(!bodies.get(2).has("size"),"batch forgot successful compatibility");
    c.generate("apple","1024x1536","high","other-model");
    check(bodies.get(3).has("size"),"compatibility leaked to other model");
   }
   reset(502,"aspect_ratio is not supported"); alwaysFail=true;
   try {client().generate("apple","1024x1024","standard","m");throw new AssertionError("expected error");}
   catch(GatewayClient.ApiException e){check(e.status==502,"lost status");}
   check(requests.get()==2,"fallback loop");
   reset(502,"Bad Gateway");
   client().generate("apple","1024x1024","ultra","m");
   check(requests.get()==2,"transient retry failed");
   check(bodies.get(0).toString().equals(bodies.get(1).toString()),"ordinary 502 mutated request");
   for(int code:new int[]{400,401,403,429}) {
    reset(code,"invalid prompt");
    try {client().generate("apple","1024x1024","standard","m");throw new AssertionError("expected rejection");}
    catch(GatewayClient.ApiException e){check(e.status==code,"lost rejection status");}
    check(requests.get()==1,"retried permanent error");
   }
   reset(502,"Bad Gateway");
   GatewayClient cancelled=client().withProgress(null);
   cancelled.withProgress(message -> { if(message.contains("HTTP 502")) cancelled.cancel(); });
   try {cancelled.generate("apple","1024x1024","standard","m");throw new AssertionError("expected cancel");}
   catch(GatewayClient.ApiException e){check(e.status==499,"lost cancellation");}
   check(requests.get()==1,"cancelled request retried");
   reset(200,""); successBody="{\"data\":[]}";
   try {client().generate("apple","1024x1024","standard","m");throw new AssertionError("expected empty image error");}
   catch(GatewayClient.ApiException e){check(e.status==200 && "empty_images".equals(e.type),"invented 502");}
   check(requests.get()==1,"retried successful empty response");
   System.out.println("PASS: production HTTP client: parameter fallback, batch cache isolation, stable retry body, size preservation, permanent errors, cancellation, actual HTTP status");
  } finally {server.stop(0);}
 }
}
