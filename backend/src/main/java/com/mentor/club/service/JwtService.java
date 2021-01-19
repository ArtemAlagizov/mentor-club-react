package com.mentor.club.service;

import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.mentor.club.exception.InternalException;
import com.mentor.club.model.ExtendableResult;
import com.mentor.club.model.InternalResponse;
import com.mentor.club.model.PublicKeyResponse;
import com.mentor.club.model.authentication.AuthenticationResult;
import com.mentor.club.model.authentication.token.abstracts.AbstractJwtTokenFactory;
import com.mentor.club.model.authentication.token.abstracts.JwtToken;
import com.mentor.club.model.authentication.token.abstracts.JwtTokenWithDeviceId;
import com.mentor.club.model.authentication.token.concretes.AccessToken;
import com.mentor.club.model.authentication.token.concretes.RefreshToken;
import com.mentor.club.model.authentication.token.enums.JwtTokenLifetime;
import com.mentor.club.model.authentication.token.enums.JwtTokenType;
import com.mentor.club.model.authentication.token.factories.JwtTokenFactory;
import com.mentor.club.model.authentication.token.factories.JwtWithDeviceIdTokenFactory;
import com.mentor.club.model.user.User;
import com.mentor.club.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.mentor.club.model.error.HttpCallError.FAILED_TO_FIND_TOKEN;
import static com.mentor.club.model.error.HttpCallError.FAILED_TO_SAVE_TO_DB;
import static com.mentor.club.service.RsaService.USERNAME_CLAIM;

