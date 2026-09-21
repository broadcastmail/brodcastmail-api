package com.broadcastmail.api.connection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;


public class ConnectionRequests {
    public record UpdateProjectRequest(@NotBlank String projectRef) {}
    public record UpdateTableRequest(
            @NotBlank String userTableSchema,
            @NotBlank String userTableName,
            @NotBlank String userIdColumn) {}
    public record UpdateColumnsRequest(@NotEmpty List<String> columnNames) {}
}