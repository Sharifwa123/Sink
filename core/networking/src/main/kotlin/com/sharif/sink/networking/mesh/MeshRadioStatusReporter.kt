package com.sharif.sink.networking.mesh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class MeshRadioActivity { IDLE, STARTING, ACTIVE, FAILED }

data class MeshRadioState(
    val activity: MeshRadioActivity = MeshRadioActivity.IDLE,
    val failureReason: String? = null,
)

/**
 * Where [com.sharif.sink.networking.transport.NearbyTransport] reports what actually happened
 * the last time it tried to start advertising/discovery — a missing permission, disabled radios,
 * or no Play Services previously failed completely silently (caught, logged to logcat only). The
 * UI has no other way to distinguish "still starting" from "genuinely can't start" without this.
 */
@Singleton
class MeshRadioStatusReporter @Inject constructor() {
    private val _state = MutableStateFlow(MeshRadioState())
    val state: StateFlow<MeshRadioState> = _state

    fun update(activity: MeshRadioActivity, failureReason: String? = null) {
        _state.value = MeshRadioState(activity, failureReason)
    }
}
