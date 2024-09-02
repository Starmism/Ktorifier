package me.starmism

import io.ktor.network.sockets.Socket
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.Key
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

suspend fun CoroutineScope.handleV2Packet(readChannel: ByteReadChannel, sendChannel: ByteWriteChannel, socket: Socket, challenge: String, random: SecureRandom, tokens: Map<String, String>) {

    val json = withTimeout(2000) {
        val length = readChannel.readShort()
        readChannel.readString(length.toInt())
    }

    val votePacket = Json.decodeFromString<V2VotePacket>(json)
    val vote = Json.decodeFromString<Vote>(votePacket.payload)

    // Verify challenge (for replay attacks)
    if (vote.challenge != challenge) {
        throw IllegalArgumentException("Challenge is not valid")
    }

    val token = tokens[vote.serviceName] ?: throw IllegalArgumentException("Unknown service '${vote.serviceName}'")

    // Verify signature (HmacSHA256)
    val key = SecretKeySpec(token.toByteArray(), "HmacSHA256")

    val signatureBytes = Base64.getDecoder().decode(votePacket.signature)

    if (!hmacEqual(signatureBytes, votePacket.payload.toByteArray(), key, random)) {
        throw IllegalArgumentException("Signature is not valid (invalid token?)")
    }

    // Usernames must be 16 chars or fewer
    if (vote.username.length > 16) {
        throw IllegalArgumentException("Username too long")
    }

    sendChannel.writeStringUtf8(Json.encodeToString(V2VoteResponse("ok")) + "\r\n")
    socket.close()

    launch {
        onSuccessfulVote(vote)
    }
}



// Taken from https://github.com/NuVotifier/NuVotifier/blob/master/common/src/main/java/com/vexsoftware/votifier/net/protocol/VotifierProtocol2Decoder.java#L75
private fun hmacEqual(sig: ByteArray, message: ByteArray, key: Key, random: SecureRandom): Boolean {
    // See https://www.nccgroup.trust/us/about-us/newsroom-and-events/blog/2011/february/double-hmac-verification/
    // This randomizes the byte order to make timing attacks more difficult.
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(key)
    val calculatedSig = mac.doFinal(message)

    // Generate a random key for use in comparison
    val randomKey = ByteArray(32)
    random.nextBytes(randomKey)

    // Then generate two HMACs for the different signatures found
    val mac2 = Mac.getInstance("HmacSHA256")
    mac2.init(SecretKeySpec(randomKey, "HmacSHA256"))
    val clientSig = mac2.doFinal(sig)
    mac2.reset()
    val realSig = mac2.doFinal(calculatedSig)

    return MessageDigest.isEqual(clientSig, realSig)
}

suspend fun ByteReadChannel.readString(size: Int): String {
    ByteArray(size).let {
        readFully(it)
        return it.toString(Charsets.UTF_8)
    }
}


@Serializable
data class V2VotePacket(val payload: String, val signature: String)

@Serializable
data class V2VoteResponse(val status: String, val cause: String? = null, val error: String? = null)
