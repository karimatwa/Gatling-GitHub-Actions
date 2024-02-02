package computerdatabase;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;
import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;

public class ComputerDatabaseSimulation extends Simulation {


        
        FeederBuilder<String> feeder = csv("search.csv").random();
        Properties properties = new Properties();
        
        int usersCount = Integer.parseInt(System.getProperty("users", "6000"));


        
        @Override
        public void before() {
                System.out.printf("Running test with %d users%n", usersCount);
        }


        
        ChainBuilder search = exec(http("Home").get("/"))
                        .pause(1)
                        .feed(feeder)
                        .exec(
                                http("Search")
                                        .get("/computers?f=#{searchCriterion}")
                                        .check(css("a:contains('#{searchComputerName}')", "href").saveAs("computerUrl")))
                        .pause(1)
                        .exec(
                                http("Select")
                                        .get("#{computerUrl}")
                                        .check(status().is(200)))
                        .pause(1);

        ChainBuilder browse =
                        // Note how we force the counter name, so we can reuse it
                        repeat(4, "i").on(
                                exec(
                                        http("Page #{i}")
                                        .get("/computers?p=#{i}"))
                                        .pause(1));

        // Note we should be using a feeder here
        // let's demonstrate how we can retry: let's make the request fail randomly and
        // retry a given
        // number of times

        ChainBuilder edit =
                        // let's try at max 2 times
                        tryMax(2)
                                .on(
                                        exec(
                                                http("Form").get("/computers/new"))
                                                .pause(1)
                                                .exec(
                                                http("Post")
                                                .post("/computers")
                                                .formParam("name",
                                                                "Beautiful Computer")
                                                .formParam("introduced",
                                                                "2012-05-30")
                                                .formParam("discontinued",
                                                                "")
                                                .formParam("company",
                                                                "37")
                                                .check(status().is(
                                                // request
                                                session -> 200 + ThreadLocalRandom.current().nextInt(2)))))

                                        .exitHereIfFailed();

        HttpProtocolBuilder httpProtocol = http.baseUrl("https://computer-database.gatling.io")
                        .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .acceptLanguageHeader("en-US,en;q=0.5")
                        .acceptEncodingHeader("gzip, deflate")
                        .userAgentHeader(
                                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0");

        ScenarioBuilder users = scenario("Users").exec(search
        // , browse
        );
        ScenarioBuilder admins = scenario("Admins").exec(search, browse, edit);
        Assertion assertion = global().responseTime().percentile(95.0).lt(250);
        {
                setUp(
                                users.injectOpen(rampUsers(usersCount).during(35))).assertions(assertion).protocols(httpProtocol);
        }
}
