import java.io.*;
import java.net.*;
import java.util.regex.*;
import java.sql.*;
import java.util.*;
import org.apache.commons.cli.*;

public class Crawler
{
    Connection connection;
    int urlID;
    int maxURL;
    String root;
    String domain;
    String curr;
    public Properties props;

    Crawler() {
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

    public String makeAbsoluteURL(String url, String parentURL) {
        System.out.println("Parent " + parentURL);
        System.out.println("  Child " + url);

        if (parentURL.contains(".html")) {
            parentURL = parentURL.substring(0, parentURL.length()-5);
            while (parentURL.charAt(parentURL.length()-1) != '/') {
                parentURL = parentURL.substring(0, parentURL.length()-1);
            }
        }

        if (url.indexOf(":") > 0) {
            return url;
        }

        if (url.charAt(0) == '\"') {
            url = url.substring(1, url.length()-1);
        }

        while (url.charAt(0) == '.') {
            url = url.substring(3, url.length());

            // remove last char incase it ended with a /
            parentURL = parentURL.substring(0, parentURL.length()-1);
            while (parentURL.charAt(parentURL.length()-1) != '/') {
                parentURL = parentURL.substring(0, parentURL.length()-1);
            }
            System.out.println("  Parent new " + parentURL);
            System.out.println("  Child new " + url);
        }

        if (url.charAt(0) == '/' && parentURL.charAt(parentURL.length()-1) == '/') {
            url = url.substring(1, url.length());
        }
        return parentURL + url;
    }

    public void fetchURL(String urlScanned) {
        try {
            URL url = new URL(urlScanned);
            // System.out.println("urlscanned="+urlScanned+" url.path="+url.getPath());

            // open reader for URL
            try {
                InputStreamReader in = new InputStreamReader(url.openStream());
                // read contents into string builder
                StringBuilder input = new StringBuilder();
                int ch;
                while ((ch = in.read()) != -1) {
                        input.append((char) ch);
                }

                // TODO: get description here
                // System.out.println(input.toString());

                // search for all occurrences of pattern
                String patternString = "<a\\s+href\\s*=\\s*(\"[^\"]*\"|[^\\s>]*)\\s*>";
                Pattern pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(input);

                while (matcher.find()) {
                    int start = matcher.start();
                    int end = matcher.end();
                    String match = input.substring(start, end);
                    String urlFound = this.makeAbsoluteURL(matcher.group(1), this.curr);
                    System.out.println("  Full URL " + urlFound);

                    // Check if it is already in the database
                    if (!urlInDB(urlFound) && !urlFound.contains("mailto:") && urlFound.contains(this.domain) && urlFound.contains("http")) {
                        insertURLInDB(urlFound);
                    }
                }
            } catch (FileNotFoundException exp) {
                System.out.println("Found dead link: " + url);
                return;
            }


        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void main(String[] args)
    {
        CommandLineParser parser = new GnuParser();
        Options options = new Options();
        Crawler crawler = new Crawler();

        options.addOption("u", false, "max URL");
        options.addOption("d", false, "domain");

        if (args.length < 1) {
            System.out.println("usage: [-u <maxurls>] [-d domain] url-list");
            return;
        }

        try {
            CommandLine line = parser.parse(options, args);
            String setMaxURL = line.getOptionValue("u");

            if (setMaxURL != null) {
                System.out.println(line.getOptionValue("u"));
                crawler.maxURL = Integer.parseInt(line.getOptionValue("u"));
                System.out.println("u set");
            } else {
                System.out.println("wtf");
            }
        } catch (ParseException exp) {
            System.out.println("Unexpected exception:" + exp.getMessage());
        }

        int nextURLID = 0;
        int nextURLIDScanned = 0;
        try {
            crawler.readProperties();
            crawler.createDB();
            while (crawler.curr != null && nextURLID < 50) {
                // remove weird /index urls
                if (crawler.curr.substring(crawler.curr.length()-5, crawler.curr.length()).equals("index")) {
                    crawler.curr = crawler.curr.substring(0, crawler.curr.length()-5);
                }
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

