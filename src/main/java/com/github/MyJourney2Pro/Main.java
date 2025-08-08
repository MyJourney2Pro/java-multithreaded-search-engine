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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


import static org.apache.http.impl.client.HttpClients.createDefault;
import static org.jsoup.nodes.Document.OutputSettings.Syntax.html;

public class Main {
    public static void main(String[] args) throws IOException {
        List<String> linkPool = new ArrayList<>();         // 先建一个链接池
        Set<String> processedLinks = new HashSet<>();      //处理过的链接的池子
        linkPool.add("http://sina.cn");                    // 链接池一开始有sina的主页

        String link = null;
        while (!linkPool.isEmpty()) {                        // 如果链接池不是空的就继续
            link = linkPool.remove(linkPool.size() - 1);
            if (processedLinks.contains(link)) {             // 如果从链接池取出的链接是处理过的就跳过这一个链接, 继续下一个链接
                continue;
            }

            if (!link.contains("sina.cn")
                    || link.contains("password.sina.cn")
                    || (!link.contains("new.sina.cn")
                    && !link.equals("https://sina.cn")
                    && !link.equals("http://sina.cn"))) {
                continue;
            }


            try (CloseableHttpClient httpclient = createDefault()) {
                if (link.startsWith("//")) {
                    link = "https:" + link;
                }

                HttpGet httpGet = new HttpGet(link);
                httpGet.addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36");


                try (CloseableHttpResponse response1 = httpclient.execute(httpGet)) {
                    HttpEntity entity1 = response1.getEntity();

                    String html = EntityUtils.toString(entity1); // 一次性读到 String
                    Document doc = Jsoup.parse(html);            // 用jsoup解析html的string

                    // 如果是新闻页面就存入数据库, 不然就什么都不做
                    Elements articleTags = doc.select("article");
                    for (Element articleTag : articleTags) {
                        String title = articleTags.get(0).child(0).text(); // 用当前循环的元素
                        System.out.println(title);
                    }
                }
            }


            processedLinks.add(link);   // 处理之后加入processed的池子里

        }
    }
}