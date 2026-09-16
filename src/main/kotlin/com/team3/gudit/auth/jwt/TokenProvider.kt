package com.team3.gudit.auth.jwt

import com.team3.gudit.auth.security.CustomUserDetails
import com.team3.gudit.user.domain.entity.Role
import com.team3.gudit.user.domain.entity.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.Base64
import java.util.Date

@Service
class TokenProvider(private val jwtProperties: JwtProperties) {
    private val secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtProperties.secretKey))
    private val jwtParser = Jwts.parser().verifyWith(secretKey).build()

    fun getAuthentication(token: String): Authentication {
        val claims = getClaims(token)
        val principal = CustomUserDetails(
            claims.subject.toLong(),
            Role.valueOf(claims.get(CLAIM_ROLE, String::class.java)),
        )
        return UsernamePasswordAuthenticationToken(principal, token, principal.authorities)
    }

    fun generateToken(user: User, validity: Duration, tokenType: TokenType): String {
        val now = Date()
        val expiration = Date(now.time + validity.toMillis())
        return Jwts.builder()
            .header().type("JWT").and()
            .issuer(jwtProperties.issuer)
            .issuedAt(now)
            .expiration(expiration)
            .subject(user.id.toString())
            .claim(CLAIM_ROLE, user.role.name)
            .claim(CLAIM_TOKEN_TYPE, tokenType.name)
            .signWith(secretKey, Jwts.SIG.HS512)
            .compact()
    }

    fun getTokenType(token: String): TokenType =
        TokenType.valueOf(getClaims(token).get(CLAIM_TOKEN_TYPE, String::class.java))

    fun validateToken(token: String, expectedType: TokenType): TokenStatus {
        return try {
            val claims = jwtParser.parseSignedClaims(token).payload
            val tokenType = claims.get(CLAIM_TOKEN_TYPE, String::class.java)
            if (jwtProperties.issuer != claims.issuer || expectedType.name != tokenType) {
                TokenStatus.INVALID
            } else {
                TokenStatus.VALID
            }
        } catch (exception: ExpiredJwtException) {
            TokenStatus.EXPIRED
        } catch (exception: Exception) {
            TokenStatus.INVALID
        }
    }

    fun getUserId(token: String): Long = getClaims(token).subject.toLong()
    fun getRole(token: String): Role = Role.valueOf(getClaims(token).get(CLAIM_ROLE, String::class.java))
    private fun getClaims(token: String): Claims = jwtParser.parseSignedClaims(token).payload

    companion object {
        private const val CLAIM_ROLE = "role"
        private const val CLAIM_TOKEN_TYPE = "token_type"
    }
}
