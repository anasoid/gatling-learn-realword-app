Gatling multi-project demo
==========================

This repository is organized as a Gradle multi-project build:

* `gatling-app`: the Gatling test application and simulations
* `realword-openapi`: the RealWorld OpenAPI contract and Gatling code generation
* `gatling-core`: shared Gatling support code

The existing Gatling simulation and resources now live under `gatling-app/src/gatling`.
Generated Gatling Scala simulations and resources are written to `realword-openapi/build/generated/scala-gatling`.

Common commands:

* `./gradlew build`
* `./gradlew :realword-openapi:validateRealworldOpenApi`
* `./gradlew :realword-openapi:generateRealworldGatling`
* `./gradlew :gatling-app:gatlingRun`
* `./gradlew :gatling-app:gatlingRun --simulation com.realworld.gatling.api.UserAndAuthenticationApiSimulation`
