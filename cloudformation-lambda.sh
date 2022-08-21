VERSION=$1
echo "Updating Lambda with version $VERSION"

aws cloudformation deploy \
  --template-file s3-object-lambda/template/s3objectlambda_defaultconfig.yaml \
  --stack-name s3-authz-demo \
  --parameter-overrides ObjectLambdaAccessPointName=s3-authz-demo-olap \
                        SupportingAccessPointName=s3-authz-demo-ap \
                        S3BucketName=s3-authz-demo \
                        LambdaFunctionS3BucketName=s3-authz-demo-lambda-deploy \
                        LambdaFunctionS3Key=S3ObjectLambdaDefaultConfigJavaFunction-1.0.jar \
                        LambdaFunctionRuntime=java11 \
                        LambdaFunctionS3ObjectVersion=$VERSION \
                        CreateNewSupportingAccessPoint=true \
 --capabilities CAPABILITY_IAM \
 --region us-east-1