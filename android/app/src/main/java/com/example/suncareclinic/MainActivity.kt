package com.example.suncareclinic

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.suncareclinic.data.api.ApiClient
import com.example.suncareclinic.data.local.TokenStorage
import com.example.suncareclinic.data.repository.AppointmentScheduleRepository
import com.example.suncareclinic.data.repository.AuthRepository
import com.example.suncareclinic.data.repository.DoctorEncounterRepository
import com.example.suncareclinic.data.repository.OnsiteAppointmentRepository
import com.example.suncareclinic.data.repository.ProfileRepository
import com.example.suncareclinic.ui.components.SuncareLoadingView
import com.example.suncareclinic.ui.navigation.SignedInShell
import com.example.suncareclinic.ui.screens.auth.AuthScreen
import com.example.suncareclinic.ui.screens.auth.AuthViewModel
import com.example.suncareclinic.ui.screens.auth.SessionState
import com.example.suncareclinic.ui.theme.SuncareClinicTheme
import com.example.suncareclinic.ui.theme.SuncareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            SuncareClinicTheme {
                SuncareApp()
            }
        }
    }
}

private data class AppDependencies(
    val authRepository: AuthRepository,
    val profileRepository: ProfileRepository,
    val onsiteAppointmentRepository: OnsiteAppointmentRepository,
    val appointmentScheduleRepository: AppointmentScheduleRepository,
    val doctorEncounterRepository: DoctorEncounterRepository,
)

@Composable
fun SuncareApp() {
    val context = LocalContext.current.applicationContext
    val deps = remember(context) {
        val tokenStorage = TokenStorage(context)
        val api = ApiClient.create(tokenStorage)
        AppDependencies(
            authRepository = AuthRepository(api, tokenStorage),
            profileRepository = ProfileRepository(api, tokenStorage),
            onsiteAppointmentRepository = OnsiteAppointmentRepository(api, tokenStorage),
            appointmentScheduleRepository = AppointmentScheduleRepository(api, tokenStorage),
            doctorEncounterRepository = DoctorEncounterRepository(api, tokenStorage),
        )
    }
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory(deps.authRepository))
    val uiState by authViewModel.uiState.collectAsState()

    when (val session = uiState.session) {
        SessionState.Checking -> {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = SuncareTheme.colors.canvas,
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SuncareLoadingView(message = "Đang kiểm tra phiên đăng nhập...")
                }
            }
        }

        SessionState.Guest -> {
            AuthScreen(
                state = uiState,
                onModeChange = authViewModel::switchMode,
                onFieldChange = authViewModel::updateField,
                onSubmit = authViewModel::submit,
                onRetryHealth = authViewModel::checkHealth,
            )
        }

        is SessionState.SignedIn -> {
            SignedInShell(
                account = session.account,
                authState = uiState,
                profileRepository = deps.profileRepository,
                onsiteAppointmentRepository = deps.onsiteAppointmentRepository,
                appointmentScheduleRepository = deps.appointmentScheduleRepository,
                doctorEncounterRepository = deps.doctorEncounterRepository,
                onRefreshAccount = authViewModel::refreshAccount,
                onSessionExpired = { authViewModel.handleSessionExpired() },
                onConfirmLogout = authViewModel::logout,
                onClearLogoutError = authViewModel::clearLogoutError,
            )
        }
    }
}
