import java.io.*;
import java.net.*;
import java.util.regex.*;
import java.sql.*;
import java.util.*;

class Results
{
    public List<String> title;
    public List<String> desc;
    public List<String> url;
    public List<String> image;
    public List<Integer> urlid;
    Connection connection;
    public Properties props;
    public int totalCount;

    Results() {
        title = new ArrayList<>();
        desc = new ArrayList<>();
        url = new ArrayList<>();
        image = new ArrayList<>();
        urlid = new ArrayList<>();
        totalCount = 0;
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
                String[] split = result.getString("description").split("\\|");
                url.add(result.getString("url"));
                desc.add(split[0] + "...");
                if (split.length > 1) {
                    title.add(split[1]);
                } else {
                    title.add("???");
                }
                image.add(result.getString("image"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void resetLists() {
        title.clear();
        desc.clear();
        image.clear();
        url.clear();
        urlid.clear();
        totalCount = 0;
    }

    public void querySearch(String keywordsRaw, int start) {
        keywordsRaw = keywordsRaw.toLowerCase();
        String[] keywords = keywordsRaw.split(" ");
        for (int i = 0; i < keywords.length; i++) {
            keywords[i] = keywords[i].replaceAll("'", "");
        }

        resetLists();
        urlid = setUrlidFromWords(keywords);
        if (urlid != null) {
            totalCount = urlid.size();
            if (totalCount > 10 && start < totalCount) {
                if (start+10 < totalCount) {
                    urlid = urlid.subList(start, start+10);
                } else {
                    urlid = urlid.subList(start, totalCount);
                }
            }
            getUrlidInfo(urlid);
        }
    }
}
