package in.skillsignal.security;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String email, String fullName, String role) {}

