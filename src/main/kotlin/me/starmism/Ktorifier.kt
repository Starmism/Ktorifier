package me.starmism

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.math.BigInteger
import java.security.SecureRandom
import javax.crypto.BadPaddingException
import kotlin.io.path.toPath

val logger = LoggerFactory.getLogger("main")

fun main() = runBlocking {
    val directory = Vote::class.java.protectionDomain.codeSource.location.toURI().toPath().parent.toFile()
    val random = SecureRandom()
    val keys = loadKeys(directory)
    val config = Json.decodeFromString<Config>(File(directory, "config.json").readText())

    val selectorManager = SelectorManager(Dispatchers.IO)
    val serverSocket = aSocket(selectorManager).tcp().bind(config.host, config.port)
    logger.info("Votifier listener started on ${config.host}:${config.port}")

    while (true) {
        val socket = serverSocket.accept()
        logger.info("Accepted socket ${socket.remoteAddress}")

        launch {
            // Per-request token to prevent replay attacks (only for V2)
            val challenge = BigInteger(130, random).toString(32)
            var isProtocolV2 = false

            val readChannel = socket.openReadChannel()
            val sendChannel = socket.openWriteChannel(autoFlush = true)

            sendChannel.writeStringUtf8("VOTIFIER 2 $challenge\n")

            try {
                val discriminator = withTimeout(500) {
                    readChannel.readShort()
                }

                if (discriminator == 0x733A.toShort()) {
                    isProtocolV2 = true
                    handleV2Packet(readChannel, sendChannel, socket, challenge, random, config.tokens)
                } else {
                    handleV1Packet(readChannel, socket, keys.private, discriminator)
                }
            } catch (e: Exception) {
                when (e) {
                    is BadPaddingException -> logger.error("Invalid RSA decode for V1 Vote")
                    is IllegalArgumentException -> logger.error(e.message)
                    else -> logger.error("", e)
                }

                if (isProtocolV2) {
                    sendChannel.writeStringUtf8(Json.encodeToString(V2VoteResponse("error", "CorruptedFrameException", e.message)) + "\r\n")
                }
            } finally {
                if (!socket.isClosed) {
                    socket.close()
                }
            }
        }
    }
}

suspend fun onSuccessfulVote(vote: Vote) {
    logger.info("Vote: $vote")
}


@Serializable
data class Config(
    val host: String,
    val port: Int,
    val tokens: Map<String, String>
)
