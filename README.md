Gatling multi-project demo
==========================

This repository is organized as a Gradle multi-project build:

* `gatling-app`: the Gatling test application and simulations
* `realword-openapi`: placeholder module for the RealWorld OpenAPI contract/client code
* `gatling-core`: shared Gatling support code

The existing Gatling simulation and resources now live under `gatling-app/src/gatling`.

Common commands:

* `./gradlew build`
* `./gradlew :gatling-app:gatlingRun`
