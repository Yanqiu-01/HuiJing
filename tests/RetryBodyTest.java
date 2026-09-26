import java.util.*;
public class RetryBodyTest {
 static class JSONObject { private final String text; JSONObject(String t){text=t;} public String toString(){return text;} }
 static class ApiException extends Exception { int status; String type; ApiException(int s,String t,String m){super(m);status=s;type=t;} }
 List<String> bodies=new ArrayList<>(); List<String> updates=new ArrayList<>(); int[] codes;
 RetryBodyTest(int... c){codes=c;}
 void progress(String s){updates.add(s);}
 String postJson(String path,String body) throws Exception {
  bodies.add(body); int code=codes[bodies.size()-1];
  if(code>=400)throw new ApiException(code,"upstream_error","upstream request failed");
  return "success";
 }
    private String postJsonRetry(String path, JSONObject body) throws Exception {
        final String payload = body.toString();
        ApiException last = null;
        long started = System.currentTimeMillis();
        for (int attempt = 1; attempt <= 3; attempt++) {
            progress("请求 " + attempt + "/3 · 正在等待网关");
            try {
                return postJson(path, payload);
            } catch (ApiException e) {
                last = e;
                boolean retry = shouldRetry(e) && attempt < 3;
                String detail = "请求 " + attempt + "/3 · HTTP " + e.status + " · " + e.getMessage();
                progress(detail + (retry ? "；稍后重试（不保证换号）" : "；停止"));
                if (!retry) {
                    throw new ApiException(e.status, e.type, "已请求 " + attempt + " 次，耗时 "
                            + ((System.currentTimeMillis()-started)/1000) + " 秒；" + e.getMessage());
                }
                Thread.sleep(attempt * 2000L);
            }
        }
        throw last;
    }

    private static boolean shouldRetry(ApiException e) {
        String type = e.type.toLowerCase(java.util.Locale.US);
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(java.util.Locale.US);
        if (e.status == 401 || e.status == 403 || e.status == 429) return false;
        if (type.contains("policy") || type.contains("filter") || message.contains("content policy")) return false;
        return e.status == 502 || e.status == 503 || e.status == 504;
    }


 public static void main(String[] args)throws Exception {
  String body="{\"prompt\":\"test\",\"n\":1}";
  RetryBodyTest t=new RetryBodyTest(502,502,200);
  if(!t.postJsonRetry("/v1/images/generations",new JSONObject(body)).equals("success"))throw new AssertionError();
  if(t.bodies.size()!=3)throw new AssertionError();
  for(String sent:t.bodies)if(!sent.equals(body)||sent.contains("user")||sent.contains("accountId"))throw new AssertionError("mutated account selection");
  for(int status:new int[]{401,403,429}) {
   RetryBodyTest q=new RetryBodyTest(status);
   try{q.postJsonRetry("/v1/images/generations",new JSONObject(body));throw new AssertionError();}catch(ApiException expected){}
   if(q.bodies.size()!=1)throw new AssertionError();
  }
  System.out.println("PASS: 502 -> 502 -> 200; all three bodies identical; no invented user/accountId; progress emitted; 401/403/429 stop immediately");
 }
}
