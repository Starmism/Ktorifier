package me.starmism

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.net.InetAddress
import java.util.UUID

@Serializable
data class Vote(
    val username: String,
    val serviceName: String,
    val timestamp: Long,
    @Serializable(with = InetAddressAsStringSerializer::class)
    val address: InetAddress,
    val challenge: String,
    @Serializable(with = UUIDAsStringSerializer::class)
    val uuid: UUID? = null,
)



// kotlinx.serialization should just include these!!!
object InetAddressAsStringSerializer : KSerializer<InetAddress> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("InetAddress", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: InetAddress) {
        encoder.encodeString(value.hostAddress)
    }

    override fun deserialize(decoder: Decoder): InetAddress {
        return try {
            InetAddress.getByName(decoder.decodeString())
        } catch (e: Exception) {
            throw SerializationException("Invalid address format", e)
        }
    }
}

object UUIDAsStringSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }
}
