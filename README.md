# Matchmaking Microservice

Handles matchmaking at ```/seeks```

## From nothing to playing
1. User makes a POST request on ```/seeks```
2. Matchmaking returns ```seek_id``` with which the user starts long polling on ```/seeks/{seek_id}/wait```
3. When Matchmaking finds a worthy opponent it tells the **Gameplay / Games** microservice to create a game. It gets a ```game_id``` and ```game_token``` authorization token.
4. Matchmaking returns both the ```game_id``` and ```game_token``` to the users (seperate tokens for white and black), with which they can make action to the game (through the **Gameplay / Games** microserivce)
