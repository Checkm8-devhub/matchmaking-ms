package com.checkm8.matchmaking.ms.api.v1.resources;

import java.util.concurrent.CompletableFuture;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import com.checkm8.matchmaking.ms.api.v1.dtos.KeycloakResponse;
import com.checkm8.matchmaking.ms.api.v1.dtos.UsersResponse;
import com.checkm8.matchmaking.ms.beans.MatchmakingBean;
import com.checkm8.matchmaking.ms.dtos.FutureConsumable;
import com.checkm8.matchmaking.ms.dtos.User;

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

    private Client httpClient_users;
    @ConfigProperty(name = "auth.users.ms.base-url")
    private String baseUrl_users;

    private Client httpClient_keycloak;
    @ConfigProperty(name = "keycloak.token-url")
    private String tokenUrl_keycloak;

    @ConfigProperty(name = "clientSecret")
    private String clientSecret;

    private volatile String jwt_access_token;

    @Inject
    private MatchmakingBean matchmakingBean;

    @Inject
    private JsonWebToken jwt;

    // for timer
    @Inject
    Vertx vertx;

    @PostConstruct
    private void init() {
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
        this.jwt_access_token = keycloakResponse.access_token;
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
            .header("Authorization", "Bearer " + this.jwt_access_token)
            .get();

        if (r.getStatus() == 401) {
            r.close();
            this.getJwtToken(); // refresh token
            // redo the request
            r = this.httpClient_users
                .target(this.baseUrl_users + "/users/" + userSubject)
                .request()
                .header("Authorization", "Bearer " + this.jwt_access_token)
                .get();
        }

        if (r.getStatus() != 200) {
            r.close();
            return Uni.createFrom().item(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
        }

        UsersResponse usersResponse = r.readEntity(UsersResponse.class);
        r.close();

        User user = new User();
        user.elo = usersResponse.elo;
        user.startingTimeMillis = System.currentTimeMillis();
        System.out.printf("%s: %d elo\n", userSubject, user.elo);
        // ---

        // try to find opponent immediatelly
        FutureConsumable consumable = matchmakingBean.makeMatch(user);
        if (consumable != null) {
            return Uni.createFrom().item(Response.ok(consumable).build());
        }

        // register as a waiter
        CompletableFuture<FutureConsumable> f = matchmakingBean.registerWaiter(userSubject, user);

        // long pool: wait up to 30s for the future to complete
        // ---
        // cleanUp after timeout:
        // - if the future not completed yet => complete the future with null and remove the future
        long timerId = vertx.setTimer(LONG_POOL_TIMEOUT_MS, tId -> {
            if (f.complete(null)) {
                matchmakingBean.removeWaiter(userSubject);
            }
        });
        // set callback on completion. Just cancel the timer because we don't need the cleanup
        f.whenComplete((newUci, err) -> {
            matchmakingBean.removeWaiter(userSubject);
            vertx.cancelTimer(timerId);
        });

        // on completion return consumable or empty
        return Uni.createFrom().completionStage(f)
            .onItem().transform(futureConsumable -> {
                if (futureConsumable == null) {
                    return Response.status(Response.Status.NO_CONTENT).build();
                }
                return Response.ok(futureConsumable).build();
            });
        // ---
    }
}
