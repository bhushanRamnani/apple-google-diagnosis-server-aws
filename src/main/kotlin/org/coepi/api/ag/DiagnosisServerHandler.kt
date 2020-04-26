package org.coepi.api.ag

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.commons.lang3.Validate
import org.apache.logging.log4j.LogManager
import org.coepi.api.ag.Utils.getDayNumber
import org.coepi.api.ag.Utils.getIntervalNumber
import org.coepi.api.ag.dao.DiagnosisDao
import org.coepi.api.ag.dao.DiagnosisKeyRecord

import java.time.Instant
import java.util.*


class DiagnosisServerHandler: RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private val diagnosisDao: DiagnosisDao = DiagnosisDao()
    private val objectMapper = jacksonObjectMapper()

    companion object {
        const val DAY_NUMBER_KEY = "reportDayNumber"
        const val INTERVAL_NUMBER_KEY = "intervalNumber"
        const val INTERVAL_LENGTH_KEY = "intervalLength"
        const val INTERVAL_LENGTH_S: Long = 6 * 3600

        private val logger = LogManager.getLogger(DiagnosisServerHandler::class.java)
    }

    override fun handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent {
        logger.info("Processing request: ${context.awsRequestId}. " +
                "Query params: ${input.queryStringParameters}. " +
                "Body: ${input.body}")
        try {
            if (input.httpMethod == "GET") {
                logger.info("Handling GET Request for :${input.path}. ReqId: ${context.awsRequestId}")
                return handleGetReport(input)
            }
            logger.info("Handling POST Request for :${input.path}. ReqId: ${context.awsRequestId}")
            return handlePostReport(input)
        } catch (ex: Exception) {
            logger.error("Failed to serve request", ex)
            return APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("Internal Diagnosis Server Failure")
        }
    }

    fun handleGetReport(input: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent {
        var statusCode: Int?
        var body: String?

        try {
            val queryParams = parseQueryParameters(input)
            val epochNow = Instant.now().epochSecond
            val reportDayNumber = queryParams.first.orElse(getDayNumber(epochNow))
            val intervalNumber = queryParams.second.orElse(getIntervalNumber(epochNow))

            logger.info("Querying records for reportdayNumber: $reportDayNumber and $intervalNumber")
            val reports = diagnosisDao.queryReports(reportDayNumber, intervalNumber)
                    .map {
                        record -> DiagnosisKey(record.dayNumber,
                            Base64.getEncoder().encodeToString(record.keyData))
                    }
            body = objectMapper.writeValueAsString(reports)
            statusCode = 200
        } catch (ex: DiagnosisClientException) {
            logger.info("Failed to serve get request due to client error.", ex)
            body = ex.message
            statusCode = 400
        } catch (ex: UnexpectedIntervalLengthException) {
            logger.info("Failed to serve get request due to interval length client error.", ex)
            body = ex.message
            statusCode = 401
        }

        return APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withBody(body)
    }

    private fun parseQueryParameters(input: APIGatewayProxyRequestEvent): Pair<Optional<Long>, Optional<Int>> {
        val queryParameters = input.queryStringParameters
        var dayNumber = Optional.empty<Long>()
        var intervalNumber = Optional.empty<Int>()

        if (queryParameters == null) return Pair(dayNumber, intervalNumber)


        try {
            if(queryParameters.containsKey(DAY_NUMBER_KEY) && !queryParameters[DAY_NUMBER_KEY].isNullOrEmpty()) {
                val rawDayNumber = queryParameters[DAY_NUMBER_KEY]?.toLong()
                dayNumber = if (rawDayNumber != null) Optional.of(rawDayNumber) else Optional.empty()
            }

            if(queryParameters.containsKey(INTERVAL_NUMBER_KEY)) {
                val rawBatch = queryParameters[INTERVAL_NUMBER_KEY]?.toInt()
                intervalNumber = if (rawBatch != null) Optional.of(rawBatch) else Optional.empty()

                if (!queryParameters.containsKey(INTERVAL_LENGTH_KEY)) {
                    throw DiagnosisClientException("$INTERVAL_LENGTH_KEY query parameter is required if " +
                            " $INTERVAL_NUMBER_KEY is provided")
                }
                val intervalLength = queryParameters[INTERVAL_LENGTH_KEY]?.toLong()

                if (intervalLength != INTERVAL_LENGTH_S) {
                    throw UnexpectedIntervalLengthException("Interval Length $intervalLength is invalid for " +
                            "$DAY_NUMBER_KEY. Please use $INTERVAL_LENGTH_S to calculate $INTERVAL_NUMBER_KEY")
                }
            }
        } catch (ex: NumberFormatException) {
            throw DiagnosisClientException("$DAY_NUMBER_KEY or $INTERVAL_NUMBER_KEY or $INTERVAL_LENGTH_KEY in " +
                    "illegal number format.", ex)
        }

        if (intervalNumber.isPresent && intervalNumber.get() < 0) {
            throw DiagnosisClientException("$INTERVAL_LENGTH_KEY should be positive")
        }
        return Pair(dayNumber, intervalNumber)
    }

    fun handlePostReport(input: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent {
        return try {
            val records = validateAndParseRequest(input)
                    .filter { record ->
                        if (diagnosisDao.doesKeyExist(record.keyData)) {
                            logger.info("${record.keyData} has already been added.")
                            false
                        } else {
                            true
                        }
                    }
            logger.info("Adding ${records.size} items to DDB")
            val numFailed = diagnosisDao.addBatchReports(records)

            if (numFailed != null && numFailed > 0) {
                throw IllegalStateException("Failed to add save all keys to dynamodb from $records")
            }
            logger.info("Number of records actually added: ${records.size}")
            APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
        } catch (ex: DiagnosisClientException) {
            logger.info("Failed request due to client error: ${ex.message}")
            APIGatewayProxyResponseEvent()
                    .withBody(ex.message)
                    .withStatusCode(400)
        } catch (ex: InvalidSignatureException) {
            logger.info("Failed request due to invalid signature error: ${ex.message}")
            APIGatewayProxyResponseEvent()
                    .withBody(ex.message)
                    .withStatusCode(401)
        } catch (ex: Exception) {
            logger.error("Request failed do to internal diagnosis server error: ${ex.message}")
            APIGatewayProxyResponseEvent()
                    .withBody("Unexpected Diagnosis server Error")
                    .withStatusCode(500)
        }
    }

    fun validateAndParseRequest(input: APIGatewayProxyRequestEvent): List<DiagnosisKeyRecord> {
        try {
            if (input.body.isNullOrBlank()) {
                throw IllegalArgumentException("Post request body cannot be null or empty.")
            }

            val keys: List<DiagnosisKey> = objectMapper.readValue(input.body)

            if (keys.isEmpty()) {
                throw IllegalArgumentException("No diagnosis keys provided")
            }

            val epochNow = Instant.now().epochSecond

            return keys.map { k ->
                val keyDataBuffer = Base64.getDecoder().decode(k.keyData)
                Validate.isTrue(keyDataBuffer.isNotEmpty(), "key data cannot be empty")
                Utils.validateDayNumber(k.dayNumber)

                val reportDayNumber = Utils.getDayNumber(epochNow)
                val intervalNumber = Utils.getIntervalNumber(epochNow)

                DiagnosisDao.generateRecord(reportDayNumber, k.dayNumber,
                        intervalNumber, keyDataBuffer)
            }
        } catch (ex: Exception) {
            when(ex) {
                is JsonProcessingException,
                is JsonMappingException,
                is IllegalArgumentException,
                is NullPointerException -> {
                    throw DiagnosisClientException(ex.message, ex)
                } else -> throw ex
            }
        }
    }
}