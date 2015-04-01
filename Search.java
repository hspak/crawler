import static spark.Spark.*;

public class Search {
    public static void main(String[] args) {
        Results r = new Results();
        staticFileLocation("/public");

        get("/search", (req, res) -> {
            String keywords = req.queryParams("keywords");
            System.out.println("GET keywords: " + keywords);
            return keywords;
        });
    }
}
