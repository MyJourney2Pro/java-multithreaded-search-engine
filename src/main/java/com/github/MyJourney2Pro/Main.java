package com.github.MyJourney2Pro;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.apache.http.impl.client.HttpClients.createDefault;

public class Main {
    public static void main(String[] args) throws IOException {
        boolean isCI = System.getenv("CI") != null; // 检测 CI 环境变量

        List<String> linkPool = new ArrayList<>();
        Set<String> processedLinks = new HashSet<>();
        linkPool.add("http://sina.cn");

        String link;
        while (!linkPool.isEmpty()) {
            link = linkPool.remove(linkPool.size() - 1);
            if (processedLinks.contains(link)) {
                continue;
            }

            // 过滤不需要的链接
            if (!link.contains("sina.cn")
                    || link.contains("password.sina.cn")
                    || (!link.contains("new.sina.cn")
                    && !link.equals("https://sina.cn")
                    && !link.equals("http://sina.cn"))) {
                continue;
            }

            Document doc;

            if (isCI) {
                System.out.println("[CI MODE] Using local HTML file instead of network request.");
                try (InputStream in = Main.class.getResourceAsStream("/test.html")) {
                    if (in == null) {
                        throw new IOException("test.html not found in resources");
                    }
                    String html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    doc = Jsoup.parse(html);
                }
            } else {
                try (CloseableHttpClient httpclient = createDefault()) {
                    if (link.startsWith("//")) {
                        link = "https:" + link;
                    }
                    HttpGet httpGet = new HttpGet(link);
                    httpGet.addHeader("User-Agent",
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36");

                    try (CloseableHttpResponse response1 = httpclient.execute(httpGet)) {
                        HttpEntity entity1 = response1.getEntity();
                        String html = EntityUtils.toString(entity1);
                        doc = Jsoup.parse(html);
                    }
                } catch (IOException e) {
                    System.err.println("访问失败: " + link);
                    processedLinks.add(link);
                    continue;
                }
            }

            // 解析 <article> 并输出标题
            Elements articleTags = doc.select("article");
            for (Element articleTag : articleTags) {
                if (!articleTag.children().isEmpty()) { // 避免 child(0) 越界
                    String title = articleTag.child(0).text();
                    System.out.println(title);
                }
            }

            processedLinks.add(link);
        }
    }
}