@Service
public class JwtService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtService.class);

    private static final String MESSAGE_CLAIM = "message";
    private static final String USER_ID_CLAIM = "user_id";

    private static final String ERROR_MESSAGE_INVALID_TOKEN = "Invalid token.";
    private static final String ERROR_MESSAGE_SIGNATURE_VERIFICATION = "Signature verification exception.";

    private static final String INFO_MESSAGE_VALID_JWT = "JWT is valid!";
    private static final String INFO_MESSAGE_NON_WHITELIST_JWT = "JWT is not whitelisted!";

    private IUserRepository userRepository;
    private IAccessTokenRepository accessTokenRepository;
    private IRefreshTokenRepository refreshTokenRepository;
    private RsaService rsaService;
    private AbstractJwtTokenFactory jwtTokenFactory;
    private AbstractJwtTokenFactory jwtWithDeviceIdTokenFactory;

    @Value("${backend.deployment.url}")
    private String backendDeploymentUrl;

    @Autowired
    public JwtService(IUserRepository userRepository,
                      IAccessTokenRepository accessTokenRepository,
                      IRefreshTokenRepository refreshTokenRepository,
                      RsaService rsaService,
                      JwtTokenFactory jwtTokenFactory,
                      JwtWithDeviceIdTokenFactory jwtWithDeviceIdTokenFactory) {
        this.userRepository = userRepository;
        this.accessTokenRepository = accessTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.rsaService = rsaService;
        this.jwtTokenFactory = jwtTokenFactory;
        this.jwtWithDeviceIdTokenFactory = jwtWithDeviceIdTokenFactory;
    }

    public ResponseEntity<Object> validateAccessToken(String authorization) {
        DecodedJWT decodedJWT = null;
        String errorMessage = "";

        try {
            String token = authorization.substring(authorization.lastIndexOf(" ") + 1);
            decodedJWT = rsaService.decodeToken(token);
        } catch (JWTDecodeException exception) {
            errorMessage = ERROR_MESSAGE_INVALID_TOKEN;
        } catch (SignatureVerificationException ex) {
            errorMessage = ERROR_MESSAGE_SIGNATURE_VERIFICATION;
        }

        InternalResponse authResponse = checkAccessToken(errorMessage, decodedJWT);

        if (authResponse.getStatus() == HttpStatus.OK) {
            return new ResponseEntity<>(authResponse.getJson(), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(authResponse.getJson(), HttpStatus.UNAUTHORIZED);
        }
    }

    private InternalResponse checkAccessToken(String errorMessage, DecodedJWT decodedJWT) {
        final InternalResponse internalResponse = new InternalResponse();
        final ExtendableResult resultJson = new ExtendableResult();

        if (decodedJWT == null) {
            LOGGER.error(errorMessage);
            resultJson.getProperties().put(MESSAGE_CLAIM, errorMessage);
            internalResponse.setStatus(HttpStatus.BAD_REQUEST);
        } else {
            if (isAccessTokenWhitelisted(decodedJWT)) {
                resultJson.getProperties().put(MESSAGE_CLAIM, INFO_MESSAGE_VALID_JWT);
                resultJson.getProperties().put(USER_ID_CLAIM, decodedJWT.getClaims().get(USERNAME_CLAIM).asString());

                internalResponse.setStatus(HttpStatus.OK);
            } else {
                resultJson.getProperties().put(MESSAGE_CLAIM, INFO_MESSAGE_NON_WHITELIST_JWT);
                internalResponse.setStatus(HttpStatus.UNAUTHORIZED);
            }
        }

        internalResponse.setJson(resultJson);

        return internalResponse;
    }

    Optional<JwtTokenWithDeviceId> getOptionalAccessToken(String authorization, UUID deviceId) {
        String token = authorization.substring(authorization.lastIndexOf(" ") + 1);

        return accessTokenRepository.findByTokenAndDeviceId(token, deviceId);
    }

    // TODO introduce type
    ResponseEntity<Object> logout(String authorization, UUID deviceId) {
        try {
            if (!areTokenAndDeviceIdValid(authorization, deviceId)) {
                LOGGER.error("Failed to logout user with deviceId " + deviceId + ". JWT is invalid");

                return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
            }

            Optional<JwtTokenWithDeviceId> optionalAccessToken = getOptionalAccessToken(authorization, deviceId);
            User user = optionalAccessToken.get().getUser();

            deleteAllJwtTokensForUser(user);

            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception exception) {
            LOGGER.error("Failed to logout user with deviceId " + deviceId + ". Error: " + exception.getMessage());

            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    Boolean areTokenAndDeviceIdValid(String authorization, UUID deviceId) {
        String token = authorization.substring(authorization.lastIndexOf(" ") + 1);
        Optional<JwtTokenWithDeviceId> optionalAccessToken = accessTokenRepository.findByTokenAndDeviceId(token, deviceId);
        boolean isTokenPresent = optionalAccessToken.isPresent();
        boolean isActionAllowed = validateAccessToken(authorization).getStatusCode().is2xxSuccessful();

        return isActionAllowed && isTokenPresent;
    }

    private boolean isAccessTokenWhitelisted(DecodedJWT decodedJWT) {
        String username = decodedJWT.getClaims().get(USERNAME_CLAIM).asString();

        try {
            Optional<User> optionalUser = userRepository.findUserByUsername(username);

            if (optionalUser.isPresent()) {
                List<AccessToken> userAccessTokens = accessTokenRepository.findByUserId(optionalUser.get().getId());

                boolean isTokenPresent = userAccessTokens
                        .stream()
                        .map(JwtToken::getToken)
                        .collect(Collectors.toList())
                        .contains(decodedJWT.getToken());
                boolean isTokenExpired = decodedJWT.getExpiresAt().before(Date.from(Instant.now()));

                if (isTokenPresent && isTokenExpired) {
                    removeAccessTokenIfExpired(decodedJWT);

                    return false;
                }

                return isTokenPresent;
            } else {
                LOGGER.error("Failed to check token validity, user with username " + username + " not found!");

                return false;
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to check token validity for user with username " + username + ". Error: " + exception.getMessage());

            return false;
        }
    }

    private void removeAccessTokenIfExpired(DecodedJWT decodedJWT) {
        try {
            Optional<AccessToken> optionalToken = accessTokenRepository.findByToken(decodedJWT.getToken());

            optionalToken.ifPresent(jwtToken -> accessTokenRepository.delete(jwtToken));
        } catch (Exception exception) {
            String username = decodedJWT.getClaims().get(USERNAME_CLAIM).asString();

            LOGGER.error("Failed to remove token for user with username " + username + ". Error: " + exception.getMessage());
        }
    }

    private void deleteJwtToken(IJwtTokenRepository repository, JwtToken jwtToken) {
        try {
            Optional<JwtToken> token = repository.findByToken(jwtToken.getToken());

            repository.delete(token.get());
        } catch (Exception exception) {
            LOGGER.error("Failed to remove " + jwtToken.getJwtTokenType().name() + " token: " + jwtToken.getToken() + "!");
        }
    }

    private void deleteJwtTokensForUserForDevice(User user, IJwtTokenWithDeviceIdRepository repository, UUID deviceId) {
        try {
            List<JwtToken> tokensOfUser = repository.findByUserAndDeviceId(user, deviceId);

            tokensOfUser.forEach(repository::delete);
        } catch (Exception exception) {
            LOGGER.error("Failed to remove all tokens for user with username " + user.getUsername() + " and deviceId " + deviceId + " !");
        }
    }

    private void deleteJwtTokensForUser(User user, IJwtTokenRepository repository) {
        try {
            List<JwtToken> tokensOfUser = repository.findByUserId(user.getId());

            tokensOfUser.forEach(repository::delete);
        } catch (Exception exception) {
            LOGGER.error("Failed to remove all tokens for user with username " + user.getUsername() + "!");
        }
    }

    void deleteAllJwtTokensForUserForDevice(User user, UUID deviceId) {
        this.deleteJwtTokensForUserForDevice(user, accessTokenRepository, deviceId);
        this.deleteJwtTokensForUserForDevice(user, refreshTokenRepository, deviceId);
    }

    private void deleteAllJwtTokensForUser(User user) {
        this.deleteJwtTokensForUser(user, accessTokenRepository);
        this.deleteJwtTokensForUser(user, refreshTokenRepository);
    }

    private AccessToken getAccessTokenFromAuthorizationString(String authorization) {
        String token = authorization.substring(authorization.lastIndexOf(" ") + 1);

        try {
            Optional<AccessToken> jwtToken = accessTokenRepository.findByToken(token);

            if (!jwtToken.isPresent()) {
                throw new InternalException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, FAILED_TO_FIND_TOKEN);
            }

            return jwtToken.get();
        } catch (Exception exception) {
            LOGGER.error("Failed to get access token from authorization string " + authorization + "!");

            throw new InternalException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, FAILED_TO_FIND_TOKEN);
        }
    }

    void setDeviceIdOnJwtToken(JwtTokenWithDeviceId jwtTokenWithDeviceId, UUID deviceId, IJwtTokenWithDeviceIdRepository jwtTokenWithDeviceIdRepository) {
        try {
            jwtTokenWithDeviceId.setDeviceId(deviceId);

            jwtTokenWithDeviceIdRepository.save(jwtTokenWithDeviceId);
        } catch (Exception exception) {
            LOGGER.error("Could not update refresh token with deviceId " + deviceId + " for refreshToken " + jwtTokenWithDeviceId.getToken() + "!");

            throw new InternalException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, FAILED_TO_SAVE_TO_DB, exception.getMessage());
        }
    }

    <T extends JwtToken> T createJwtToken(User user, Long tokenLifetime, IJwtTokenRepository repository, JwtTokenType jwtTokenType, Boolean isWithDeviceId) {
        try {
            JwtToken jwtToken = isWithDeviceId ? jwtWithDeviceIdTokenFactory.getJwtTokenWithDeviceId(jwtTokenType) : jwtTokenFactory.getJwtToken(jwtTokenType);

            List<String> userGroups = Arrays.asList("user"); // change in the future
            String jwtTokenString = rsaService.generateToken(user.getUsername(), userGroups, tokenLifetime);

            jwtToken.setToken(jwtTokenString);
            jwtToken.setUser(user);
            jwtToken.setExpirationDate(Date.from(Instant.now().plusSeconds(tokenLifetime)));

            repository.save(jwtToken);

            return (T) jwtToken;
        } catch (Exception exception) {
            LOGGER.error("Could not create token of type " + jwtTokenType.name() + " for user " + user.getUsername() + "!");

            throw new InternalException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, FAILED_TO_SAVE_TO_DB, exception.getMessage());
        }
    }

    private ResponseEntity<Object> handleTokenRefreshUnauthorizedFlow(String refreshTokenCookie, UUID deviceId) {
        try {
            Optional<JwtTokenWithDeviceId> optionalRefreshToken = refreshTokenRepository.findByTokenAndDeviceId(refreshTokenCookie, deviceId);

            if (!optionalRefreshToken.isPresent()) {
                Optional<RefreshToken> optionalRefreshTokenWithoutDeviceId = refreshTokenRepository.findByToken(refreshTokenCookie);

                optionalRefreshTokenWithoutDeviceId.ifPresent(refreshToken -> deleteJwtToken(refreshTokenRepository, refreshToken));

                return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
            }

            JwtToken refreshToken = optionalRefreshToken.get();

            if (refreshToken.isExpired()) {
                return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
            }

            Optional<User> optionalUser = userRepository.findById(refreshToken.getUser().getId());

            if (!optionalUser.isPresent()) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception exception) {
            LOGGER.error("Could not authorize with refresh token " + refreshTokenCookie + " and deviceId " + deviceId + "! Error: " + exception.getMessage());

            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
    }

    private ResponseEntity handleTokenRefreshAuthorizedFlow(String refreshTokenCookie, Optional<String> authorization, UUID deviceId, HttpServletResponse httpServletResponse) {
        try {
            Optional<JwtTokenWithDeviceId> optionalRefreshToken = refreshTokenRepository.findByTokenAndDeviceId(refreshTokenCookie, deviceId);
            Optional<User> optionalUser = userRepository.findById(optionalRefreshToken.get().getUser().getId());

            deleteJwtToken(refreshTokenRepository, optionalRefreshToken.get());

            JwtTokenWithDeviceId newRefreshToken = createJwtToken(optionalUser.get(), JwtTokenLifetime.REFRESH_TOKEN_LIFESPAN_IN_SECONDS.getLifetime(), refreshTokenRepository, JwtTokenType.REFRESH_TOKEN, true);
            Cookie cookieWithRefreshToken = createCookieWithRefreshToken(newRefreshToken.getToken());

            setDeviceIdOnJwtToken(newRefreshToken, deviceId, refreshTokenRepository);
            httpServletResponse.addCookie(cookieWithRefreshToken);
            addSameSiteCookieAttribute(httpServletResponse);

            AuthenticationResult result = new AuthenticationResult();
            User user = optionalUser.get();

            result.setUsername(user.getUsername());
            result.setThumbnailPhoto(user.getThumbnailBase64());
            result.setToken(createJwtToken(optionalUser.get(), JwtTokenLifetime.ACCESS_TOKEN_LIFESPAN_IN_SECONDS.getLifetime(), accessTokenRepository, JwtTokenType.ACCESS_TOKEN, true).getToken());
            result.setDisplayName(user.getName());
            result.setThumbnailPhoto(user.getThumbnailBase64());

            if (authorization.isPresent()) {
                try {
                    AccessToken accessToken = getAccessTokenFromAuthorizationString(authorization.get());

                    deleteJwtToken(accessTokenRepository, accessToken);
                } catch (Exception exception) {
                    LOGGER.error("Could not delete access token for refresh token with deviceId " + deviceId + " and refreshToken " + refreshTokenCookie + "!");
                }
            }

            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception exception) {
            LOGGER.error("Could not authorize with refresh token " + refreshTokenCookie + " and deviceId " + deviceId + "!Error: " + exception.getMessage());

            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
    }

    public ResponseEntity getNewAccessAndRefreshToken(String refreshTokenCookie, Optional<String> authorization, UUID deviceId, HttpServletResponse httpServletResponse) {
        ResponseEntity<Object> responseEntity = handleTokenRefreshUnauthorizedFlow(refreshTokenCookie, deviceId);

        if (!responseEntity.getStatusCode().is2xxSuccessful()) {
            return responseEntity;
        }

        return handleTokenRefreshAuthorizedFlow(refreshTokenCookie, authorization, deviceId, httpServletResponse);
    }

    Cookie createCookieWithRefreshToken(String refreshToken) {
        Cookie cookie = new Cookie("refreshToken", refreshToken);

        cookie.setHttpOnly(true);
        cookie.setPath("/web-auth/token/new-access-token");
        cookie.setMaxAge(Math.toIntExact(JwtTokenLifetime.REFRESH_TOKEN_LIFESPAN_IN_SECONDS.getLifetime()));
        cookie.setSecure(true); // TODO check if needed with httpd

        return cookie;
    }

    void addSameSiteCookieAttribute(HttpServletResponse response) {
        Collection<String> headers = response.getHeaders(HttpHeaders.SET_COOKIE);
        boolean firstHeader = true;

        for (String header : headers) {
            if (firstHeader) {
                response.setHeader(HttpHeaders.SET_COOKIE, String.format("%s; %s", header, "SameSite=Strict"));

                firstHeader = false;

                continue;
            }

            response.addHeader(HttpHeaders.SET_COOKIE, String.format("%s; %s", header, "SameSite=Strict"));
        }
    }

    public ResponseEntity<PublicKeyResponse> getPublicKey() {
        return new ResponseEntity<>(getPublicKeyResponse(), HttpStatus.OK);
    }

    private PublicKeyResponse getPublicKeyResponse() {

        final PublicKeyResponse publicKeyResponse = new PublicKeyResponse();

        // get public key from docker volume
        // publicKeyResponse.setPublic_key(getProperty("PUBLIC_KEY"));

        return publicKeyResponse;
    }
}
