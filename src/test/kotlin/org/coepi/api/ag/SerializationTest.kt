package org.coepi.api.ag

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class SerializationTest {

    val objectMapper = jacksonObjectMapper()

    @Test
    fun serialization_sanity() {
        val postData = "[{\"keyData\":\"cXdlcmZzZGFzZ3RlcjU0MAo=\",\"dayNumber\":\"18374\"},{\"keyData\":\"cXdlcmZzZGE0NTNlcjU0MAo=\",\"dayNumber\":\"18375\"},{\"keyData\":\"cXdl45^zZGE0NTNlcjU0MAo=\",\"dayNumber\":\"18376\"},{\"keyData\":\"cXdl455670NTNlcjU0MAo=\",\"dayNumber\":\"18377\"}]"
        val request: List<DiagnosisKey> = objectMapper.readValue(postData)
        Assertions.assertEquals(request.size, 4)
    }
}