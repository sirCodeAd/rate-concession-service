package com.mortgage.rate_concession_service.security;

import com.mortgage.rate_concession_service.domain.AppUser;
import com.mortgage.rate_concession_service.domain.UserRole;
import com.mortgage.rate_concession_service.exception.UnauthorizedException;
import com.mortgage.rate_concession_service.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Simulated authentication stand-in for real OAuth2/JWT: every request must carry {@code
 * X-User-Id} and {@code X-User-Role} headers that match a seeded {@link AppUser}. On success the
 * resolved user is attached to the request as an attribute for {@link CurrentUserArgumentResolver}
 * to pick up; failures short-circuit with HTTP 401.
 */
@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    public static final String CURRENT_USER_ATTRIBUTE = "currentUser";

    private final AppUserRepository appUserRepository;

    public AuthenticationInterceptor(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = request.getHeader("X-User-Id");
        String roleHeader = request.getHeader("X-User-Role");

        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("Missing required header: X-User-Id");
        }
        if (roleHeader == null || roleHeader.isBlank()) {
            throw new UnauthorizedException("Missing required header: X-User-Role");
        }

        UserRole claimedRole;
        try {
            claimedRole = UserRole.valueOf(roleHeader);
        } catch (IllegalArgumentException ex) {
            throw new UnauthorizedException("Unknown X-User-Role: " + roleHeader);
        }

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Unknown X-User-Id: " + userId));

        if (user.getRole() != claimedRole) {
            throw new UnauthorizedException("X-User-Role does not match the actual role of user " + userId);
        }

        request.setAttribute(CURRENT_USER_ATTRIBUTE, user);
        return true;
    }
}
