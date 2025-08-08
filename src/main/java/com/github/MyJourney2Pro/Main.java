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

import static java.lang.System.in;
import static org.apache.http.impl.client.HttpClients.createDefault;

public class Main {


    private static final String HOMEPAGE_HTTP  = "http://sina.cn";
    private static final String HOMEPAGE_HTTPS = "https://sina.cn";
    private static final String UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36";


    public static void main(String[] args) throws IOException {
        boolean isCI = System.getenv("CI") != null; // 检测 CI 环境变量

        List<String> linkPool = new ArrayList<>();    // 待处理的链接池
        Set<String> processedLinks = new HashSet<>();  // 已处理的链接池
        linkPool.add(HOMEPAGE_HTTP);                   // 先把新浪首页压进去


        while (!linkPool.isEmpty()) {
            String link = linkPool.remove(linkPool.size() - 1);
            if (processedLinks.contains(link)) {
                continue;
            }

            if ( shouldSkipLink(link)){
                processedLinks.add(link);
                continue;
            }

            Document doc = fetchDocument(link, isCI);
            if (doc == null) { // ✅ 检查 doc
                System.err.println("Document is null for link: " + link);
                processedLinks.add(link);
                continue;
            }

            parseAndPrintTitles(doc);



            parseAndPrintTitles(doc);                 // ✅ 解析并输出

            processedLinks.add(link);
        }
    }


    private static boolean shouldSkipLink(String link) {
        if (!link.contains("sina.cn")) {
            return true;
        }
        if (isLoginPage(link)) {
            return true;
        }
        if (!isNewsPage(link) && !isHomepage(link)) {
            return true;
        }
        return false;
    }


    private static Document fetchDocument(String link, boolean isCI) throws IOException {
        if (isCI) {
            try (InputStream in = Main.class.getResourceAsStream("/test.html")) {
                if (in == null) {
                    System.err.println("test.html not found in resources");
                    return null;
                }
                String html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return Jsoup.parse(html);
            }
        } else {
            if (link.startsWith("//")) {
                link = "https:" + link;
            }
            try (CloseableHttpClient httpclient = createDefault()) {
                HttpGet httpGet = new HttpGet(link);
                httpGet.addHeader("User-Agent", UA);
                try (CloseableHttpResponse resp = httpclient.execute(httpGet)) {
                    HttpEntity entity = resp.getEntity();
                    String html = EntityUtils.toString(entity);
                    return Jsoup.parse(html);
                }
            } catch (IOException e) {
                System.err.println("访问失败: " + link);
                return null;
            }
        }
    }


    private static void parseAndPrintTitles(Document doc) {
        Elements articleTags = doc.select("article");
        for (Element articleTag : articleTags) {
            if (!articleTag.children().isEmpty()) {
                System.out.println(articleTag.child(0).text());
            }
        }

    }
    private static boolean isNewsPage(String link){
        return link.contains("new.sina.cn");
    }

    private static boolean isLoginPage(String link){
        return link.contains("password.sina.cn");
    }

    private static boolean isHomepage(String link) {
        return link.equals(HOMEPAGE_HTTP) || link.equals(HOMEPAGE_HTTPS);
    }
}


