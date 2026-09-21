package com.mortgage.rate_concession_service.security;

import com.mortgage.rate_concession_service.domain.AppUser;
import com.mortgage.rate_concession_service.exception.UnauthorizedException;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolves {@link CurrentUser}-annotated controller parameters from the request attribute set by {@link AuthenticationInterceptor}. */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && parameter.getParameterType().equals(AppUser.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        AppUser user = (AppUser) webRequest.getAttribute(
                AuthenticationInterceptor.CURRENT_USER_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);
        if (user == null) {
            throw new UnauthorizedException("No authenticated user resolved for this request");
        }
        return user;
    }
}
