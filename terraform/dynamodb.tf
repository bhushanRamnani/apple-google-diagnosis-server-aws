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

  attribute {
      name = "keyData"
      type = "B"
    }

  global_secondary_index {
    name               = "keyData-index"
    hash_key           = "keyData"
    write_capacity     = 5
    read_capacity      = 5
    projection_type    = "ALL"
  }

  tags = {
    Name        = var.appName
    Environment = var.env
  }
}