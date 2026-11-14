package lab8;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

public class Main {

    private static final String DEFAULT_URL = "jdbc:h2:mem:labdb";

    public static void main(String[] args) throws Exception {
        String url = args.length > 0 ? args[0] : DEFAULT_URL;
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            prepareMusic(conn);
            task1(conn);
            task2(conn);
            task3(conn);
            task4(conn);
            task5(conn);
            task6(conn);
            task7(conn);
            task8(conn);
        }
    }

    private static void prepareMusic(Connection conn) throws SQLException, IOException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS study");
        }
        runScript(conn, readResource("/music-create.sql"));
    }

    private static void runScript(Connection conn, String script) throws SQLException {
        try (Statement st = conn.createStatement()) {
            for (String sql : script.split(";")) {
                if (!sql.isBlank()) {
                    st.execute(sql);
                }
            }
        }
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = Main.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Не найден ресурс " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void title(String text) {
        System.out.println();
        System.out.println(text);
    }

    private static void task1(Connection conn) throws SQLException {
        title("№ 1. Все композиции");
        printMusic(conn, "SELECT id, name FROM study.music ORDER BY id");
    }

    private static void task2(Connection conn) throws SQLException {
        title("№ 2. Композиции без букв m и t");
        printMusic(conn, "SELECT id, name FROM study.music "
                + "WHERE LOWER(name) NOT LIKE '%m%' AND LOWER(name) NOT LIKE '%t%' ORDER BY id");
    }

    private static void task3(Connection conn) throws SQLException {
        title("№ 3. Добавление композиции");
        int newId;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(id), 0) + 1 FROM study.music")) {
            rs.next();
            newId = rs.getInt(1);
        }
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO study.music (id, name) VALUES (?, ?)")) {
            ps.setInt(1, newId);
            ps.setString(2, "Smoke on the Water");
            System.out.println("Добавлено строк: " + ps.executeUpdate());
        }
        printMusic(conn, "SELECT id, name FROM study.music ORDER BY id");
    }

    private static void printMusic(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            int count = 0;
            while (rs.next()) {
                System.out.println(rs.getInt("id") + ". " + rs.getString("name"));
                count++;
            }
            System.out.println("Строк: " + count);
        }
    }

    private static void task4(Connection conn) throws SQLException, IOException {
        title("№ 4. Загрузка данных из JSON");
        createBookTables(conn);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> visitors = (List<Map<String, Object>>) Json.parse(readResource("/books.json"));

        int newVisitors = 0;
        int newBooks = 0;
        for (Map<String, Object> v : visitors) {
            long visitorId = findVisitor(conn, v);
            if (visitorId < 0) {
                visitorId = insertVisitor(conn, v);
                newVisitors++;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> books = (List<Map<String, Object>>) v.get("favoriteBooks");
            for (Map<String, Object> b : books) {
                long bookId = findBook(conn, b);
                if (bookId < 0) {
                    bookId = insertBook(conn, b);
                    newBooks++;
                }
                linkVisitorBook(conn, visitorId, bookId);
            }
        }
        System.out.println("Посетителей в JSON: " + visitors.size() + ", добавлено новых: " + newVisitors);
        System.out.println("Новых уникальных книг: " + newBooks);
        System.out.println("В базе: посетителей = " + count(conn, "visitors") + ", книг = " + count(conn, "books"));
    }

    private static void createBookTables(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS visitors ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "name VARCHAR(100) NOT NULL, "
                    + "surname VARCHAR(100) NOT NULL, "
                    + "phone VARCHAR(30) NOT NULL, "
                    + "subscribed BOOLEAN NOT NULL, "
                    + "UNIQUE (name, surname, phone))");
            st.execute("CREATE TABLE IF NOT EXISTS books ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "name VARCHAR(255) NOT NULL, "
                    + "author VARCHAR(255) NOT NULL, "
                    + "publishing_year INT NOT NULL, "
                    + "isbn VARCHAR(20) NOT NULL UNIQUE, "
                    + "publisher VARCHAR(255))");
            st.execute("CREATE TABLE IF NOT EXISTS visitor_books ("
                    + "visitor_id INT NOT NULL, "
                    + "book_id INT NOT NULL, "
                    + "PRIMARY KEY (visitor_id, book_id), "
                    + "FOREIGN KEY (visitor_id) REFERENCES visitors(id), "
                    + "FOREIGN KEY (book_id) REFERENCES books(id))");
        }
    }

    private static long findVisitor(Connection conn, Map<String, Object> v) throws SQLException {
        String sql = "SELECT id FROM visitors WHERE name = ? AND surname = ? AND phone = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, (String) v.get("name"));
            ps.setString(2, (String) v.get("surname"));
            ps.setString(3, (String) v.get("phone"));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    private static long insertVisitor(Connection conn, Map<String, Object> v) throws SQLException {
        String sql = "INSERT INTO visitors (name, surname, phone, subscribed) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, (String) v.get("name"));
            ps.setString(2, (String) v.get("surname"));
            ps.setString(3, (String) v.get("phone"));
            ps.setBoolean(4, (Boolean) v.get("subscribed"));
            ps.executeUpdate();
            return generatedKey(ps);
        }
    }

    private static long findBook(Connection conn, Map<String, Object> b) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM books WHERE isbn = ?")) {
            ps.setString(1, (String) b.get("isbn"));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    private static long insertBook(Connection conn, Map<String, Object> b) throws SQLException {
        String sql = "INSERT INTO books (name, author, publishing_year, isbn, publisher) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, (String) b.get("name"));
            ps.setString(2, (String) b.get("author"));
            ps.setInt(3, ((Number) b.get("publishingYear")).intValue());
            ps.setString(4, (String) b.get("isbn"));
            ps.setString(5, (String) b.get("publisher"));
            ps.executeUpdate();
            return generatedKey(ps);
        }
    }

    private static void linkVisitorBook(Connection conn, long visitorId, long bookId) throws SQLException {
        String check = "SELECT 1 FROM visitor_books WHERE visitor_id = ? AND book_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(check)) {
            ps.setLong(1, visitorId);
            ps.setLong(2, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO visitor_books (visitor_id, book_id) VALUES (?, ?)")) {
            ps.setLong(1, visitorId);
            ps.setLong(2, bookId);
            ps.executeUpdate();
        }
    }

    private static long generatedKey(PreparedStatement ps) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            keys.next();
            return keys.getLong(1);
        }
    }

    private static int count(Connection conn, String table) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void task5(Connection conn) throws SQLException {
        title("№ 5. Книги по году издания");
        printBooks(conn, "SELECT publishing_year, name, author FROM books ORDER BY publishing_year, name");
    }

    private static void task6(Connection conn) throws SQLException {
        title("№ 6. Книги, изданные до 2000 года");
        printBooks(conn, "SELECT publishing_year, name, author FROM books "
                + "WHERE publishing_year < 2000 ORDER BY publishing_year, name");
    }

    private static void printBooks(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            int count = 0;
            while (rs.next()) {
                System.out.printf("%d | %s | %s%n",
                        rs.getInt("publishing_year"), rs.getString("name"), rs.getString("author"));
                count++;
            }
            System.out.println("Строк: " + count);
        }
    }

    private static void task7(Connection conn) throws SQLException {
        title("№ 7. Добавление посетителя и книг");

        Map<String, Object> me = Map.of(
                "name", "Nikita", "surname", "Shilov", "phone", "+7-676-767-67-67", "subscribed", true);
        long visitorId = findVisitor(conn, me);
        if (visitorId < 0) {
            visitorId = insertVisitor(conn, me);
        }

        List<Map<String, Object>> myBooks = List.of(
                Map.of("name", "Clean Code", "author", "Robert C. Martin", "publishingYear", 2008,
                        "isbn", "0132350882", "publisher", "Prentice Hall"),
                Map.of("name", "Effective Java", "author", "Joshua Bloch", "publishingYear", 2018,
                        "isbn", "0134685997", "publisher", "Addison-Wesley"),
                Map.of("name", "The Pragmatic Programmer", "author", "Andrew Hunt", "publishingYear", 1999,
                        "isbn", "020161622X", "publisher", "Addison-Wesley"));
        for (Map<String, Object> b : myBooks) {
            long bookId = findBook(conn, b);
            if (bookId < 0) {
                bookId = insertBook(conn, b);
            }
            linkVisitorBook(conn, visitorId, bookId);
        }

        String sql = "SELECT v.name, v.surname, v.phone, b.name AS book, b.author, b.publishing_year "
                + "FROM visitors v JOIN visitor_books vb ON vb.visitor_id = v.id "
                + "JOIN books b ON b.id = vb.book_id WHERE v.id = ? ORDER BY b.publishing_year";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, visitorId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean header = false;
                while (rs.next()) {
                    if (!header) {
                        System.out.println("Посетитель: " + rs.getString("name") + " "
                                + rs.getString("surname") + ", " + rs.getString("phone"));
                        System.out.println("Любимые книги:");
                        header = true;
                    }
                    System.out.printf("  %d | %s | %s%n",
                            rs.getInt("publishing_year"), rs.getString("book"), rs.getString("author"));
                }
            }
        }
    }

    private static void task8(Connection conn) throws SQLException {
        title("№ 8. Удаление таблиц");
        try (Statement st = conn.createStatement()) {
            st.execute("DROP TABLE visitor_books");
            st.execute("DROP TABLE visitors");
            st.execute("DROP TABLE books");
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                     + "WHERE TABLE_NAME IN ('VISITORS', 'BOOKS', 'VISITOR_BOOKS')")) {
            rs.next();
            System.out.println("Осталось таблиц посетителей/книг: " + rs.getInt(1));
        }
    }
}