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

    // ✅ 修复：该方法用途是取“一个”链接，所以返回类型改为 String
    private static String getNextLink(Connection connection, String sql) throws SQLException {
        ResultSet resultSet = null;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString(1);
            }
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
        }
        return null;
    }

    private static String getNextLinkThenDelete(Connection connection) throws SQLException {
        String link = getNextLink(connection, "select link from LINKS_TO_BE_PROCESSED LIMIT 1");
        if (link != null) {
            // ✅ 修复：列名应为 LINK 而不是 links
            UpdateDatabase(connection, link, "DELETE FROM LINKS_TO_BE_PROCESSED WHERE LINK = ?");
        }
        return link;
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

    private static void UpdateDatabase(Connection connection, String link, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, link);
            statement.executeUpdate();
        }
    }

    // ✅ 补回你之前用到的工具方法：从数据库读一列结果到 List<String>
    // 保留原签名，避免 “cannot find symbol loadUrlFromDatabase(...)”
    private static List<String> loadUrlFromDatabase(Connection connection, String sql) throws SQLException {
        List<String> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        }
        return result;
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

            while (true) {
                // 从数据库取一个待处理链接
                String link = popOneLink(connection);

                if (link == null) {
                    System.out.println("done");
                    break;
                }

                // 如果已经处理过就跳过
                if (alreadyProcessed(connection, link)) {
                    continue;
                }

                if (shouldSkipLink(link)) {
                    markProcessed(connection, link);
                    continue;
                }

                Document doc = fetchDocument(link, isCI);
                if (doc == null) {
                    System.err.println("Document is null for link: " + link);
                    markProcessed(connection, link);
                    continue;
                }

                parseAndPrintTitles(doc);

                // 标记当前链接已处理
                markProcessed(connection, link);

                // 假设在这里解析到新链接并入队（示例）
                // for (String newUrl : findNewLinks(doc)) {
                //     enqueue(connection, newUrl);
                // }
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

    private static boolean isNewsPage(String link) {
        return link.contains("new.sina.cn");
    }

    private static boolean isLoginPage(String link) {
        return link.contains("password.sina.cn");
    }

    private static boolean isHomepage(String link) {
        return link.equals(HOMEPAGE_HTTP) || link.equals(HOMEPAGE_HTTPS);
    }
}
