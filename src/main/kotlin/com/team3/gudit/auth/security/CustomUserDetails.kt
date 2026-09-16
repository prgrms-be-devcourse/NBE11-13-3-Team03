package com.team3.gudit.auth.security

import com.team3.gudit.user.domain.entity.Role
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class CustomUserDetails(val userId: Long, val role: Role) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> = listOf(SimpleGrantedAuthority(role.name))
    override fun getPassword(): String? = null
    override fun getUsername(): String = userId.toString()
}
