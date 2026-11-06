package com.sportage.obd

import com.sportage.obd.obd.J1979Pid
import com.sportage.obd.obd.J1979Reading
import com.sportage.obd.obd.UdsDecoding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class J1979PidTest {

    @Test
    fun `dids and commands follow the standardised block`() {
        assertEquals("F40C", J1979Pid.RPM.did)
        assertEquals("22F40C", J1979Pid.RPM.command)
        assertEquals("F405", J1979Pid.COOLANT_TEMP.did)
        assertEquals("F42F", J1979Pid.FUEL_LEVEL.did)
    }

    @Test
    fun `word scaled parameters decode like their Mode 01 twins`() {
        assertEquals(1726f, J1979Pid.RPM.decode(intArrayOf(0x1A, 0xF8))!!, 0.01f)
        assertEquals(2.56f, J1979Pid.MAF.decode(intArrayOf(0x01, 0x00))!!, 0.01f)
        assertEquals(0f, J1979Pid.SHORT_TERM_FUEL_TRIM.decode(intArrayOf(0x80))!!, 0.01f)
        assertEquals(60f, J1979Pid.SPEED.decode(intArrayOf(0x3C))!!, 0.01f)
        assertEquals(50f, J1979Pid.COOLANT_TEMP.decode(intArrayOf(0x5A))!!, 0.01f)
        assertEquals(50.19f, J1979Pid.ENGINE_LOAD.decode(intArrayOf(0x80))!!, 0.05f)
        assertEquals(0f, J1979Pid.TIMING_ADVANCE.decode(intArrayOf(0x80))!!, 0.01f)
    }

    @Test
    fun `word parameters refuse a one byte payload instead of inventing a value`() {
        assertNull(J1979Pid.RPM.decode(intArrayOf(0x1A)))
        assertNull(J1979Pid.MAF.decode(intArrayOf(0x01)))
        assertNull(J1979Pid.RPM.decode(intArrayOf()))
    }

    @Test
    fun `payload extraction finds the data after the DID echo`() {
        val payload = UdsDecoding.payload("62 F4 0C 1A F8", 0x22, "F40C")
        assertEquals(listOf(0x1A, 0xF8), payload!!.toList())
        assertNull(UdsDecoding.payload("7F 22 31", 0x22, "F40C"))
    }

    @Test
    fun `comments in the adapter reply do not break payload extraction`() {
        val payload = UdsDecoding.payload("0:\r\n62 F4 05 5A\r\n1: 00 00\r\n>", 0x22, "F405")
        assertEquals(0x5A, payload!![0])
    }

    @Test
    fun `readings format with the precision their unit needs`() {
        val now = System.currentTimeMillis()
        assertEquals("1726", J1979Reading(J1979Pid.RPM, 1726.4f, now).formatted)
        assertEquals("50.2", J1979Reading(J1979Pid.ENGINE_LOAD, 50.19f, now).formatted)
    }
}
