import app.inkbench.studio.GatewayClient;
import java.lang.reflect.Method;
public class RetryTest {
 public static void main(String[] args) throws Exception {
  Method m=GatewayClient.class.getDeclaredMethod("shouldRetry",GatewayClient.ApiException.class);m.setAccessible(true);
  int[] codes={401,403,429,502,503,504,400,0,200};
  for(int c:codes) {
   boolean got=(Boolean)m.invoke(null,new GatewayClient.ApiException(c,"upstream_error","test"));
   boolean want=c==502||c==503||c==504;
   if(got!=want)throw new AssertionError("HTTP "+c);
  }
  if((Boolean)m.invoke(null,new GatewayClient.ApiException(502,"content_policy_error","blocked")))throw new AssertionError("policy");
  Method aspect=GatewayClient.class.getDeclaredMethod("isUnsupportedAspectRatio",GatewayClient.ApiException.class);aspect.setAccessible(true);
  if(!(Boolean)aspect.invoke(null,new GatewayClient.ApiException(400,"invalid_request_error","Unsupported parameter: aspect_ratio")))throw new AssertionError("aspect_ratio rejection not detected");
  if(!(Boolean)aspect.invoke(null,new GatewayClient.ApiException(422,"invalid_request_error","size is not supported for this model")))throw new AssertionError("size rejection not detected");
  if((Boolean)aspect.invoke(null,new GatewayClient.ApiException(400,"invalid_request_error","invalid prompt")))throw new AssertionError("unrelated 400 classified as size rejection");
  if(!(Boolean)aspect.invoke(null,new GatewayClient.ApiException(502,"upstream_error","Unsupported aspect_ratio")))throw new AssertionError("wrapped parameter rejection missed");
  for(String text:new String[]{"Bad Gateway","invalid prompt for size 1024x1024","unsupported max_size","invalid api key; size unsupported"}) {
   if((Boolean)aspect.invoke(null,new GatewayClient.ApiException(502,"upstream_error",text)))throw new AssertionError("false fallback: "+text);
  }
  if((Boolean)aspect.invoke(null,new GatewayClient.ApiException(502,"content_policy_error","size unsupported")))throw new AssertionError("policy fallback");
  GatewayClient cancelled = new GatewayClient("http://127.0.0.1:1", "test", 30);
  cancelled.cancel();
  if (!cancelled.isCancelled()) throw new AssertionError("cancel flag");
  System.out.println("PASS: retry status matrix (9 cases) + policy refusal + cancellation flag");
 }
}
