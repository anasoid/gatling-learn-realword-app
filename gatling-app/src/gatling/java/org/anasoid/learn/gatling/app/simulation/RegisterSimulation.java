package org.anasoid.learn.gatling.app.simulation;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import com.realworld.gatling.generated.api.UserAndAuthenticationApi;
import com.realworld.gatling.generated.model.CreateUserRequest;
import com.realworld.gatling.generated.model.LoginRequest;
import com.realworld.gatling.generated.model.LoginUser;
import com.realworld.gatling.generated.model.NewUser;
import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Session;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class RegisterSimulation extends AbstractRealWorldSimulation {
  private static final Logger LOGGER = Logger.getLogger(RegisterSimulation.class.getName());

  private static final int USERS_COUNT = 1;
  private static final String PASSWORD = "pass";
  private static final Path LOGIN_USERS_CSV_FILE =
      Path.of("gatling-app", "build", "register", "logged-in-users.csv");

  public RegisterSimulation() {
    List<Map<String, Object>> users = buildUsers();
    resetLoginUsersCsv();
    HttpProtocolBuilder httpProtocol = getHttpProtocolBuilder();

    List<PopulationBuilder> scenarioBuilders = new ArrayList<>();

    ScenarioBuilder registerAndCheckCurrentUser =
        scenario("registerAndCheckCurrentUser")
            .feed(users.iterator())
            .exec(
                UserAndAuthenticationApi.createUserRequest(createUserRequestWithSessionValues())
                    .check(status().is(201))
                    .check(jsonPath("$.user.username").isEL("#{username}"))
                    .check(jsonPath("$.user.email").isEL("#{email}")))
            .exitHereIfFailed()
            .exec(
                UserAndAuthenticationApi.loginRequest(loginRequestWithSessionValues())
                    .check(status().is(200))
                    .check(jsonPath("$.user.token").saveAs("token")))
            .exitHereIfFailed()
            .exec(RegisterSimulation::appendLoggedInUserCsv)
            .exec(
                UserAndAuthenticationApi.getCurrentUserRequest()
                    .header("Authorization", "Token #{token}")
                    .check(status().is(200))
                    .check(jsonPath("$.user.username").isEL("#{username}"))
                    .check(jsonPath("$.user.email").isEL("#{email}")));

    scenarioBuilders.add(registerAndCheckCurrentUser.injectOpen(atOnceUsers(USERS_COUNT)));

    setUp(scenarioBuilders).protocols(httpProtocol);
  }

  private static CreateUserRequest createUserRequestWithSessionValues() {
    return new CreateUserRequest()
        .setUser(
            new NewUser()
                .setUsername("#{username}")
                .setEmail("#{email}")
                .setPassword("#{password}"));
  }

  private static LoginRequest loginRequestWithSessionValues() {
    return new LoginRequest().setUser(new LoginUser().setEmail("#{email}").setPassword("#{password}"));
  }

  private static List<Map<String, Object>> buildUsers() {
    List<Map<String, Object>> users = new ArrayList<>(USERS_COUNT);
    for (int i = 1; i <= USERS_COUNT; i++) {
      String username = "user" + i;
      Map<String, Object> user = new HashMap<>();
      user.put("username", username);
      user.put("email", username + "@example.com");
      user.put("password", PASSWORD);
      users.add(user);
    }
    return users;
  }

  private static void resetLoginUsersCsv() {
    try {
      Files.createDirectories(LOGIN_USERS_CSV_FILE.getParent());
      Files.deleteIfExists(LOGIN_USERS_CSV_FILE);
      LOGGER.info("Logged-in users CSV path: " + LOGIN_USERS_CSV_FILE.toAbsolutePath().normalize());
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to initialize login users CSV: " + LOGIN_USERS_CSV_FILE, ex);
    }
  }

  private static Session appendLoggedInUserCsv(Session session) {
    try {
      appendLoginUsersRow(
          session.getString("username"), session.getString("email"), session.getString("password"));
      return session;
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to append login users CSV: " + LOGIN_USERS_CSV_FILE, ex);
    }
  }

  private static synchronized void appendLoginUsersRow(String username, String email, String password)
      throws IOException {
    if (Files.notExists(LOGIN_USERS_CSV_FILE)) {
      Files.writeString(
          LOGIN_USERS_CSV_FILE,
          "username,email,password\n",
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE);
    }
    Files.writeString(
        LOGIN_USERS_CSV_FILE,
        username + "," + email + "," + password + "\n",
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND);
  }
}
