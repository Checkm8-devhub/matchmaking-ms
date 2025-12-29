package com.checkm8.matchmaking.ms.api.v1.resources;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.checkm8.matchmaking.ms.beans.MatchmakingBean;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

@ApplicationScoped
@Path("seeks")
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

    @Inject
    private MatchmakingBean matchmakingBean;

    @PostConstruct
    private void init() {
        this.httpClient_gameplay = ClientBuilder.newClient();
        this.httpClient_users = ClientBuilder.newClient();
    }
}
