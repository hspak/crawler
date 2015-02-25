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
    public Properties props;

    Crawler() {
        urlID = 0;
        maxURL = 1000;
    }

    public void readProperties() throws IOException {
        props = new Properties();
        FileInputStream in = new FileInputStream("database.properties");
        props.load(in);
        in.close();
    }

    public void openConnection() throws SQLException, IOException
    {
        String drivers = props.getProperty("jdbc.drivers");
        if (drivers != null) System.setProperty("jdbc.drivers", drivers);
        String url = props.getProperty("jdbc.url");
        String username = props.getProperty("jdbc.username");
        String password = props.getProperty("jdbc.password");
        connection = DriverManager.getConnection( url, username, password );
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
            System.out.println("URL "+urlFound+" already in DB");
            return true;
        }

        System.out.println("URL "+urlFound+" not yet in DB");
        return false;
    }

    public void insertURLInDB(String url) throws SQLException, IOException {
        Statement stat = connection.createStatement();
        String query = "INSERT INTO URLS VALUES ('"+urlID+"','"+url+"','')";
        System.out.println("Executing "+query);
        stat.executeUpdate( query );
        urlID++;
    }

/*
    public String makeAbsoluteURL(String url, String parentURL) {
        if (url.indexOf(":")<0) {
            // the protocol part is already there.
            return url;
        }

        if (url.length > 0 && url.charAt(0) == '/') {
            // It starts with '/'. Add only host part.
            int posHost = url.indexOf("://");
            if (posHost <0) {
                return url;
            }
            int posAfterHist = url.indexOf("/", posHost+3);
            if (posAfterHist < 0) {
                posAfterHist = url.Length();
            }
            String hostPart = url.substring(0, posAfterHost);
            return hostPart + "/" + url;
        }

        // URL start with a char different than "/"
        int pos = parentURL.lastIndexOf("/");
        int posHost = parentURL.indexOf("://");
        if (posHost <0) {
            return url;
        }
    }
*/

    public void fetchURL(String urlScanned) {
        try {
            URL url = new URL(urlScanned);
            System.out.println("urlscanned="+urlScanned+" url.path="+url.getPath());

            // open reader for URL
            InputStreamReader in = new InputStreamReader(url.openStream());

            // read contents into string builder
            StringBuilder input = new StringBuilder();
            int ch;
            while ((ch = in.read()) != -1) {
                    input.append((char) ch);
            }

            // search for all occurrences of pattern
            String patternString = "<a\\s+href\\s*=\\s*(\"[^\"]*\"|[^\\s>]*)\\s*>";
            Pattern pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(input);

            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                String match = input.substring(start, end);
                String urlFound = matcher.group(1);
                System.out.println(urlFound);

                // Check if it is already in the database
                if (!urlInDB(urlFound)) {
                    insertURLInDB(urlFound);
                }
                System.out.println(match);
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

        // jank args parsing
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

        try {
            crawler.readProperties();
            String root = crawler.props.getProperty("crawler.root");
            crawler.createDB();
            crawler.fetchURL(root);
        } catch( Exception e) {
            e.printStackTrace();
        }
    }
}

