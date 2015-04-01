import static spark.Spark.*;

public class Search {
    public static void main(String[] args) {
        Results r = new Results();
        r.connectDB();
        staticFileLocation("/public");

        get("/search", (req, res) -> {
            String keywords = req.queryParams("keywords");
            System.out.println("GET keywords: " + keywords);
            r.querySearch(keywords);
            for (String d: r.desc) {
                System.out.println("desc: " + d);
            }
            for (String d: r.url) {
                System.out.println("url: " + d);
            }
            for (String d: r.image) {
                System.out.println("image: " + d);
            }
            return "stuff";
        });
    }
}
