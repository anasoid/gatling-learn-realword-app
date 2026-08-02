package org.anasoid.learn.gatling.app.simulation;

import io.gatling.javaapi.http.HttpProtocolBuilder;
import org.anasoid.learn.gatling.core.simulation.AbstractSimulation;

import static io.gatling.javaapi.http.HttpDsl.http;

/*
 * Copyright 2023-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * @author : anasoid
 * Date :   8/2/26
 */ public class AbstractRealWorldSimulation extends AbstractSimulation {

    public AbstractRealWorldSimulation() {
        super();
        int durationSeconds = getConfig().getInt("performance.durationSeconds");
        int rampUpSeconds = getConfig().getInt("performance.rampUpSeconds");
        int rampDownSeconds = getConfig().getInt("performance.rampDownSeconds");
        String authentication = getConfig().getString("performance.authorizationHeader");
        String acceptHeader = getConfig().getString("performance.acceptType");
        String contentTypeHeader = getConfig().getString("performance.contentType");


    }

    HttpProtocolBuilder getHttpProtocolBuilder(){
        return  http.baseUrl(getBaseUrl())
                .doNotTrackHeader("1")
                .acceptLanguageHeader("en-US,en;q=0.5")
                .acceptEncodingHeader("gzip, deflate")
                .userAgentHeader("Mozilla/5.0 (Windows NT 5.1; rv:31.0) Gecko/20100101 Firefox/31.0")
                .acceptHeader("application/json")
                .contentTypeHeader("application/json");
    }
}
