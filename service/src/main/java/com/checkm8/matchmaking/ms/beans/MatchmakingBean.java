package com.checkm8.matchmaking.ms.beans;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.logging.Logger;

@ApplicationScoped
public class MatchmakingBean {

    private Logger log = Logger.getLogger(MatchmakingBean.class.getName());

    @PostConstruct
    private void init() {
        log.info("Bean initialized " + MatchmakingBean.class.getSimpleName());
    }
    @PreDestroy
    private void destroy() {
        log.info("Bean destroyed " + MatchmakingBean.class.getSimpleName());
    }
}
