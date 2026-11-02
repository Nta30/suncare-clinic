package com.suncare.clinic.modules.account;

import com.suncare.clinic.modules.account.repository.AdminBootstrapRepository;
import com.suncare.clinic.modules.account.service.AdminBootstrapService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapServiceTests {
    @Test
    void createsAdminWithHashedPasswordAndNullGrantActor() {
        AdminBootstrapRepository repository = mock(AdminBootstrapRepository.class);
        when(repository.findAccountId("admin.local")).thenReturn(null);
        when(repository.findStaffId("DEV_ADMIN")).thenReturn(null);
        when(repository.findAdminRoleId()).thenReturn(3L);
        when(repository.createStaff("Quản trị viên")).thenReturn(10L);
        when(repository.createAccount(eq("admin.local"), anyString(), eq(10L))).thenAnswer(call -> {
            String hash = call.getArgument(1);
            assertTrue(new BCryptPasswordEncoder().matches("local-password-123", hash));
            return 20L;
        });

        new AdminBootstrapService(repository).bootstrap(" Admin.Local ", "local-password-123", "Quản trị viên");

        verify(repository).grantAdminRole(20L, 3L);
    }

    @Test
    void leavesExistingAdminUnchanged() {
        AdminBootstrapRepository repository = mock(AdminBootstrapRepository.class);
        when(repository.findAccountId("admin.local")).thenReturn(20L);
        when(repository.findStaffId("DEV_ADMIN")).thenReturn(10L);
        when(repository.findAdminRoleId()).thenReturn(3L);
        when(repository.isBootstrapAccount(20L, 10L)).thenReturn(true);
        when(repository.hasRole(20L, 3L)).thenReturn(true);

        new AdminBootstrapService(repository).bootstrap("admin.local", "local-password-123", "Quản trị viên");

        verify(repository, never()).createStaff(anyString());
        verify(repository, never()).createAccount(anyString(), anyString(), eq(10L));
        verify(repository, never()).grantAdminRole(20L, 3L);
    }

    @Test
    void rejectsExistingLoginForDifferentStaff() {
        AdminBootstrapRepository repository = mock(AdminBootstrapRepository.class);
        when(repository.findAccountId("admin.local")).thenReturn(20L);
        when(repository.findAdminRoleId()).thenReturn(3L);

        assertThrows(IllegalStateException.class, () ->
                new AdminBootstrapService(repository).bootstrap("admin.local", "local-password-123", "Quản trị viên"));
    }
}
