package br.com.microservices.orchestrated.inventoryservice.config.exception;

import java.io.Serializable;

public record ExceptionDetails(int status, String message) implements Serializable {
}
