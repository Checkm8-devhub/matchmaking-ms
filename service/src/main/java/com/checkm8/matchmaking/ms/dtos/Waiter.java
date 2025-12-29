package com.checkm8.matchmaking.ms.dtos;

import java.util.concurrent.CompletableFuture;

public class Waiter {
    public User user;
    public CompletableFuture<FutureConsumable> future;
}
