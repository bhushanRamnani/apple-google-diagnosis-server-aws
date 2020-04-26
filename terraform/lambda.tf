resource "aws_iam_role" "ag_diagnosis_server_lambda_backend_role" {
  name = "iam_for_diagnosis_server_lambda_${var.region}"

  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": "lambda.amazonaws.com"
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF
}

locals {
  jarfile = "../build/libs/apple-google-diagnosis-server-1.0.0-all.jar"
}

resource "aws_lambda_function" "ag_diagnosis_server_lambda" {
  filename      = local.jarfile
  function_name = "DiagnosisServerLambda"
  role          = aws_iam_role.ag_diagnosis_server_lambda_backend_role.arn
  handler       = "org.coepi.api.ag.DiagnosisServerHandler::handleRequest"

  source_code_hash = filebase64sha256(local.jarfile)
  memory_size      = 512
  timeout          = 10
  runtime          = "java11"
}

resource "aws_iam_policy" "ag_lambda_dynamodb_access" {
  name        = "diagnosis_server_lambda_dynamodb_policy_${var.region}"
  path        = "/"
  description = "IAM policy for DynamoDB access from lambda"

  policy = <<EOF
{
    "Version": "2012-10-17",
        "Statement": [
            {
                "Effect": "Allow",
                "Action": [
                    "dynamodb:DeleteItem",
                    "dynamodb:DescribeContributorInsights",
                    "dynamodb:RestoreTableToPointInTime",
                    "dynamodb:ListTagsOfResource",
                    "dynamodb:UpdateContributorInsights",
                    "dynamodb:UpdateContinuousBackups",
                    "dynamodb:TagResource",
                    "dynamodb:DescribeTable",
                    "dynamodb:GetItem",
                    "dynamodb:DescribeContinuousBackups",
                    "dynamodb:BatchGetItem",
                    "dynamodb:UpdateTimeToLive",
                    "dynamodb:BatchWriteItem",
                    "dynamodb:ConditionCheckItem",
                    "dynamodb:UntagResource",
                    "dynamodb:PutItem",
                    "dynamodb:Scan",
                    "dynamodb:Query",
                    "dynamodb:DescribeStream",
                    "dynamodb:UpdateItem",
                    "dynamodb:DescribeTimeToLive",
                    "dynamodb:DescribeGlobalTableSettings",
                    "dynamodb:GetShardIterator",
                    "dynamodb:DescribeGlobalTable",
                    "dynamodb:RestoreTableFromBackup",
                    "dynamodb:DescribeBackup",
                    "dynamodb:GetRecords",
                    "dynamodb:DescribeTableReplicaAutoScaling"
                ],
                "Resource": [
                    "${aws_dynamodb_table.ag_diagnosis_table.arn}"
                ]
            },
            {
                "Effect": "Allow",
                "Action": [
                    "dynamodb:DescribeReservedCapacityOfferings",
                    "dynamodb:DescribeReservedCapacity",
                    "dynamodb:PurchaseReservedCapacityOfferings",
                    "dynamodb:DescribeLimits",
                    "dynamodb:ListStreams"
                ],
                "Resource": [
                    "${aws_dynamodb_table.ag_diagnosis_table.arn}"
                ]
            }
        ]
}
EOF
}

resource "aws_iam_role_policy_attachment" "ag_lambda_dynamodb_policy_attachment" {
  role       = aws_iam_role.ag_diagnosis_server_lambda_backend_role.name
  policy_arn = aws_iam_policy.ag_lambda_dynamodb_access.arn
}

resource "aws_iam_role_policy_attachment" "ag_lambda_cloudwatch_policy_attachment" {
  role       = aws_iam_role.ag_diagnosis_server_lambda_backend_role.name
  policy_arn = var.cloudwatch_policy_arn
}