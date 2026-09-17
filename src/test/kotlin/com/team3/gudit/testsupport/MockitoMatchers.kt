package com.team3.gudit.testsupport

import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq

/** Mockito의 null matcher 반환값이 Kotlin non-null 검사를 건드리지 않게 한다. */
fun <T> anyValue(type: Class<T>): T = any(type)

/** Mockito의 null matcher 반환값이 Kotlin non-null 검사를 건드리지 않게 한다. */
fun <T> eqValue(value: T): T = eq(value)
