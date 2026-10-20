package com.jjt.ingsis.permissions.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AccessRelationTest {
    @Test
    fun ownerIsOwnedEvenIfAShareExists() {
        assertEquals(AccessRelation.OWNED, AccessRelation.of("dev-juan", "dev-juan", shared = true))
        assertEquals(AccessRelation.OWNED, AccessRelation.of("dev-juan", "dev-juan", shared = false))
    }

    @Test
    fun recipientOfAShareIsShared() {
        assertEquals(AccessRelation.SHARED, AccessRelation.of("dev-juan", "dev-thiago", shared = true))
    }

    @Test
    fun anyoneElseHasNoAccess() {
        assertEquals(AccessRelation.NONE, AccessRelation.of("dev-juan", "dev-thiago", shared = false))
    }

    @Test
    fun identitiesAreComparedExactly() {
        assertEquals(AccessRelation.NONE, AccessRelation.of("dev-juan", "DEV-JUAN", shared = false))
        assertEquals(AccessRelation.NONE, AccessRelation.of("dev-juan", " dev-juan", shared = false))
    }
}
