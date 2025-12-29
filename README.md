# Matchmaking Microservice

Handles matchmaking at ```/seeks```

## From nothing to playing
1. Users starts long pooling with GET request on ```/seeks```
2. When Matchmaking finds a worthy opponent it tells the **Gameplay / Games** microservice to create a game. It gets a ```game_id``` and ```game_token``` authorization token.
3. Matchmaking returns both the ```game_id``` and ```game_token``` to the users (seperate tokens for white and black), with which they can make action to the game (through the **Gameplay / Games** microserivce)

## IDEA
- on GET request on ```/seeks```:
    - ms checks (that user is not already waiting, other checks...)
    - ms gets user elo (by making a request on auth.users.ms)
    - ms tries to match the user
        - if matches create a game and return ```game_id``` and ```game_token```
        - if not:
            - ms registers user as a waiter
            - request is waiting for the future to complete with a timeout of 30s
