import java.io.*;
import java.net.*;
import java.util.regex.*;
import java.sql.*;
import java.util.*;

import org.jsoup.Jsoup;
import org.jsoup.helper.Validate;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Crawler
{
    static int nextURLID;
    static int nextURLIDScanned;

    Connection connection;
    int urlID;
    int maxURL;
    String root;
    String domain;
    String curr;
    public Properties props;

    Crawler() {
        nextURLID = 0;
        nextURLIDScanned = 0;

        urlID = 0;
        maxURL = 1000;
        root = null;
        domain = null;
        curr = null;
    }

    public void readProperties() throws IOException {
        props = new Properties();
        FileInputStream in = new FileInputStream("database.properties");
        props.load(in);
        this.maxURL = Integer.parseInt(props.getProperty("crawler.maxurls"));
        this.root = props.getProperty("crawler.root");
        this.domain = props.getProperty("crawler.domain");
        this.curr = this.root;
        in.close();
    }

    public void openConnection() throws SQLException, IOException
    {
        String drivers = props.getProperty("jdbc.drivers");
        if (drivers != null) System.setProperty("jdbc.drivers", drivers);
        String url = props.getProperty("jdbc.url");
        String username = props.getProperty("jdbc.username");
        String password = props.getProperty("jdbc.password");
        connection = DriverManager.getConnection(url, username, password);
    }

    public void createDB() throws SQLException, IOException {
        openConnection();
        Statement stat = connection.createStatement();

        // Delete the table first if any
        try {
            stat.executeUpdate("DROP TABLE URLS");
        } catch (Exception e) {
        }

        // Create the table
        stat.executeUpdate("CREATE TABLE URLS (urlid INT, url VARCHAR(512), description VARCHAR(200))");
    }

    public boolean urlInDB(String urlFound) throws SQLException, IOException {
        Statement stat = connection.createStatement();
        ResultSet result = stat.executeQuery( "SELECT * FROM URLS WHERE url LIKE '"+urlFound+"'");

        if (result.next()) {
            // System.out.println("URL "+urlFound+" already in DB");
            return true;
        }

        // System.out.println("URL "+urlFound+" not yet in DB");
        return false;
    }

    public void insertURLInDB(String url) throws SQLException, IOException {
        Statement stat = connection.createStatement();
        if (url.charAt(0) == '\"') {
            url = url.substring(1, url.length()-1);
        }
        String query = "INSERT INTO URLS VALUES ('"+urlID+"','"+url+"','')";
        // System.out.println("Executing "+query);
        stat.executeUpdate( query );
        urlID++;
    }

    public void grabURLInDB(int URLID) throws SQLException, IOException {
        Statement stat = connection.createStatement();
        ResultSet result = stat.executeQuery("SELECT * FROM URLS WHERE URLID = " + Integer.toString(URLID));
        if (result.next()) {
            String res = result.getString("url");
            if (res.charAt(0) == '\"') {
                this.curr = res.substring(1, res.length()-1);
            } else {
                this.curr = res;
            }
        } else {
            this.curr = null;
        }
    }

    public void fetchURL(String urlScanned) {
        try {
            Document doc = Jsoup.connect(urlScanned).get();
            Elements links = doc.select("a[href]");
            for (Element link: links) {
                String url = link.attr("abs:href");
                try {
                    if (!urlInDB(url) && !url.contains("mailto") && url.contains(this.domain)) {
                        insertURLInDB(url);
                    }
                } catch (SQLException e) {
                    return;
                    // System.out.println(e.stackTrace);
                }
            }
        } catch (IOException e) {
            return;
            // System.out.println(e.stackTrace);
        }
    }

    public static void main(String[] args)
    {
        Crawler crawler = new Crawler();

        if (args.length < 1) {
            System.out.println("usage: [-u <maxurls>] [-d domain] url-list");
            return;
        }

        try {
            crawler.readProperties();
            crawler.createDB();
            while (crawler.curr != null && nextURLID < 50) {
                System.out.println("==> Curr = " + Integer.toString(nextURLID) + " " + crawler.curr);
                crawler.fetchURL(crawler.curr);
                crawler.grabURLInDB(nextURLID);
                nextURLID++;
            }
        } catch( Exception e) {
            e.printStackTrace();
        }
    }
}

