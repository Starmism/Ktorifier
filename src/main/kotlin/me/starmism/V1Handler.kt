package me.starmism

import io.ktor.network.sockets.Socket
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.bits.highByte
import io.ktor.utils.io.bits.lowByte
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import kotlin.io.path.toPath

suspend fun CoroutineScope.handleV1Packet(readChannel: ByteReadChannel, socket: Socket, privateKey: PrivateKey, firstTwoBytes: Short) {

    val packet = withTimeout(2000) {
        ByteArray(256).apply {
            // Copy the first two bytes read earlier
            this[0] = firstTwoBytes.highByte
            this[1] = firstTwoBytes.lowByte
            readChannel.readFully(this, 2, 254)
        }
    }

    // RSA Decode
    val cipher = Cipher.getInstance("RSA")
    cipher.init(Cipher.DECRYPT_MODE, privateKey)
    val rawBytes = cipher.doFinal(packet)

    val (opcode, serviceName, username, address, timestamp) = rawBytes.toString(StandardCharsets.US_ASCII).split("\n")

    if (opcode != "VOTE") {
        throw IllegalArgumentException("Vote opcode missing.")
    }

    val vote = Vote(serviceName, username, timestamp.toLong(), InetAddress.getByName(address), "")

    // No response in V1 protocol
    socket.close()

    launch {
        onSuccessfulVote(vote)
    }
}



fun loadKeys(directory: File): KeyPair {
    if (!File(directory, "private.key").exists()) {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()
        saveKeys(Vote::class.java.protectionDomain.codeSource.location.toURI().toPath().parent.toFile(), pair)
    }

    val encodedPublicKey = Base64.getDecoder().decode(Files.readAllBytes(File(directory, "public.key").toPath()))
    val encodedPrivateKey = Base64.getDecoder().decode(Files.readAllBytes(File(directory, "private.key").toPath()))

    val keyFactory = KeyFactory.getInstance("RSA")
    val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(encodedPublicKey))
    val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(encodedPrivateKey))
    return KeyPair(publicKey, privateKey)
}

fun saveKeys(directory: File, keyPair: KeyPair) {
    FileOutputStream(File(directory, "public.key")).use {
        it.write(Base64.getEncoder().encode(keyPair.public.encoded))
    }

    FileOutputStream(File(directory, "private.key")).use {
        it.write(Base64.getEncoder().encode(keyPair.private.encoded))
    }
}

