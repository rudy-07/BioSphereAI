package com.madhusudhan.biosphereai.data

/**
 * The one place that decides which backend the app talks to.
 * To use the real server, return an HTTP implementation of [BioSphereRepository] here
 * (same four calls, base URL e.g. http://10.0.2.2:8000/api/v1 for the emulator).
 */
object RepositoryProvider {
    val repository: BioSphereRepository by lazy { MockBioSphereRepository() }
}
