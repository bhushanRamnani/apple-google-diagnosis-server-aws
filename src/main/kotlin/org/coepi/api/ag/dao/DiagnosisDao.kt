package org.coepi.api.ag.dao

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import org.apache.commons.lang3.Validate
import org.coepi.api.ag.Utils
import java.nio.ByteBuffer
import java.util.*

class DiagnosisDao {
    private val dynamoMapper: DynamoDBMapper

    companion object {
        fun generateReportId(reportDayNumber: Long, intervalNumber: Int): String {
            return "$reportDayNumber:$intervalNumber"
        }

        fun generateRecord(reportDayNumber: Long,
                           keyDayNumber: Long,
                           intervalNumber: Int,
                           keyData: ByteArray): DiagnosisKeyRecord {
            val reportId = generateReportId(reportDayNumber, intervalNumber)
            val randomId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            return DiagnosisKeyRecord(reportId, randomId, timestamp, keyDayNumber, keyData)
        }
    }

    init {
        val ddbClient = AmazonDynamoDBClientBuilder.standard().build()
        this.dynamoMapper = DynamoDBMapper(ddbClient)
    }

    fun addReport(keyData: ByteArray,
                  reportDayNumber: Long,
                  keyDayNumber: Long,
                  intervalNumber: Int): DiagnosisKeyRecord {
        Validate.isTrue(keyData.isNotEmpty(), "keyData cannot be empty")
        Utils.validateDayNumber(reportDayNumber)
        Utils.validateDayNumber(keyDayNumber)
        Validate.isTrue(intervalNumber > 0, "intervalNumber should be positive")

        val reportRecord = generateRecord(reportDayNumber, keyDayNumber, intervalNumber, keyData)
        this.dynamoMapper.save(reportRecord)
        return reportRecord
    }

    fun addBatchReports(diagnosisKeys: List<DiagnosisKeyRecord>): Int? {
        if (diagnosisKeys.isEmpty()) return 0

        val batchSaveResult = dynamoMapper.batchSave(diagnosisKeys)
        return batchSaveResult?.size
    }

    fun doesKeyExist(keyData: ByteArray): Boolean {
        val attributeValueMap = HashMap<String, AttributeValue>()
        attributeValueMap[":val1"] = AttributeValue().withB(ByteBuffer.wrap(keyData))

        val queryExpression = DynamoDBQueryExpression<DiagnosisKeyRecord>()
        queryExpression.keyConditionExpression = "keyData = :val1"
        queryExpression.indexName = DiagnosisKeyRecord.KEY_DATA_INDEX
        queryExpression.expressionAttributeValues = attributeValueMap
        queryExpression.isConsistentRead = false
        queryExpression.limit = 1

        val queryOutput = dynamoMapper.queryPage(DiagnosisKeyRecord::class.java, queryExpression) ?: return false

        return queryOutput.count > 0
    }

    fun queryReports(dayNumber: Long, intervalNumber: Int): List<DiagnosisKeyRecord> {
        val reportId = generateReportId(dayNumber, intervalNumber)
        val queryExpression = DynamoDBQueryExpression<DiagnosisKeyRecord>()
        queryExpression.keyConditionExpression = "reportId = :val1"

        val attributeValueMap = HashMap<String, AttributeValue>()
        attributeValueMap[":val1"] = AttributeValue().withS(reportId)
        queryExpression.expressionAttributeValues = attributeValueMap

        val outputList = mutableListOf<DiagnosisKeyRecord>()
        var lastEvalKey: Map<String, AttributeValue>? = null

        do {
            queryExpression.exclusiveStartKey = lastEvalKey
            val pageOutput = dynamoMapper.queryPage(DiagnosisKeyRecord::class.java, queryExpression)
            outputList.addAll(pageOutput.results)
            lastEvalKey = pageOutput.lastEvaluatedKey
        } while (lastEvalKey != null)

        return outputList
    }
}