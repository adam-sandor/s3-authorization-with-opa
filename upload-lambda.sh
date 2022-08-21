mvn -f s3-object-lambda/function/java11/pom.xml package
echo "Package complete, uploading artifact..."
aws s3 cp s3-object-lambda/function/java11/target/S3ObjectLambdaDefaultConfigJavaFunction-1.0.jar s3://s3-authz-demo-lambda-deploy/S3ObjectLambdaDefaultConfigJavaFunction-1.0.jar
echo "Upload complete, updating Lambda"

VERSION=$(aws s3api list-object-versions --bucket 's3-authz-demo-lambda-deploy' | jq '.Versions[] | select((.Key == "S3ObjectLambdaDefaultConfigJavaFunction-1.0.jar") and (.IsLatest == true)) | {VersionId}' | jq -r .VersionId)
./cloudformation-lambda.sh $VERSION
