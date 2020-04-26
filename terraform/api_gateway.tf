data "template_file" "ag_openapi_def_v1" {
  template = file(var.api_spec_path_v1)

  # #Pass the variable value if needed in swagger file
  vars = {
    lambda_invoke_arn = aws_lambda_function.ag_diagnosis_server_lambda.invoke_arn
  }
}

resource "aws_api_gateway_rest_api" "ag_api_gateway" {
  name        = "ag_diagnosis_api_gateway"
  description = "API Gateway for Apple/Google Diagnosis server"
  body        = data.template_file.ag_openapi_def_v1.rendered
}

resource "aws_api_gateway_deployment" "ag_api_lambda_gateway" {
  rest_api_id = aws_api_gateway_rest_api.ag_api_gateway.id
  stage_name  = "v1"
}

resource "aws_lambda_permission" "ag_lambda_apigateway" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.ag_diagnosis_server_lambda.function_name
  principal     = "apigateway.amazonaws.com"

  # The "/*/*" portion grants access from any method on any resource
  # within the API Gateway REST API.
  source_arn = "${aws_api_gateway_rest_api.ag_api_gateway.execution_arn}/*/*"
}

output "gateway_base_url" {
  value = "${aws_api_gateway_deployment.ag_api_lambda_gateway.invoke_url}/diagnosiskeys"
}
