package org.anasoid.learn.gatling.app.api;

import com.realworld.gatling.generated.api.GeneratedArticlesApi;
import com.realworld.gatling.generated.model.ArticlesEnvelope;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Session;
import io.gatling.javaapi.http.HttpRequestActionBuilder;
import java.util.HashSet;
import java.util.Set;
import org.anasoid.learn.gatling.app.bean.utils.ArticlePagination;
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
 * Date :   8/19/26
 */
public class ArticlesApi extends GeneratedArticlesApi {

  public static final String ARTICLES_NEXT_PAGE = "ARTICLES_NEXT_PAGE";
  public static final String ARTICLES_ID = "ARTICLES_ID";
  private static final ArticlesApi instance = new ArticlesApi();

  public static ArticlesApi instance() {
    return instance;
  }

  @Override
  protected HttpRequestActionBuilder builderAuthentication(HttpRequestActionBuilder builder) {
    return AuthenticationUtil.builderAuthentification(builder);
  }

  @Override
  public ChainBuilder getArticles(
      String tag, String author, String favorited, Integer offset, Integer limit) {
    return getArticlesAndParseResponse(tag, author, favorited, offset, limit)
        .exec(
            session -> {
              if (session.isFailed()) {
                return session.removeAll(AuthenticationUtil.AUTH_TOKEN, ARTICLES_NEXT_PAGE);
              }
              Session newSession = initArticles(session);
              Set<String> articlesId = session.getSet(ARTICLES_ID);
              ArticlesEnvelope articlesEnvelope = getLastResponseFromGetArticles(session);
              if (articlesEnvelope == null || articlesEnvelope.getArticles() == null) {
                throw new IllegalStateException(
                    "Expected login response after a successful login request");
              }
              removeGetArticleSavedResponses(session);
              articlesEnvelope.getArticles().forEach(a -> articlesId.add(a.getSlug()));
              if (articlesEnvelope.getArticlesCount() > offset) {
                newSession.set(
                    ARTICLES_NEXT_PAGE,
                    ArticlePagination.builder()
                        .tag(tag)
                        .author(author)
                        .favorited(favorited)
                        .offset(offset + limit)
                        .limit(limit)
                        .build());
              } else {
                newSession = newSession.remove(ARTICLES_NEXT_PAGE);
              }
              return newSession.set(ARTICLES_ID, articlesId);
            });
  }

  public Boolean isArticlesLoaded(Session session) {
    Set<String> articlesId = session.getSet(ARTICLES_ID);
    return (articlesId != null && !articlesId.isEmpty());
  }

  @Override
  public ChainBuilder deleteArticle(String slug) {
    return super.deleteArticle(slug)
        .exec(
            session -> {
              Set<String> articlesId = session.getSet(ARTICLES_ID);
              if (articlesId != null && !articlesId.isEmpty()) {
                articlesId.remove(slug);
              }
              return session;
            });
  }

  private Session initArticles(Session session) {
    Set<String> articlesId = session.get(ARTICLES_ID);
    if (articlesId == null) {
      articlesId = new HashSet<>();
      return session.set(ARTICLES_ID, articlesId);
    }
    return session;
  }
}
