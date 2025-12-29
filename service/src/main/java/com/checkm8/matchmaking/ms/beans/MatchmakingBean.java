package com.checkm8.matchmaking.ms.beans;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;

import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.checkm8.matchmaking.ms.dtos.User;
import com.checkm8.matchmaking.ms.dtos.Waiter;
import com.checkm8.matchmaking.ms.dtos.FutureConsumable;
import com.checkm8.matchmaking.ms.dtos.GameplayResponse;

@ApplicationScoped
public class MatchmakingBean {

    private Client httpClient_gameplay;
    @ConfigProperty(name = "gameplay.games.ms.base-url", defaultValue = "http://localhost:8080/api/v1")
    private String baseUrl_gameplay;

    private Logger log = Logger.getLogger(MatchmakingBean.class.getName());

    @PostConstruct
    private void init() {
        log.info("Bean initialized " + MatchmakingBean.class.getSimpleName());
        this.httpClient_gameplay = ClientBuilder.newClient();
    }
    @PreDestroy
    private void destroy() {
        log.info("Bean destroyed " + MatchmakingBean.class.getSimpleName());
    }

    private final ConcurrentHashMap<String, Waiter> waiters =
        new ConcurrentHashMap<>();

    public CompletableFuture<FutureConsumable> registerWaiter(String sub, User user) {
        CompletableFuture<FutureConsumable> f = new CompletableFuture<>();
        Waiter waiter = new Waiter();
        waiter.user = user;
        waiter.future = f;

        Waiter existing = waiters.putIfAbsent(sub, waiter);
        return existing != null ? existing.future : f;
    }

    public void removeWaiter(String sub) {
        waiters.remove(sub);
    }

    private final static Integer MAX_ELO_DIFFERENCE = 100;
    /**
     * returns null on not able to match
     */
    public FutureConsumable makeMatch(User user) {

        var worthyOpponents = 
            this.waiters.entrySet().stream()
                .filter(e -> Math.abs(e.getValue().user.elo - user.elo) <= MAX_ELO_DIFFERENCE)
                .sorted((a, b) -> a.getValue().user.startingTimeMillis < b.getValue().user.startingTimeMillis ? -1 : 1)
                .toList();
        System.out.printf("number of worthyOpponents: %d\n", worthyOpponents.size());

        if (!worthyOpponents.isEmpty()) {
            var user2 = worthyOpponents.get(0);
            System.out.printf("worthyOpponent: key:[%s], elo:[%d]\n", user2.getKey(), user2.getValue().user.elo);
            if (this.waiters.remove(user2.getKey()) != null) { // remove it from waiters, so other threads cant match with the same one
                return makeMatch(user, user2);
            }
        }
        return null;
    }
    private FutureConsumable makeMatch(User user1, Entry<String, Waiter> user2) {
        
        Response r = this.httpClient_gameplay
            .target(this.baseUrl_gameplay + "/games")
            .request()
            .post(Entity.json(""));
        if (r.getStatus() != 201) {
            System.out.printf("status: [%d]\n", r.getStatus());
            // put the user2 back in the waiters list
            this.waiters.put(user2.getKey(), user2.getValue());
            r.close();
            return null;
        }
        GameplayResponse gameplayResponse = r.readEntity(GameplayResponse.class);
        r.close();
        System.out.printf("gameplayResponse: id:[%s], wtoken:[%s], btoken:[%s]\n", gameplayResponse.id, gameplayResponse.whiteToken, gameplayResponse.blackToken);

        // Make FutureConsumable and complete for user2 and return for user1
        FutureConsumable consumable_user2 = new FutureConsumable();
        consumable_user2.game_id = gameplayResponse.id;
        consumable_user2.game_token = gameplayResponse.whiteToken;
        user2.getValue().future.complete(consumable_user2);

        FutureConsumable consumable_user1 = new FutureConsumable();
        consumable_user1.game_id = gameplayResponse.id;
        consumable_user1.game_token = gameplayResponse.blackToken;
        return consumable_user1;
    }
}
