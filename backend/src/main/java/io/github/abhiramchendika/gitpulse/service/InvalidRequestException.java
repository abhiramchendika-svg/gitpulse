package io.github.abhiramchendika.gitpulse.service;

/** The client sent parameters that are well-formed but not acceptable (e.g. since after until). */
public class InvalidRequestException extends RuntimeException {

  public InvalidRequestException(String message) {
    super(message);
  }
}
