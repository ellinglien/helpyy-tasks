package com.thelightphone.helpytasks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TitleVerbTest {

    @Test
    fun `bolds a verb at the start`() {
        val result = splitTitleVerb("Call the vendor about invoice")
        assertEquals(VerbSplit("Call", "the vendor about invoice"), result)
    }

    @Test
    fun `bolds a phrasal verb`() {
        val result = splitTitleVerb("Follow up with Sarah")
        assertEquals(VerbSplit("Follow up", "with Sarah"), result)
    }

    @Test
    fun `recognises reach out as a phrasal verb`() {
        val result = splitTitleVerb("Reach out to the printer")
        assertEquals(VerbSplit("Reach out", "to the printer"), result)
    }

    @Test
    fun `does not bold a non-verb first word`() {
        assertNull(splitTitleVerb("Think about the budget"))
    }

    @Test
    fun `does not bold a weasel lead`() {
        assertNull(splitTitleVerb("Research grant options"))
    }

    @Test
    fun `returns null for an empty title`() {
        assertNull(splitTitleVerb(""))
    }

    @Test
    fun `returns null for a blank title`() {
        assertNull(splitTitleVerb("   "))
    }

    @Test
    fun `is case-insensitive on the verb but preserves original casing in the split`() {
        val result = splitTitleVerb("SEND the deck")
        assertEquals(VerbSplit("SEND", "the deck"), result)
    }

    @Test
    fun `treats a lone verb with no rest as fully bolded`() {
        val result = splitTitleVerb("Submit")
        assertEquals(VerbSplit("Submit", ""), result)
    }
}
