package com.broadcastmail.api.supabase.management;

import com.broadcastmail.api.supabase.management.dto.SupabaseProject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectNameResolver {

    private final SupabaseManagementClient supabaseManagementClient;

    public String resolve(String projectRef, Supplier<String> accessToken) {
        try {
            return supabaseManagementClient.listProjects(accessToken.get()).stream()
                    .filter(p -> projectRef.equals(p.ref()))
                    .map(SupabaseProject::name)
                    .findFirst()
                    .orElse(projectRef);
        } catch (Exception e) {
            log.warn("[project-name] could not resolve name for {}: {}", projectRef, e.getMessage());
            return projectRef;
        }
    }
}
