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
  System.out.println("PASS: retry status matrix (9 cases) + policy refusal");
 }
}
