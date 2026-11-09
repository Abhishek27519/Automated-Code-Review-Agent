import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SqlInjectionDemo {

    private final Connection connection;

    public SqlInjectionDemo(Connection connection) {
        this.connection = connection;
    }

    public List<String> findEmailsByName(String name) throws SQLException {
        String sql = "SELECT email FROM users WHERE name = '" + name + "'";
        List<String> emails = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                emails.add(rs.getString("email"));
            }
        }
        return emails;
    }

    public void deleteUser(String id) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM users WHERE id = " + id);
        }
    }
}