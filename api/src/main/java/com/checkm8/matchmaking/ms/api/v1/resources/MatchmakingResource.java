package com.checkm8.matchmaking.ms.api.v1.resources;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import com.checkm8.matchmaking.ms.api.v1.dtos.KeycloakResponse;
import com.checkm8.matchmaking.ms.api.v1.dtos.UsersResponse;
import com.checkm8.matchmaking.ms.beans.MatchmakingBean;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@ApplicationScoped
@Path("seeks")
@RolesAllowed({"user", "admin"})
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MatchmakingResource {

    @Context
    private UriInfo uriInfo;

    private Client httpClient_gameplay;
    @ConfigProperty(name = "gameplay.games.ms.base-url", defaultValue = "http://localhost:8080/api/v1")
    private String baseUrl_gameplay;

    private Client httpClient_users;
    @ConfigProperty(name = "auth.users.ms.base-url", defaultValue = "http://localhost:8082/api/v1")
    private String baseUrl_users;

    private Client httpClient_keycloak;
    @ConfigProperty(name = "keycloak.token-url", defaultValue = "http://localhost:8083/realms/auth/protocol/openid-connect/token")
    private String tokenUrl_keycloak;

    @ConfigProperty(name = "clientSecret")
    private String clientSecret;

    private volatile String jwt_access_token_users;

    @Inject
    private MatchmakingBean matchmakingBean;

    @Inject
    private JsonWebToken jwt;

    // for timer
    @Inject
    Vertx vertx;

    @PostConstruct
    private void init() {
        this.httpClient_gameplay = ClientBuilder.newClient();
        this.httpClient_users = ClientBuilder.newClient();
        this.httpClient_keycloak = ClientBuilder.newClient();
        this.getJwtToken();
    }
    private synchronized void getJwtToken() {

        // get matchmaking_client JWT token
        Form form = new Form()
            .param("grant_type", "client_credentials")
            .param("client_id", "matchmaking_client")
            .param("client_secret", clientSecret);
        KeycloakResponse keycloakResponse = this.httpClient_keycloak
            .target(this.tokenUrl_keycloak)
            .request()
            .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE),
                    KeycloakResponse.class);
        this.jwt_access_token_users = keycloakResponse.access_token;
    }

    // ****************************************
    //  GET
    // ****************************************
    private static final Long LONG_POOL_TIMEOUT_MS = 30000L;
    @GET
    @Blocking
    public Uni<Response> getOpponent() {
        
        String userSubject = this.jwt.getSubject();

        // get users elo
        // ---
        Response r = this.httpClient_users
            .target(this.baseUrl_users + "/users/" + userSubject)
            .request()
            .header("Authorization", "Bearer " + this.jwt_access_token_users)
            .get();

        if (r.getStatus() == 401) {
            this.getJwtToken(); // refresh token
            // redo the request
            r = this.httpClient_users
                .target(this.baseUrl_users + "/users/" + userSubject)
                .request()
                .header("Authorization", "Bearer " + this.jwt_access_token_users)
                .get();
        }

        if (r.getStatus() != 200) return Uni.createFrom().item(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());

        UsersResponse usersResponse = r.readEntity(UsersResponse.class);
        // ---

        return Uni.createFrom().item(Response.ok().build());

        // // register as a waiter
        // CompletableFuture<List<String>> f = gamesBean.registerWaiter(id);
        //
        // // if already behind, respond immediatelly. No need to long pool
        // List<String> list = game.getUciAsList();
        // if (since < 0) since = 0;
        // if (since > list.size()) since = list.size();
        // if (list.size() > since) {
        //     gamesBean.removeWaiter(id, f);
        //     return Uni.createFrom().item(Response.ok(list.subList(since, list.size())).build());
        // }
        //
        // // long pool: wait up to 30s for a new event
        // // ---
        // // cleanUp after timeout:
        // // - if the future not completed yet => complete the future with empty list and remove the future
        // long timerId = vertx.setTimer(EVENT_TIMEOUT_MS, tId -> {
        //     if (f.complete(List.of())) {
        //         gamesBean.removeWaiter(id, f);
        //     }
        // });
        // // set callback on completion. Just cancel the timer because we don't need the cleanup
        // f.whenComplete((newUci, err) -> {
        //     gamesBean.removeWaiter(id, f);
        //     vertx.cancelTimer(timerId);
        // });
        //
        // // on completion return newUci or empty
        // return Uni.createFrom().completionStage(f)
        //     .onItem().transform(newUci -> {
        //         if (newUci == null || newUci.isEmpty()) {
        //             return Response.status(Response.Status.NO_CONTENT).build();
        //         }
        //         return Response.ok(newUci).build();
        //     });
        // // ---
    }
}
