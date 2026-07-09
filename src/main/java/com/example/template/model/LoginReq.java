package com.example.template.model;

import jakarta.validation.constraints.NotBlank;

public record LoginReq(@NotBlank String username, @NotBlank String password) {}
