package com.github.MyJourney2Pro;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import java.io.IOException;


import static org.apache.http.impl.client.HttpClients.createDefault;

public class Main {
    public static void main(String[] args) throws IOException {
        try (CloseableHttpClient httpclient = createDefault()) {
            HttpGet httpGet = new HttpGet("https://sina.cn/");
            try (CloseableHttpResponse response1 = httpclient.execute(httpGet)) {
                System.out.println(response1.getStatusLine());
                HttpEntity entity1 = response1.getEntity();
                // do something useful with the response body
                // and ensure it is fully consumed
                System.out.println(EntityUtils.toString(entity1));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
