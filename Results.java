import java.io.*;
import java.net.*;
import java.util.regex.*;
import java.sql.*;
import java.util.*;

class Results
{
    public List<String> desc;
    public List<String> url;
    public List<String> image;
    Connection connection;
    public Properties props;

    Results() {
        desc = new ArrayList<>();
        url = new ArrayList<>();
        image = new ArrayList<>();
        connection = null;
    }

    public void readProperties() throws IOException {
        props = new Properties();
        FileInputStream in = new FileInputStream("database.properties");
        props.load(in);
        in.close();
    }

    public void connectDB() {
        try {
            readProperties();
            String drivers = props.getProperty("jdbc.drivers");
            if (drivers != null) System.setProperty("jdbc.drivers", drivers);
            String url = props.getProperty("jdbc.url");
            String username = props.getProperty("jdbc.username");
            String password = props.getProperty("jdbc.password");
            connection = DriverManager.getConnection(url, username, password);
        } catch (SQLException|IOException e) {
            e.printStackTrace();
        }
    }

    public List<Integer> setUrlidFromWords(String[] keywords) {
        List<Integer> urlid = null;
        try {
            urlid = new ArrayList<>();
            Statement stat = connection.createStatement();
            String query = "SELECT urlid FROM words WHERE word IN(";
            for (String keyword: keywords) {
                query += "'" + keyword + "', ";
            }
            query = query.substring(0, query.length()-2);
            query += ") GROUP BY urlid HAVING count(*)=" + keywords.length + ";";
            System.out.println(query);
            ResultSet result = stat.executeQuery(query);
            while (result.next()) {
                urlid.add(result.getInt("urlid"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return urlid;
    }

    public void getUrlidInfo(List<Integer> urlid) {
        if (urlid.size() == 0) {
            return;
        }

        try {
            Statement stat = connection.createStatement();
            String query = "SELECT * FROM urls WHERE urlid IN(";
            for (int i = 0; i < urlid.size(); i++) {
                query += urlid.get(i) + ", ";
            }
            query = query.substring(0, query.length()-2);
            query += ");";
            System.out.println(query);
            ResultSet result = stat.executeQuery(query);
            while (result.next()) {
                url.add(result.getString("url"));
                desc.add(result.getString("description"));
                image.add(result.getString("image"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void querySearch(String keywordsRaw) {
        keywordsRaw = keywordsRaw.toLowerCase();
        String[] keywords = keywordsRaw.split(" ");
        for (int i = 0; i < keywords.length; i++) {
            keywords[i] = keywords[i].replaceAll("''", "");
        }

        desc.clear();
        image.clear();
        url.clear();
        List<Integer> urlid = setUrlidFromWords(keywords);
        if (urlid != null) {
            getUrlidInfo(urlid);
        }
    }
}
