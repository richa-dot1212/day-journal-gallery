package com.journalgallery.shared.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun defaultHttpClient(): HttpClient = HttpClient(Darwin)
