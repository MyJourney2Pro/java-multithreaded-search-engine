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
import java.sql.*;
import java.util.*;
import static org.apache.http.impl.client.HttpClients.createDefault;

public class Main {


    private static final String HOMEPAGE_HTTP  = "http://sina.cn";
    private static final String HOMEPAGE_HTTPS = "https://sina.cn";
    private static final String UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36";


    private static List<String> loadUrlFromDatabase(Connection connection,String sql) throws SQLException {
        List<String> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                result.add(resultSet.getString(1));
            }
        }
        return result;
    }


    // 初始化数据库结构（如果不存在）
    private static void initSchema(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS LINKS_TO_BE_PROCESSED (" +
                    "LINK VARCHAR(1024) PRIMARY KEY" +
                    ")");
            stmt.execute("CREATE TABLE IF NOT EXISTS LINKS_ALREADY_PROCESSED (" +
                    "LINK VARCHAR(1024) PRIMARY KEY" +
                    ")");
        }
    }

    private static void seedIfEmpty(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM LINKS_TO_BE_PROCESSED")) {
            rs.next();
            if (rs.getInt(1) == 0) {
                stmt.executeUpdate("INSERT INTO LINKS_TO_BE_PROCESSED(LINK) VALUES ('http://sina.cn')");
            }
        }
    }



    public static void main(String[] args) throws IOException, SQLException {

        boolean isCI = System.getenv("CI") != null; // 检测 CI 环境变量

        final String jdbcUrl = isCI
                ? "jdbc:h2:mem:news;DB_CLOSE_DELAY=-1"
                : "jdbc:h2:file:./news;AUTO_SERVER=TRUE";

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "root", "")) {

            // 确保表存在 & 初始数据
            initSchema(connection);
            seedIfEmpty(connection);

            // String url = "jdbc:h2:file:/Users/aliran/news";
            // Connection connection = DriverManager.getConnection(url);

            // 从new一个待处理的链接池变成从数据库加载待处理的链接的代码👉因为数据库能实现数据的持久化
            List<String> linkPool = loadUrlFromDatabase(connection, "select link from LINKS_TO_BE_PROCESSED");
            // 从new一个已处理的链接池变成从数据库加载已经处理的链接的代码
            Set<String> processedLinks = new HashSet<>(loadUrlFromDatabase(connection, "select link from LINKS_ALREADY_PROCESSED"));

            if (linkPool.isEmpty()) linkPool.add(HOMEPAGE_HTTP);                   // 先把新浪首页压进去


            while (!linkPool.isEmpty()) {
                // 这里变成每次处理完链接后要更新数据库
                String link = linkPool.remove(linkPool.size() - 1);
                if (processedLinks.contains(link)) {
                    continue;
                }

                if (shouldSkipLink(link)) {
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
                processedLinks.add(link);
            }
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


