package fr.tlprice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class GestionErreurs {

    private static final Logger log = LoggerFactory.getLogger(GestionErreurs.class);

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> etatInvalide(IllegalStateException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> argumentInvalide(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + " : " + f.getDefaultMessage())
            .findFirst().orElse("Donnees invalides");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> statut(ResponseStatusException e) {
        String message = e.getReason() != null ? e.getReason() : "Erreur " + e.getStatusCode().value();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", message));
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<Map<String, String>> erreurEnableBanking(RestClientResponseException e) {
        log.error("Erreur Enable Banking {} : {}", e.getStatusCode(), e.getResponseBodyAsString());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message",
            "Enable Banking a repondu " + e.getStatusCode().value()
            + ". Verifie ton Application ID, ta cle privee et que ton application est Active."));
    }
}
