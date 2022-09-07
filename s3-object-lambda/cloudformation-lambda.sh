source ../.init

VERSION=$1
echo "Updating Lambda with version $VERSION"

aws cloudformation deploy \
  --template-file lambda-cf-template.yaml \
  --stack-name s3-authz-demo \
  --parameter-overrides ObjectLambdaAccessPointName=$OBJECT_AP \
                        SupportingAccessPointName=$SUPPORTING_AP \
                        S3BucketName=s3-authz-demo \
                        LambdaFunctionS3BucketName=$DEPLOYMENT_BUCKET \
                        LambdaFunctionS3Key=s3authorizer-1.0.jar \
                        LambdaFunctionRuntime=java11 \
                        LambdaFunctionS3ObjectVersion=$VERSION \
                        CreateNewSupportingAccessPoint=true \
 --capabilities CAPABILITY_IAM \
 --region us-east-1