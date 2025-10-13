package com.agent.financial_advisor.services;

import com.agent.financial_advisor.model.User;
import com.agent.financial_advisor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {
    @Autowired
    UserRepository userRepository;

    @Transactional
    public User getOrCreateUser(OAuth2User oauth2User, String accessToken, String refreshToken) {
        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");

        User user = userRepository.findByEmail(email)
                .orElse(new User());

        user.setEmail(email);
        user.setName(name);
        user.setGoogleAccessToken(accessToken);
        user.setGoogleRefreshToken(refreshToken);
        user.setGoogleTokenExpiry(LocalDateTime.now().plusHours(1));

        return userRepository.save(user);
    }

    @Transactional
    public void updateHubspotTokens(User user, String accessToken, String refreshToken) {
        user.setHubspotAccessToken(accessToken);
        user.setHubspotRefreshToken(refreshToken);
        user.setHubspotTokenExpiry(LocalDateTime.now().plusHours(6));
        userRepository.save(user);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }
}
