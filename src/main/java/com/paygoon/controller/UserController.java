package com.paygoon.controller;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paygoon.dto.UserSearchResponse;
import com.paygoon.model.AppUser;
import com.paygoon.repository.UserRepository;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired private UserRepository userRepository;

    @GetMapping("/search")
    public ResponseEntity<?> searchByNickname(@RequestParam("q") String nickname) {
        if (!StringUtils.hasText(nickname)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Nickname requerido");
        }

        Optional<AppUser> user = userRepository.findByNickname(nickname.trim());
        if (user.isEmpty() || !user.get().isVerified()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
        }

        AppUser foundUser = user.get();
        UserSearchResponse response = new UserSearchResponse(foundUser.getId(), foundUser.getEmail());
        return ResponseEntity.ok(response);
    }
}
