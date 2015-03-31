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
        maxURL = 100000;
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

    public void createDB() throws SQLException, IOException
    {
        this.openConnection();
        Statement stat = connection.createStatement();

        // Delete the table first if any
        try {
            stat.executeUpdate("DROP TABLE urls");
            stat.executeUpdate("DROP TABLE words");
        } catch (Exception e) {
        }

        // Create the table
        stat.executeUpdate("CREATE TABLE urls (urlid INT, url VARCHAR(512), description VARCHAR(400), image VARCHAR(512))");
        stat.executeUpdate("CREATE TABLE words (word VARCHAR(100), urlid INT)");
    }

    public void insertWordTable(int urlid, String desc) throws SQLException, IOException
    {
        Statement stat = connection.createStatement();
        String[] split = desc.split(" ");
        String query = "";
        for (String w: split) {
            if (w.length() <= 1) continue;
            query += "('" + w + "'," + "'" + Integer.toString(urlid) + "'), ";
        }
        query = query.substring(0, query.length()-2);
        query = "INSERT INTO words(word, urlid) values" + query + ";";
        stat.executeUpdate(query);
        this.urlID++;
    }

    public boolean urlInDB(String urlFound) throws SQLException, IOException {
        Statement stat = connection.createStatement();
        ResultSet result = stat.executeQuery( "SELECT * FROM urls WHERE url LIKE '"+urlFound+"'");

        if (result.next()) {
            return true;
        }
        return false;
    }

    public void deleteURLInDB(int urlid) throws SQLException, IOException {
       Statement stat = connection.createStatement();
        String query = "DELETE FROM urls WHERE urlid='" + urlid + "'";
        stat.executeUpdate(query);
        this.urlID++;
    }

    public void insertURLInDB(String url) throws SQLException, IOException {
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
                Document doc = Jsoup.connect(urlScanned).ignoreContentType(false).timeout(5000).get();
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // grab all links
                if (nextURLIDScanned < this.maxURL) {
                    Elements links = doc.select("a[href]");
                    for (Element link: links) {
                        String url = link.attr("abs:href");
                        try {
                            if (!urlInDB(url) && url.contains(this.domain) && url.contains("http") && !url.contains("#")) {
                                url = url.replace(" ", "%20");
                                url = url.replace("'", "\'");
                                insertURLInDB(url);
                                nextURLIDScanned++;
                                System.out.println("add " + nextURLIDScanned);
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
                    // image stuff
                    Element image = doc.select("img").first();
                    if (image != null) {
                        String imageURL = image.absUrl("src");
                        if (imageURL.equals("https://www.cs.purdue.edu/images/logo.svg")) {
                            if (doc.select("img").size() > 2) {
                                Element noLogo = doc.select("img").get(2);
                                if (image != null) {
                                    imageURL = noLogo.absUrl("src");
                                }
                            } else {
                                Element noLogo = doc.select("img").get(1);
                                if (image != null) {
                                    imageURL = noLogo.absUrl("src");
                                }
                            }
                        }

                        try {
                            this.insertImageInDB(nextURLID - 1, imageURL);
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }

                    // grab description
                    String title = doc.select("title").text();
                    String p = doc.select("p").text();
                    title = title.replaceAll("[^A-Za-z0-9 ]", "");
                    p = p.replaceAll("[^A-Za-z0-9 ]", "");
                    int titleLen = title.length();
                    int pLen = p.length();

                    if (titleLen > 45 && pLen > 10) {
                        titleLen = 45;
                    } else if (titleLen > 397) {
                        titleLen = 397;
                    }

                    if (pLen > 352) {
                        pLen = 352;
                    }

                    String save = title.substring(0, titleLen) + " " + p.substring(0, pLen);
                    try {
                        this.insertDescInDB(nextURLID - 1, save);
                        this.insertWordTable(nextURLID - 1, save);
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            } catch (org.jsoup.HttpStatusException|org.jsoup.UnsupportedMimeTypeException e) {
                try {
                    System.out.println("remove " + Integer.toString(nextURLID-1));
                    deleteURLInDB(nextURLID-1);
                } catch (SQLException ee) {
                    ee.printStackTrace();
                }
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
            // crawler.openConnection();
            // crawler.grabURLInDB(1562);
            // nextURLID = 1563;
            // nextURLIDScanned = 3446;
            while (nextURLID < nextURLIDScanned) {
                if (crawler.curr != null) {
                    System.out.println(nextURLID + " " + nextURLIDScanned + " " + crawler.curr);
                    crawler.fetchURL(crawler.curr);
                }
                crawler.grabURLInDB(nextURLID);
                nextURLID++;
            }
        } catch( Exception e) {
            e.printStackTrace();
        }
    }
}

