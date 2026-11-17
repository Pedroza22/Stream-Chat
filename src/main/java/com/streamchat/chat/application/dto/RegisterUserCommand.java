package com.streamchat.chat.application.dto;

public record RegisterUserCommand(String username, String email, String rawPassword) {
}
