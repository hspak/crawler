import java.io.*;
import java.net.*;
import java.util.regex.*;
import java.sql.*;
import java.util.*;

import spark.ModelAndView;
import spark.template.velocity.VelocityTemplateEngine;

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

        get("/wtf", (req, res) -> {
            return "wtf";
        });

        get("/test", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            model.put("hello", "Velocity World");
            model.put("person", new Person("Foobar"));

            // The wm files are located under the resources directory
            return new ModelAndView(model, "template/hello.wm");
        }, new VelocityTemplateEngine());
    }

    public static class Person {
        private String name;

        public Person(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
