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


    private static List<String> loadUrlFromDatabase(Connection connection, String sql) throws SQLException {
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
            stmt.execute("CREATE TABLE IF NOT EXISTS LINKS_TO_BE_PROCESSED (LINK VARCHAR(1024) PRIMARY KEY)");
            stmt.execute("CREATE TABLE IF NOT EXISTS LINKS_ALREADY_PROCESSED (LINK VARCHAR(1024) PRIMARY KEY)");
            // ↓↓↓ 新增：把旧库里可能是 VARCHAR(100) 的列扩到 2048
            try {
                stmt.execute("ALTER TABLE LINKS_TO_BE_PROCESSED ALTER COLUMN LINK VARCHAR(2048)");
            } catch (SQLException ignore) {

            }
            try {
                stmt.execute("ALTER TABLE LINKS_ALREADY_PROCESSED ALTER COLUMN LINK VARCHAR(2048)");
            } catch (SQLException ignore) {

            }
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


    private static String popOneLink(Connection connection) throws SQLException {
        String link = null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT LINK FROM LINKS_TO_BE_PROCESSED LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                link = rs.getString(1);
            }
        }
        if (link != null) {
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM LINKS_TO_BE_PROCESSED WHERE LINK=?")) {
                del.setString(1, link);
                del.executeUpdate();
            }
        }
        return link;
    }

    //判断是否已处理
    private static boolean alreadyProcessed(Connection connection, String link) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM LINKS_ALREADY_PROCESSED WHERE LINK=?")) {
            ps.setString(1, link);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }


    // 标记已处理（避免重复插入）
    private static void markProcessed(Connection connection, String link) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "MERGE INTO LINKS_ALREADY_PROCESSED KEY(LINK) VALUES (?)")) {
            ps.setString(1, link);
            ps.executeUpdate();
        }
    }

    // 入队新链接（写入待处理表，去重）
    private static void enqueue(Connection connection, String link) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "MERGE INTO LINKS_TO_BE_PROCESSED KEY(LINK) VALUES (?)")) {
            ps.setString(1, link);
            ps.executeUpdate();
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

            int maxPages = 30; // 防止失控
            for (int i = 0; i < maxPages; i++) {

                // FIX: 不再使用未定义的 linkPool；每次从数据库“弹出”一个
                String link = popOneLink(connection);
                if (link == null) {
                    break;
                }

                if (shouldSkipLink(link)) {
                    markProcessed(connection, link);
                    continue;
                }
                if (alreadyProcessed(connection, link)) {
                    continue;
                }


                Document doc = fetchDocument(link, isCI);
                if (doc == null) { // ✅ 检查 doc
                    System.err.println("Document is null for link: " + link);
                    markProcessed(connection, link);
                    continue;
                }

                // 打印并把页面中的链接写回待处理表
                parseAndPrintTitles(doc, connection);

                // 标记已处理
                markProcessed(connection, link);
            }
        }
        System.out.println("Done.");
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
                    // 传 baseUri，后面 absUrl 才能解析相对链接
                    return Jsoup.parse(html, link);
                }
            } catch (IOException e) {
                System.err.println("访问失败: " + link);
                return null;
            }
        }
    }


    // 传入 Connection；打印标题；把 a[href] 的绝对链接入库
    private static void parseAndPrintTitles(Document doc, Connection connection) throws SQLException {
        System.out.println("[PAGE] " + doc.title());

        Elements articleTags = doc.select("article h1, article h2, h1, h2");
        int count = 0;
        for (Element h : articleTags) {
            if (count++ >= 10) {
                break;
            }
            System.out.println("  - " + h.text());
        }

        // 把页面中的链接加入待处理（限定域）
        for (Element a : doc.select("a[href]")) {
            String next = a.absUrl("href");
            if (!shouldSkipLink(next)) {
                enqueue(connection, next);
            }
        }
    }

    private static boolean isNewsPage(String link){
        return link.contains("new.sina.cn") || link.contains("news.sina.cn");
    }

    private static boolean isLoginPage(String link){
        return link.contains("password.sina.cn");
    }

    private static boolean isHomepage(String link) {
        return link.equals(HOMEPAGE_HTTP) || link.equals(HOMEPAGE_HTTPS);
    }
}

