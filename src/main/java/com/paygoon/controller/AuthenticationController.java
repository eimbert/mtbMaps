package com.paygoon.controller;

import java.util.HashMap;
import java.util.Map;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paygoon.dto.AuthRequest;
import com.paygoon.dto.AuthResponse;
import com.paygoon.dto.LoginResponse;
import com.paygoon.dto.UserRegisterRequest;
import com.paygoon.model.AppUser;
import com.paygoon.repository.UserRepository;
import com.paygoon.security.JwtUtil;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserDetailsService userDetailsService;
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
	
	
	        String token = jwtUtil.generateToken(user.getEmail());
	
	        Map<String, Object> response = new HashMap<>();
	        response.put("exitCode", 0);
	        response.put("token", token);
	        response.put("message", "Autenticación correcta");
	        return ResponseEntity.ok(response);
	        
        } catch (BadCredentialsException | UsernameNotFoundException e) {
        	Map<String, Object> error = new HashMap<>();
            error.put("exitCode", -1);
            error.put("token", null);
            error.put("message", "Usuario o contraseña erróneos");
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
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
        user.setVerified(false);   // puedes activarlo después con verificación por correo
        user.setPremium(false);

        userRepository.save(user);

        return ResponseEntity.ok("Usuario registrado correctamente");
    }

}

