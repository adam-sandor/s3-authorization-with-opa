set -e
source ../.init

mvn clean package
echo "Package complete, uploading artifact..."
aws s3 cp target/s3authorizer-1.0.jar s3://$DEPLOYMENT_BUCKET/s3authorizer-1.0.jar
echo "Upload complete, updating Lambda"

VERSION=$(aws s3api list-object-versions --bucket $DEPLOYMENT_BUCKET | jq '.Versions[] | select((.Key == "s3authorizer-1.0.jar") and (.IsLatest == true)) | {VersionId}' | jq -r .VersionId)
./cloudformation-lambda.sh $VERSION
