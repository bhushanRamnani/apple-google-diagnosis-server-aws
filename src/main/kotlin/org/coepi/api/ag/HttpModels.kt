package org.coepi.api.ag

data class DiagnosisKey(val dayNumber: Long, val keyData: String)

data class PostDiagnosisRequest(val keys: List<DiagnosisKey>)