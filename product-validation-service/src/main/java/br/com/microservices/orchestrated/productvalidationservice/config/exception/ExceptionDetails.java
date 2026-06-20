package br.com.microservices.orchestrated.productvalidationservice.config.exception;

import java.io.Serializable;

public record ExceptionDetails(int status, String message) implements Serializable {
}
