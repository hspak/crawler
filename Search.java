import java.io.*;
import java.net.*;
import java.util.regex.*;
import java.sql.*;
import java.util.*;

import spark.ModelAndView;
import spark.template.velocity.VelocityTemplateEngine;

import static spark.Spark.*;

public class Search {
    public static class Page {
        private String link;
        private String text;

        public Page(String link, String text) {
            this.link = link;
            this.text = text;
        }

        public String getLink() {
            return link;
        }
        public String getText() {
            return text;
        }
    }

    public static class Link {
        private String url;
        private String title;
        private String desc;
        private String image;

        public Link(String url, String title, String desc, String image) {
            this.url = url;
            this.title = title;
            this.desc = desc;
            this.image = image;
        }

        public String getUrl() {
            return url;
        }
        public String getTitle() {
            return title;
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
        port(9856);

        get("/search", (req, res) -> {
            String keywords = req.queryParams("keywords");
            int start = Integer.parseInt(req.queryParams("start"));
            r.querySearch(keywords, start);

            if (r.desc.size() != r.url.size() && r.url.size() != r.image.size()) {
                return null;
            }

            // generate data structure to send to template
            List<Link> links = new ArrayList<>();
            String imageURL = "";
            for (int i = 0; i < r.url.size(); i++) {
                if (r.image.get(i) != null) {
                    imageURL = r.image.get(i);
                } else {
                    imageURL = "http://upload.wikimedia.org/wikipedia/commons/9/91/Arabic_Question_mark_%28RTL%29.svg";
                }
                links.add(new Link(r.url.get(i), r.title.get(i), r.desc.get(i), imageURL));
            }

            // logic for pagination
            int origStart = start/10;
            List<Page> pages = new ArrayList<>();
            String pageLink = "";
            if (start > 50 && r.totalCount > 100) {
                start -= 50;
                origStart = 5;
            } else if (start < 100) {
                start = 0;
            }

            if (r.totalCount > 10 && start < r.totalCount) {
                for (int i = 0; i <= r.totalCount/10; i++) {
                    if (i == 10) break;
                    pageLink = "/search?start=" + Integer.toString(start) + "&keywords=" + keywords;
                    pages.add(new Page(pageLink, Integer.toString(start/10 + 1)));
                    start += 10;
                }
            }

            // send data to template
            Map<String, Object> model = new HashMap<>();
            model.put("links", links);
            model.put("count", r.totalCount);
            model.put("pages", pages);
            model.put("curr", origStart+1);
            return new ModelAndView(model, "template/search.wm");
        }, new VelocityTemplateEngine());
    }
}
