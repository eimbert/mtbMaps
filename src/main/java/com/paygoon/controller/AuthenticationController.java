package com.paygoon.controller;

import jakarta.validation.Valid;

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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.paygoon.dto.AuthRequest;
import com.paygoon.dto.AuthResponse;
import com.paygoon.dto.LoginResponse;
import com.paygoon.dto.UserProfileResponse;
import com.paygoon.dto.UserRegisterRequest;
import com.paygoon.dto.VerificationRequest;
import com.paygoon.dto.VerificationResponse;
import com.paygoon.model.AppUser;
import com.paygoon.model.AppUser.LoginType;
import com.paygoon.model.VerificationToken;
import com.paygoon.repository.UserRepository;
import com.paygoon.security.JwtUtil;
import com.paygoon.service.EmailVerificationService;

@RestController
@RequestMapping("/auth")
public class AuthenticationController {

    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EmailVerificationService emailVerificationService;

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
        AppUser user = userRepository.findByEmail(request.email()).orElse(null);

        if (user != null) {
            if (user.isVerified()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new AuthResponse("El email ya está registrado", -2));
            }

            try {
                VerificationToken token = emailVerificationService.createAndSendToken(user);
                return ResponseEntity.ok(new VerificationResponse(
                        "Correo reenviado para verificar tu cuenta",
                        token.getExpiresAt()
                ));
            } catch (Exception ex) {
                if (ex instanceof ResponseStatusException rse) {
                    return ResponseEntity.status(rse.getStatusCode()).body(rse.getReason());
                }
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("No se pudo enviar el correo de verificación");
            }
        }

        user = new AppUser();
        user.setEmail(request.email());
        user.setName(request.name());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setRol(request.rol());
        user.setVerified(false);   // puedes activarlo después con verificación por correo
        user.setPremium(false);
        user.setLoginType(LoginType.EMAIL);

        userRepository.save(user);

        try {
            VerificationToken token = emailVerificationService.createAndSendToken(user);
            return ResponseEntity.ok(new VerificationResponse(
                    "Usuario registrado correctamente, verifica tu correo",
                    token.getExpiresAt()
            ));
        } catch (Exception ex) {
            if (ex instanceof ResponseStatusException rse) {
                return ResponseEntity.status(rse.getStatusCode()).body(rse.getReason());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("No se pudo enviar el correo de verificación");
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody UserRegisterRequest request) {
        AppUser user = userRepository.findByEmail(request.email())
                .orElseGet(AppUser::new);

        if (user.getId() != null && user.isVerified()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new AuthResponse("El email ya está verificado", -1));
        }

        user.setEmail(request.email());
        user.setName(request.name());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setRol(request.rol());
        user.setVerified(false);
        user.setPremium(false);
        user.setLoginType(LoginType.EMAIL);

        userRepository.save(user);

        try {
            VerificationToken token = emailVerificationService.createAndSendToken(user);
            return ResponseEntity.ok(new VerificationResponse("Correo enviado", token.getExpiresAt()));
        } catch (Exception ex) {
            if (ex instanceof org.springframework.web.server.ResponseStatusException rse) {
                return ResponseEntity.status(rse.getStatusCode()).body(rse.getReason());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("No se pudo enviar el correo de verificación");
        }
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verify(@RequestParam("token") String token) {
        return handleVerification(token);
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPost(@Valid @RequestBody VerificationRequest request) {
        return handleVerification(request.token());
    }

    private ResponseEntity<?> handleVerification(String token) {
        try {
            emailVerificationService.verifyToken(token);
            return ResponseEntity.ok(new AuthResponse("Correo verificado", 0));
        } catch (Exception ex) {
            if (ex instanceof org.springframework.web.server.ResponseStatusException rse) {
                return ResponseEntity.status(rse.getStatusCode()).body(rse.getReason());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("No se pudo verificar el token");
        }
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

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token no proporcionado");
        }

        String token = authorizationHeader.substring(7);
        String email = jwtUtil.extractUsername(token);

        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        if (user.isVerified()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new AuthResponse("El email ya está verificado", -1));
        }

        try {
            VerificationToken verificationToken = emailVerificationService.createAndSendToken(user);
            return ResponseEntity.ok(new VerificationResponse("Correo reenviado", verificationToken.getExpiresAt()));
        } catch (Exception ex) {
            if (ex instanceof ResponseStatusException rse) {
                return ResponseEntity.status(rse.getStatusCode()).body(rse.getReason());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("No se pudo reenviar el correo de verificación");
        }
    }

}

