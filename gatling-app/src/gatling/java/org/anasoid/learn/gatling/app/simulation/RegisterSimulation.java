package org.anasoid.learn.gatling.app.simulation;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.http;

import com.realworld.gatling.generated.api.UserAndAuthenticationApi;
import com.realworld.gatling.generated.model.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.util.ArrayList;
import java.util.List;

public class RegisterSimulation extends AbstractRealWorldSimulation {

  private static final double TARGET_RPS = 1.0;

  /** Fallback values used when no external config file is present. */

  // ── Sample body factories — replace values with realistic test data as needed ──
  private static CreateUserRequest sampleCreateUserBody() {
    return new CreateUserRequest()
        .setUser(
            new NewUser()
                .setUsername("sample_username")
                .setEmail("sample_email")
                .setPassword("sample_password"));
  }

  public RegisterSimulation() {

    int durationSeconds = 10;
    int rampUpSeconds = 10;
    int rampDownSeconds = 10;

    HttpProtocolBuilder httpProtocol =getHttpProtocolBuilder();

    List<PopulationBuilder> scenarioBuilders = new ArrayList<>();

    ScenarioBuilder scncreateUser =
        scenario("createUserSimulation")
            .exec(UserAndAuthenticationApi.createUser(sampleCreateUserBody()));

    scenarioBuilders.add(
        scncreateUser.injectOpen(
            rampUsersPerSec(1.0).to(TARGET_RPS).during(rampUpSeconds),
            constantUsersPerSec(TARGET_RPS).during(durationSeconds),
            rampUsersPerSec(TARGET_RPS).to(1.0).during(rampDownSeconds)));

    ScenarioBuilder scngetCurrentUser =
        scenario("getCurrentUserSimulation").exec(UserAndAuthenticationApi.getCurrentUser());

    scenarioBuilders.add(
        scngetCurrentUser.injectOpen(
            rampUsersPerSec(1.0).to(TARGET_RPS).during(rampUpSeconds),
            constantUsersPerSec(TARGET_RPS).during(durationSeconds),
            rampUsersPerSec(TARGET_RPS).to(1.0).during(rampDownSeconds)));

    setUp(scenarioBuilders).protocols(httpProtocol);
  }
}
