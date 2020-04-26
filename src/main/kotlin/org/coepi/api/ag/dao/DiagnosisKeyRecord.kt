package org.coepi.api.ag.dao

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable

@DynamoDBTable(tableName = "DiagnosisKeys")
data class DiagnosisKeyRecord(

        /**
         * Partition key for the diagnosis key. Set to <dayNumber>:<intervalNumber>
         */
        @DynamoDBHashKey
        var reportId: String = "",

        /**
         * Random GUID so that the keys can be stored out-of-order, randomly in the table
         */
        @DynamoDBRangeKey
        var randomId: String = "",

        /**
         * Timestamp at which this key was reported to the server
         */
        @DynamoDBAttribute
        var reportTimestamp: Long = 0,

        /**
         * Diagnosis key day number corresponding to the day when the diagnosis key was
         * generated on the client device. dayNumber = (epoch seconds / 24 * 60 * 60)
         */
        @DynamoDBAttribute
        var dayNumber: Long = 0,

        /**
         * Raw bytes containing the diagnosis key data
         */
        @DynamoDBAttribute
        @DynamoDBIndexHashKey(globalSecondaryIndexName = KEY_DATA_INDEX)
        var keyData: ByteArray = byteArrayOf()
) {
        companion object {
            const val KEY_DATA_INDEX = "keyData-index"
        }
}