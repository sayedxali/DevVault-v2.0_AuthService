package com.seyed.ali.authenticationservice.keycloak.service;

import com.seyed.ali.authenticationservice.keycloak.model.domain.KeycloakUser;
import com.seyed.ali.authenticationservice.keycloak.repository.KeycloakUserRepository;
import com.seyed.ali.authenticationservice.keycloak.service.interfaces.RolePersistenceService;
import com.seyed.ali.authenticationservice.keycloak.service.interfaces.UserOperationService;
import com.seyed.ali.authenticationservice.keycloak.service.interfaces.UserPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPersistenceServiceImpl implements UserPersistenceService {

    private final UserOperationService userOperationService;
    private final RolePersistenceService rolePersistenceService;
    private final KeycloakUserRepository keycloakUserRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    @Cacheable(
            key = "#jwt.subject",
            cacheNames = "user-cache"
    )
    public KeycloakUser handleUser(Jwt jwt) {
        // find the user in database
        String userId = jwt.getSubject();
        Optional<KeycloakUser> keycloakUser = this.userOperationService.retrieveUser(userId);

        // add `user` role as default roles
        this.userOperationService.assignDefaultRolesToUser(userId);

        if (keycloakUser.isPresent()) {
            log.info("User fetched from _DataBase_ and is cached. Checking user's roles to update... No further logs will be generated for this operation.");
            KeycloakUser user = keycloakUser.get();
            // update roles if necessary; if the user has new roles, assign them and update db
            this.rolePersistenceService.updateRolesIfNecessary(jwt, user);
            this.keycloakUserRepository.save(user);
            log.info("Found user updated. UserEmail: {}", user.getEmail());
            return user;
        }

        // user not present in db. create new user.
        log.info("User not found in cache or database. Creating new user in DB: {{}}", jwt.getClaim("email").toString());
        KeycloakUser newUser = this.userOperationService.createNewUser(jwt);
        log.info("User created and is cached. No further logs will be generated for this operation.");
        this.rolePersistenceService.updateRolesIfNecessary(jwt, newUser);
        this.keycloakUserRepository.save(newUser);
        log.info("User created successfully. UserEmail: {}", newUser.getEmail());
        return newUser;
    }

}
