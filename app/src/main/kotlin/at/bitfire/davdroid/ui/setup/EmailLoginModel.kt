/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.util.DavUtils.toURIorNull

class EmailLoginModel: ViewModel() {

    data class UiState(
        val email: String = "",
        val password: String = ""
    ) {
        val uri = "mailto:$email".toURIorNull()

        val canContinue = uri != null && password.isNotEmpty()

        fun asLoginInfo(): LoginInfo {
            return LoginInfo(
                baseUri = uri,
                credentials = Credentials(
                    username = email,
                    password = password
                )
            )
        }
    }

    var uiState by mutableStateOf(UiState())
        private set

    fun initialize(initialLoginInfo: LoginInfo) {
        uiState = uiState.copy(
            email = initialLoginInfo.credentials?.username ?: "",
            password = initialLoginInfo.credentials?.password ?: ""
        )
    }

    fun setEmail(email: String) {
        uiState = uiState.copy(email = email)
    }

    fun setPassword(password: String) {
        uiState = uiState.copy(password = password)
    }

}