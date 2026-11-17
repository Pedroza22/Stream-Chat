package com.streamchat.chat.application.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddMemberRequest(@NotNull UUID targetUserId) {
}
