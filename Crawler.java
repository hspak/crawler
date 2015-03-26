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
        nextURLIDScanned++;
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
            //
        }

        // Create the table
        stat.executeUpdate("CREATE TABLE URLS (urlid INT, url VARCHAR(512), description VARCHAR(200), image VARCHAR(512))");
    }

    public boolean urlInDB(String urlFound) throws SQLException, IOException {
        Statement stat = connection.createStatement();
        ResultSet result = stat.executeQuery( "SELECT * FROM URLS WHERE url LIKE '"+urlFound+"'");

        if (result.next()) {
            return true;
        }
        return false;
    }

    public void insertURLInDB(String url) throws SQLException, IOException {
        url = url.replace("'", "\'");

        Statement stat = connection.createStatement();
        String query = "INSERT INTO urls(urlid, url) VALUES ('" + urlID + "','" + url + "');";
        stat.executeUpdate(query);
        this.urlID++;
    }

    public void insertImageInDB(int urlid, String imageSrc) throws SQLException, IOException {
        Statement stat = connection.createStatement();
        String query = "UPDATE urls SET image='" + imageSrc + "' WHERE urlid='" + urlid + "';";
        stat.executeUpdate(query);
    }

    public void insertDescInDB(int urlid, String desc) throws SQLException, IOException {
        Statement stat = connection.createStatement();
        String query = "UPDATE urls SET description='" + desc + "' WHERE urlid='" + urlid + "';";
        stat.executeUpdate(query);
    }

    public void grabURLInDB(int URLID) throws SQLException, IOException {
        Statement stat = connection.createStatement();
        ResultSet result = stat.executeQuery("SELECT url FROM urls WHERE urlid = " + Integer.toString(URLID));
        if (result.next()) {
            this.curr = result.getString("url");
        } else {
            this.curr = null;
        }
    }

    public void fetchURL(String urlScanned) {
        try {
            try {
                Document doc = Jsoup.connect(urlScanned).ignoreContentType(true).get();

                // grab all links
                if (nextURLIDScanned < this.maxURL) {
                    Elements links = doc.select("a[href*=http]");
                    for (Element link: links) {
                        String url = link.attr("abs:href");
                        try {
                            if (!urlInDB(url) && url.contains(this.domain)) {
                                url = url.replace(" ", "%20");
                                insertURLInDB(url);
                                nextURLIDScanned++;
                                if (nextURLIDScanned > this.maxURL) {
                                    break;
                                }
                            }
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }
                }

                if (nextURLID != 0) {
                    // grab first image
                    Element image = doc.select("img").first();
                    if (image != null) {
                        String imageURL = image.absUrl("src");
                        try {
                            this.insertImageInDB(nextURLID - 1, imageURL);
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    } else {
                        // TODO: maybe log
                    }

                    // grab description
                    String title = doc.select("title").text();
                    String body = doc.select("body").text();
                    int titleLen = title.length();
                    int bodyLen = body.length();
                    if (titleLen > 45) {
                        titleLen = 45;
                    }
                    if (bodyLen > 150) {
                        bodyLen = 150;
                    }

                    String save = title.substring(0, titleLen) + ": " + body.substring(0, bodyLen);
                    try {
                        this.insertDescInDB(nextURLID - 1, save);
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            } catch (org.jsoup.HttpStatusException e) {
                System.out.println("dead link: " + urlScanned);
                // TODO: log
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args)
    {
        Crawler crawler = new Crawler();
        try {
            crawler.readProperties();
            crawler.createDB();
            while (crawler.curr != null && nextURLID < nextURLIDScanned) {
                crawler.fetchURL(crawler.curr);
                crawler.grabURLInDB(nextURLID);
                nextURLID++;
            }
        } catch( Exception e) {
            e.printStackTrace();
        }
    }
}

