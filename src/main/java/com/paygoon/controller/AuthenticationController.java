package com.paygoon.controller;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.paygoon.dto.AuthRequest;
import com.paygoon.dto.AuthResponse;
import com.paygoon.dto.LoginResponse;
import com.paygoon.dto.UserProfileResponse;
import com.paygoon.dto.UserRegisterRequest;
import com.paygoon.model.AppUser;
import com.paygoon.repository.UserRepository;
import com.paygoon.security.JwtUtil;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        try {
                authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
                );

                AppUser user = userRepository.findByEmail(request.email())
                            .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));


                String token = jwtUtil.generateToken(user);

                LoginResponse response = new LoginResponse(
                    0,
                    token,
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getNickname(),
                    user.getRol(),
                    user.isPremium(),
                    user.isVerified()
                );
                return ResponseEntity.ok(response);

        } catch (BadCredentialsException | UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse("Usuario o contraseña erróneos", -1));
        }
    }

    
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody UserRegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            return ResponseEntity.badRequest().body("El email ya está registrado");
        }

        AppUser user = new AppUser();
        user.setEmail(request.email());
        user.setName(request.name());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setRol(request.rol());
        user.setVerified(false);   // puedes activarlo después con verificación por correo
        user.setPremium(false);

        userRepository.save(user);

        return ResponseEntity.ok("Usuario registrado correctamente");
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token no proporcionado");
        }

        String token = authorizationHeader.substring(7);
        String email = jwtUtil.extractUsername(token);

        AppUser user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        UserProfileResponse response = new UserProfileResponse(
            user.getId(),
            user.getName(),
            user.getNickname(),
            user.getRol()
        );

        return ResponseEntity.ok(response);
    }

}

