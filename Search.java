import java.io.*;
import java.net.*;
import java.util.regex.*;
import java.sql.*;
import java.util.*;

import spark.ModelAndView;
import spark.template.velocity.VelocityTemplateEngine;

import static spark.Spark.*;

public class Search {
    public static class Link {
        private String url;
        private String desc;
        private String image;

        public Link(String url, String desc, String image) {
            this.url = url;
            this.desc = desc;
            this.image = image;
        }

        public String getUrl() {
            return url;
        }
        public String getDesc() {
            return desc;
        }
        public String getImage() {
            return image;
        }
    }

    public static void main(String[] args) {
        Results r = new Results();
        r.connectDB();
        staticFileLocation("/public");

        get("/search", (req, res) -> {
            String keywords = req.queryParams("keywords");
            r.querySearch(keywords);

            if (r.desc.size() != r.url.size() && r.url.size() != r.image.size()) {
                return null;
            }

            List<Link> links = new ArrayList<>();
            for (int i = 0; i < r.url.size(); i++) {
                links.add(new Link(r.url.get(i), r.desc.get(i), r.image.get(i)));
            }

            Map<String, Object> model = new HashMap<>();
            model.put("links", links);
            model.put("count", r.url.size());
            return new ModelAndView(model, "template/search.wm");
        }, new VelocityTemplateEngine());
    }
}
