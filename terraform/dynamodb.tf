resource "aws_dynamodb_table" "ag_diagnosis_table" {
  name           = "DiagnosisKeys"
  read_capacity  = 5
  write_capacity = 5
  hash_key       = "reportId"
  range_key      = "randomId"

  attribute {
    name = "reportId"
    type = "S"
  }

  attribute {
    name = "randomId"
    type = "S"
  }

  tags = {
    Name        = var.appName
    Environment = var.env
  }
}