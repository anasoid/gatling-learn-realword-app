package org.anasoid.learn.gatling.app.api;

import com.realworld.gatling.generated.api.GeneratedUserAndAuthenticationApi;
import com.realworld.gatling.generated.model.LoginUserEnvelope;
import com.realworld.gatling.generated.model.UserEnvelope;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.http.HttpRequestActionBuilder;
import org.anasoid.learn.gatling.app.util.AuthenticationUtil;

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
 * Date :   8/14/26
 */
public class UserAndAuthenticationApi extends GeneratedUserAndAuthenticationApi {

  private static final UserAndAuthenticationApi instance = new UserAndAuthenticationApi();

  public static UserAndAuthenticationApi instance() {
    return instance;
  }

  @Override
  public ChainBuilder login(LoginUserEnvelope body) {
    return loginAndParseResponse(body)
        .exec(
            session -> {
              if (session.isFailed()) {
                return session.remove(AuthenticationUtil.AUTH_TOKEN);
              }
              UserEnvelope userEnvelope = getLastResponseFromLogin(session);
              if (userEnvelope == null || userEnvelope.getUser() == null) {
                throw new IllegalStateException(
                    "Expected login response after a successful login request");
              }
              removeLoginSavedResponses(session);
              return session.set(AuthenticationUtil.AUTH_TOKEN, userEnvelope.getUser().getToken());
            });
  }

  @Override
  protected HttpRequestActionBuilder builderAuthentication(HttpRequestActionBuilder builder) {
    return AuthenticationUtil.builderAuthentification(builder);
  }
}
