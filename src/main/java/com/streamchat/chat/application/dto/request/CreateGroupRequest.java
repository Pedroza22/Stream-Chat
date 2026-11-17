package com.streamchat.chat.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(@NotBlank @Size(max = 100) String name) {
}
