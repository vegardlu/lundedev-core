package no.lundedev.core.model

import jakarta.persistence.*
import java.time.ZonedDateTime
import java.util.UUID

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(unique = true, nullable = false)
    val email: String,

    val name: String? = null,

    @Column(nullable = false)
    val role: String = "USER",

    @Column(name = "created_at", insertable = false, updatable = false)
    val createdAt: ZonedDateTime? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    val updatedAt: ZonedDateTime? = null
)
