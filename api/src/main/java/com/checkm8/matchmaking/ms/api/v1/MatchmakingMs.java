package com.checkm8.matchmaking.ms.api.v1;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import jakarta.ws.rs.core.Application;

@OpenAPIDefinition(
  info = @Info(
    title = "Matchmaking MS API",
    version = "v1",
    description = "Endpoints for seeking an opponent (matchmaking)."
  ),
  security = @SecurityRequirement(name = "bearerAuth")
)
public class MatchmakingMs extends Application {}
