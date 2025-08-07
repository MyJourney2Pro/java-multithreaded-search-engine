# 🕸️ Java Multithreaded Web Crawler & Elasticsearch Data Analysis
This project is a practical implementation of a multithreaded web crawler written in Java. It collects online news data and indexes it into Elasticsearch for full-text search and structured analysis. The project focuses on efficient multithreaded crawling, data extraction, and integration with Elasticsearch.

## 🚀 Features

- 🔄 Multithreaded Web Crawler: Efficiently fetches and parses news articles using multiple threads.
- 🧠 Text Preprocessing: Cleans and normalizes HTML content into searchable text.
- 📦 Elasticsearch Integration: Indexes articles into Elasticsearch for powerful full-text search.
- 🔍 RESTful API: Search articles by keyword, title, date, or source.
- 🧪 JUnit Test Coverage: Includes unit tests for core components like file IO and crawling.

---

## 🛠️ Tech Stack

| Layer            | Technology      |
|------------------|-----------------|
| Language         | Java 17+        |
| Build Tool       | Maven           |
| Web Framework    | SpringBoot      |
| Crawler Threading| `ExecutorService`, `ThreadPool` |
| Search Engine    | Elasticsearch 8.x |
| JSON Handling    | Jackson / GSON  |
| Logging          | SLF4J + Logback |
| Testing          | JUnit 5         |

---

## ⚙️ How to Run

### 🧾 Prerequisites

- Java 17+
- Maven 3.6+
- Elasticsearch 8.x running locally (`http://localhost:9200`)

### 🛠 Build

```bash
mvn clean install
````

### ▶️ Run Crawler

```bash
java -jar target/news-crawler.jar
```

This will crawl and index news articles into Elasticsearch.

### 🔎 Run Search API Server

If you implemented a RESTful API (e.g., with Spring Boot):

```bash
mvn spring-boot:run
```

Or:

```bash
java -jar target/news-search-api.jar
```

---

## 🧪 API Examples

### `GET /search?q=earthquake`

```json
[
  {
    "title": "Earthquake Shakes Sydney",
    "url": "https://news.example.com/earthquake-sydney",
    "snippet": "A magnitude 5.3 earthquake was felt...",
    "publishedAt": "2025-08-06T09:00:00Z"
  },
  ...
]
```

---

## 🧹 Tips for Customization

* To change the crawl source, update `SeedURLs.txt` or modify the crawler logic.
* To change the index name in Elasticsearch, update the configuration in `ElasticIndexer.java`.
* To improve performance, tune the thread pool size and bulk index size.

---

## 🙋 Contact

Feel free to reach out or open an issue if you have questions or want to contribute!
